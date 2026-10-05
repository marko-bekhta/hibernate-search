package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonBodies;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** UTF-8 decoding, parser construction, complete feed/end-of-input and tree construction. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonUtf8IngressBenchmark {
	@State(Scope.Thread)
	public static class Bodies extends JsonComparisonBenchmark.Bodies {
		byte[] input;
		@Param({ "8192" })
		public int feedSize;

		// The inherited @Setup invokes this override; annotating it too schedules it twice.
		@Override
		public void setup() {
			super.setup();
			if ( feedSize <= 0 ) {
				throw new IllegalArgumentException( "Feed size must be positive" );
			}
			input = json.getBytes( StandardCharsets.UTF_8 );
			try {
				JsonElement search = parseSearch( this );
				var gsonResult = parseGson( this );
				byte[] searchBytes = JsonBenchmarkSupport.bytes( JsonBodies.of( search )::writeTo );
				byte[] gsonBytes = gson.toJson( gsonResult ).getBytes( StandardCharsets.UTF_8 );
				if ( !Arrays.equals( input, searchBytes ) || !Arrays.equals( input, gsonBytes ) ) {
					throw new IllegalStateException( "UTF-8 ingress content mismatch" );
				}
			}
			catch (Exception e) {
				throw new IllegalStateException( e );
			}
		}
	}

	@Benchmark
	public JsonElement searchParser(Bodies state) {
		return parseSearch( state );
	}

	@Benchmark
	public com.google.gson.JsonObject gsonParser(Bodies state) throws IOException {
		return parseGson( state );
	}

	private static JsonElement parseSearch(Bodies state) {
		try ( var session = state.parsing.createParser() ) {
			ByteBuffer input = ByteBuffer.wrap( state.input );
			while ( input.position() < state.input.length ) {
				input.limit( Math.min( input.position() + state.feedSize, state.input.length ) );
				session.feed( input );
			}
			return session.endOfInput();
		}
	}

	private static com.google.gson.JsonObject parseGson(Bodies state) throws IOException {
		try ( var reader =
				new InputStreamReader( new FragmentedInputStream( state.input, state.feedSize ), StandardCharsets.UTF_8 ) ) {
			var result = state.gson.fromJson( reader, com.google.gson.JsonObject.class );
			if ( reader.read() != -1 ) {
				throw new IllegalStateException( "Incomplete UTF-8 consumption" );
			}
			return result;
		}
	}

	private static final class FragmentedInputStream extends ByteArrayInputStream {
		private final int feedSize;

		private FragmentedInputStream(byte[] input, int feedSize) {
			super( input );
			this.feedSize = feedSize;
		}

		@Override
		public synchronized int read(byte[] bytes, int offset, int length) {
			return super.read( bytes, offset, Math.min( length, feedSize ) );
		}
	}
}
