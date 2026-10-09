package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.client.common.gson.entity.spi.ContentEncoder;
import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.client.common.gson.entity.spi.GsonHttpEntityContentProvider;
import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.client.common.gson.spi.GsonProvider;
import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl.JsonAccessor;
import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.spi.GsonProviderHelper;
import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.lowlevel.index.mapping.impl.RootTypeMapping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Server-independent baseline of the historical Gson tree, codecs and HTTP body writer.
 * Fixture construction is excluded from measured operations; encoded-body construction is included.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonBaselineBenchmark {

	@State(Scope.Thread)
	public static class Bodies extends JsonBenchmarkFixtures {
		@Param({ "small", "mapping", "unicode", "nested", "scalar-1m", "scalar-8m", "bulk-10000" })
		public String fixture;

		@Setup
		@Override
		public void setup() {
			super.fixture = fixture;
			super.setup();
		}
	}

	@State(Scope.Thread)
	public static class Accessors {
		private final JsonAccessor<Long> accessor =
				JsonAccessor.root().property( "hits" ).property( "total" ).property( "value" ).asLong();
		private final Gson gson = new Gson();
		private final JsonObject strict = gson.fromJson( "{\"hits\":{\"total\":{\"value\":42}}}", JsonObject.class );
		private final JsonObject adapted = gson.fromJson( "{\"hits\":[[{\"total\":[{\"value\":[[42]]}]}]]}", JsonObject.class );
	}

	@Benchmark
	public long synchronousBody(Bodies bodies) throws IOException {
		GsonHttpEntityContentProvider provider = new GsonHttpEntityContentProvider( bodies.gson, bodies.parts );
		provider.writeTo( OutputStream.nullOutputStream() );
		return provider.getContentLength();
	}

	@Benchmark
	public boolean pagedBody(Bodies bodies) throws IOException {
		GsonHttpEntityContentProvider provider = new GsonHttpEntityContentProvider( bodies.gson, bodies.parts );
		DiscardingEncoder encoder = new DiscardingEncoder();
		while ( !encoder.isCompleted() ) {
			encoder.budget = 8192;
			provider.produceContent( encoder );
		}
		return encoder.isCompleted();
	}

	@Benchmark
	public JsonObject parseFirstPart(Bodies bodies) {
		return bodies.gson.fromJson( bodies.json, JsonObject.class );
	}

	@Benchmark
	public GsonProvider initializeProvider(JsonBenchmarkAffinity affinity) {
		return GsonProviderHelper.create( GsonBuilder::new, false );
	}

	@Benchmark
	public Object mappingCodec(Bodies bodies) {
		return bodies.gson.fromJson( bodies.json, RootTypeMapping.class );
	}

	@Benchmark
	public long accessor(Accessors accessors) {
		return accessors.accessor.get( accessors.strict ).orElseThrow();
	}

	@Benchmark
	public long adaptedAccessor(Accessors accessors) {
		return accessors.accessor.get( accessors.adapted ).orElseThrow();
	}

	private static class DiscardingEncoder implements ContentEncoder {
		private int budget;
		private boolean completed;

		@Override
		public int write(ByteBuffer src) {
			int size = Math.min( budget, src.remaining() );
			src.position( src.position() + size );
			budget -= size;
			return size;
		}

		@Override
		public void complete() {
			completed = true;
		}

		@Override
		public boolean isCompleted() {
			return completed;
		}
	}
}
