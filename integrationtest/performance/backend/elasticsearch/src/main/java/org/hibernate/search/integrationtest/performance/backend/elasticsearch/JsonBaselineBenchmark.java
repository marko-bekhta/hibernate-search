package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.backend.elasticsearch.client.common.gson.entity.spi.ContentEncoder;
import org.hibernate.search.backend.elasticsearch.client.common.gson.entity.spi.GsonHttpEntityContentProvider;
import org.hibernate.search.backend.elasticsearch.client.common.gson.spi.GsonProvider;
import org.hibernate.search.backend.elasticsearch.gson.impl.JsonAccessor;
import org.hibernate.search.backend.elasticsearch.gson.spi.GsonProviderHelper;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.RootTypeMapping;

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
 * Server-independent baseline of the current Gson tree, codecs and HTTP body writer.
 * Fixture construction is excluded from measured operations; encoded-body construction is included.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonBaselineBenchmark {

	@State(Scope.Thread)
	public static class Bodies {
		@Param({ "small", "mapping", "unicode", "nested", "scalar-1m", "scalar-8m", "bulk-10000" })
		public String fixture;

		public Gson gson;
		public List<JsonObject> parts;
		public String json;
		public long encodedBytes;

		@Setup
		public void setup() {
			gson = GsonProviderHelper.create( GsonBuilder::new, false ).getGson();
			parts = new ArrayList<>();
			JsonObject body = new JsonObject();
			switch ( fixture ) {
				case "small":
					body = gson.fromJson( "{\"query\":{\"term\":{\"name\":\"test\"}},\"size\":10}", JsonObject.class );
					break;
				case "mapping":
					body = gson.fromJson( "{\"dynamic\":\"strict\",\"_routing\":{\"required\":true},"
							+ "\"properties\":{\"name\":{\"type\":\"keyword\"},\"time\":{\"type\":\"date\","
							+ "\"format\":\"strict_date_optional_time||epoch_millis\"}}}", JsonObject.class );
					break;
				case "unicode":
					body.addProperty( "text", "é漢😀\n\"\\".repeat( 256 ) );
					break;
				case "nested":
					JsonObject current = body;
					for ( int i = 0; i < 32; i++ ) {
						JsonObject child = new JsonObject();
						current.add( "child", child );
						current = child;
					}
					current.addProperty( "value", 9007199254740993L );
					break;
				case "scalar-1m":
					body.addProperty( "text", "x".repeat( 1024 * 1024 ) );
					break;
				case "scalar-8m":
					body.addProperty( "text", "x".repeat( 8 * 1024 * 1024 ) );
					break;
				case "bulk-10000":
					for ( int i = 0; i < 10_000; i++ ) {
						JsonObject metadata = new JsonObject();
						JsonObject index = new JsonObject();
						index.addProperty( "_index", "benchmark" );
						index.addProperty( "_id", i );
						metadata.add( "index", index );
						parts.add( metadata );
						JsonObject document = new JsonObject();
						document.addProperty( "text", "x".repeat( 256 ) );
						document.addProperty( "number", i );
						parts.add( document );
					}
					break;
				default:
					throw new IllegalArgumentException( fixture );
			}
			if ( parts.isEmpty() ) {
				parts.add( body );
			}
			json = gson.toJson( parts.get( 0 ) );
			encodedBytes = 0;
			for ( JsonObject part : parts ) {
				encodedBytes += gson.toJson( part ).getBytes( StandardCharsets.UTF_8 ).length + 1;
			}
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
	public GsonProvider initializeProvider() {
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
