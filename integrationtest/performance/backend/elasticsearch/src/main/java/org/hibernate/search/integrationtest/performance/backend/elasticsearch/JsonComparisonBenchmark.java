package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.ElasticsearchJsonBody;
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

import tools.jackson.databind.JsonNode;

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
		List<JsonNode> searchParts;
		JsonBody body;
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
				searchParts = List.copyOf( parts );
				body = ElasticsearchJsonBody.ndjson( searchParts );
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
	public JsonNode searchParser(Bodies state) {
		return state.parsing.parse( state.json );
	}

	@State(Scope.Thread)
	public static class Accessors {
		final org.hibernate.search.backend.elasticsearch.json.impl.JsonAccessor<Long> strict =
				org.hibernate.search.backend.elasticsearch.json.impl.JsonAccessor.root().property( "hits" ).property( "total" )
						.property( "value" ).asLong();
		final org.hibernate.search.backend.elasticsearch.json.impl.JsonAccessor<Long> adapted =
				org.hibernate.search.backend.elasticsearch.json.impl.JsonAccessor.root().property( "hits" )
						.unwrapSingletonArrays()
						.property( "total" ).unwrapSingletonArrays().property( "value" ).unwrapSingletonArrays().asLong();
		final String plain = "{\"hits\":{\"total\":{\"value\":42}}}";
		final String arrays = "{\"hits\":[[{\"total\":[{\"value\":[[42]]}]}]]}";
		final tools.jackson.databind.node.ObjectNode searchPlain = new JsonParsing().parse( plain ).asObject();
		final tools.jackson.databind.node.ObjectNode searchArrays = new JsonParsing().parse( arrays ).asObject();

		@Setup
		public void verify() {
			JsonBenchmarkSupport.verifyAffinity();
			if ( strict.get( searchPlain ).orElseThrow() != 42 || adapted.get( searchArrays ).orElseThrow() != 42 ) {
				throw new IllegalStateException( "Accessor mismatch" );
			}
		}
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
