package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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

import tools.jackson.databind.JsonNode;

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
		@Param({ "heap" })
		public String bufferKind;
		ByteBuffer prepared;

		// The inherited @Setup invokes this override; annotating it too schedules it twice.
		@Override
		public void setup() {
			super.setup();
			if ( feedSize <= 0 ) {
				throw new IllegalArgumentException( "Feed size must be positive" );
			}
			input = json.getBytes( StandardCharsets.UTF_8 );
			prepared = switch ( bufferKind ) {
				case "heap" -> null;
				case "readonly" -> ByteBuffer.wrap( input ).asReadOnlyBuffer();
				case "direct" -> ByteBuffer.allocateDirect( input.length ).put( input ).flip();
				default -> throw new IllegalArgumentException( bufferKind );
			};
			try {
				JsonNode search = parseSearch( this );
				byte[] searchBytes = JsonBenchmarkSupport.bytes( JsonBodies.of( search )::writeTo );
				if ( exactOutputBytes()
						? !Arrays.equals( input, searchBytes )
						: !parsing.parse( json ).equals( search ) ) {
					throw new IllegalStateException( "UTF-8 ingress content mismatch" );
				}
			}
			catch (Exception e) {
				throw new IllegalStateException( e );
			}
		}
	}

	@Benchmark
	public JsonNode searchParser(Bodies state) {
		return parseSearch( state );
	}

	private static JsonNode parseSearch(Bodies state) {
		try ( var session = state.parsing.createParser() ) {
			ByteBuffer input = state.prepared == null ? ByteBuffer.wrap( state.input ) : state.prepared.duplicate();
			while ( input.position() < state.input.length ) {
				input.limit( Math.min( input.position() + state.feedSize, state.input.length ) );
				session.feed( input );
			}
			return session.endOfInput();
		}
	}

}
