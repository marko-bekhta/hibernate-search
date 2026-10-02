package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.backend.elasticsearch.client.common.gson.entity.spi.GsonHttpEntityContentProvider;
import org.hibernate.search.backend.elasticsearch.client.common.json.impl.ElasticsearchJsonBody;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Complete NDJSON writes. Fresh includes construction/probe; reused excludes them for every writer. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonComparisonBenchmark {
	@State(Scope.Thread)
	public static class Bodies extends JsonBenchmarkFixtures {
		@Param({ "small", "mapping", "unicode", "nested", "scalar-1m", "scalar-8m", "bulk-10000" })
		public String fixture;
		@Param({ "fresh", "reused" })
		public String lifecycle;
		List<JsonElement> searchParts;
		JsonBody body;
		GsonHttpEntityContentProvider provider;
		JsonBoundedWorker worker;
		final ByteBuffer buffer = ByteBuffer.allocate( 8192 );
		final JsonParsing parsing = new JsonParsing();

		@Setup
		@Override
		public void setup() {
			super.fixture = fixture;
			super.setup();
			JsonBenchmarkSupport.verifyAffinity();
			try {
				searchParts = JsonBenchmarkSupport.convert( this );
				body = ElasticsearchJsonBody.ndjson( searchParts );
				provider = new GsonHttpEntityContentProvider( gson, parts );
				worker = new JsonBoundedWorker();
				JsonBenchmarkSupport.verify( this, searchParts, worker );
			}
			catch (Exception e) {
				throw new IllegalStateException( e );
			}
		}

		JsonBody selectedBody() {
			return "fresh".equals( lifecycle ) ? ElasticsearchJsonBody.ndjson( searchParts ) : body;
		}

		@TearDown
		public void close() throws Exception {
			worker.close();
		}
	}

	@Benchmark
	public long gson(Bodies state, Blackhole blackhole) throws Exception {
		var sink = new JsonBenchmarkSupport.OpaqueSink( blackhole );
		GsonHttpEntityContentProvider provider = "fresh".equals( state.lifecycle )
				? new GsonHttpEntityContentProvider( state.gson, state.parts )
				: state.provider;
		provider.writeTo( sink );
		return sink.result() ^ provider.getContentLength();
	}

	@Benchmark
	public long jackson(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = new JsonBenchmarkSupport.OpaqueSink( blackhole );
		JsonBenchmarkSupport.jackson( state.searchParts, sink );
		return sink.result() ^ body.contentLength();
	}

	@Benchmark
	public long synchronousCursor(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = new JsonBenchmarkSupport.OpaqueSink( blackhole );
		body.writeTo( sink );
		return sink.result() ^ body.contentLength();
	}

	@Benchmark
	public long incrementalCursor(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = new JsonBenchmarkSupport.OpaqueSink( blackhole );
		JsonBenchmarkSupport.incremental( body, sink, state.buffer );
		return sink.result() ^ body.contentLength();
	}

	@Benchmark
	public long boundedWorker(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = new JsonBenchmarkSupport.OpaqueSink( blackhole );
		state.worker.write( output -> JsonBenchmarkSupport.jackson( state.searchParts, output ), sink );
		return sink.result() ^ body.contentLength();
	}

	@Benchmark
	public JsonElement searchParser(Bodies state) {
		return state.parsing.parse( state.json );
	}

	@Benchmark
	public Object gsonParser(Bodies state) {
		return state.gson.fromJson( state.json, com.google.gson.JsonObject.class );
	}

	@State(Scope.Thread)
	public static class Accessors {
		final org.hibernate.search.backend.elasticsearch.gson.impl.JsonAccessor<Long> gsonAccessor =
				org.hibernate.search.backend.elasticsearch.gson.impl.JsonAccessor.root()
						.property( "hits" ).property( "total" ).property( "value" ).asLong();
		final org.hibernate.search.util.json.spi.JsonAccessor<Long> strict =
				org.hibernate.search.util.json.spi.JsonAccessor.root().property( "hits" ).property( "total" )
						.property( "value" ).asLong();
		final org.hibernate.search.util.json.spi.JsonAccessor<Long> adapted =
				org.hibernate.search.util.json.spi.JsonAccessor.root().property( "hits" ).unwrapSingletonArrays()
						.property( "total" ).unwrapSingletonArrays().property( "value" ).unwrapSingletonArrays().asLong();
		final String plain = "{\"hits\":{\"total\":{\"value\":42}}}";
		final String arrays = "{\"hits\":[[{\"total\":[{\"value\":[[42]]}]}]]}";
		final com.google.gson.JsonObject gsonPlain =
				new com.google.gson.Gson().fromJson( plain, com.google.gson.JsonObject.class );
		final com.google.gson.JsonObject gsonArrays =
				new com.google.gson.Gson().fromJson( arrays, com.google.gson.JsonObject.class );
		final org.hibernate.search.util.json.JsonObject searchPlain = new JsonParsing().parse( plain ).getAsJsonObject();
		final org.hibernate.search.util.json.JsonObject searchArrays = new JsonParsing().parse( arrays ).getAsJsonObject();

		@Setup
		public void verify() {
			JsonBenchmarkSupport.verifyAffinity();
			if ( gsonAccessor.get( gsonPlain ).orElseThrow() != 42
					|| gsonAccessor.get( gsonArrays ).orElseThrow() != 42
					|| strict.get( searchPlain ).orElseThrow() != 42 || adapted.get( searchArrays ).orElseThrow() != 42 ) {
				throw new IllegalStateException( "Accessor mismatch" );
			}
		}
	}

	@Benchmark
	public long gsonAccessor(Accessors state) {
		return state.gsonAccessor.get( state.gsonPlain ).orElseThrow();
	}

	@Benchmark
	public long gsonAdaptedAccessor(Accessors state) {
		return state.gsonAccessor.get( state.gsonArrays ).orElseThrow();
	}

	@Benchmark
	public long searchAccessor(Accessors state) {
		return state.strict.get( state.searchPlain ).orElseThrow();
	}

	@Benchmark
	public long searchAdaptedAccessor(Accessors state) {
		return state.adapted.get( state.searchArrays ).orElseThrow();
	}

	@Benchmark
	public JsonParsing initializeParser(JsonBenchmarkAffinity affinity) {
		return new JsonParsing();
	}
}
