package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.util.json.spi.JsonParsingOptions;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.async.ByteBufferFeeder;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;

/** Upstream String token completion versus complete tree construction; never skip lazy string completion. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonScalarConsumptionBenchmark {
	@State(Scope.Thread)
	public static class Scalars extends JsonComparisonBenchmark.Bodies {
		final JsonFactory jackson = factory();
		byte[] utf8;

		private static JsonFactory factory() {
			var options = JsonParsingOptions.DEFAULT;
			return JsonFactory.builder().streamReadConstraints( StreamReadConstraints.builder()
					.maxNestingDepth( options.maxDepth() ).maxStringLength( options.maxStringLength() )
					.maxNameLength( options.maxNameLength() ).maxNumberLength( options.maxNumberLength() )
					.maxDocumentLength( -1 ).maxTokenCount( -1 ).build() ).build();
		}

		// The inherited @Setup invokes this override; annotating it too schedules it twice.
		@Override
		public void setup() {
			super.setup();
			utf8 = json.getBytes( StandardCharsets.UTF_8 );
			List<String> expected = new ArrayList<>();
			var tree = parts.get( 0 );
			if ( tree.has( "text" ) ) {
				expected.add( tree.get( "text" ).asString() );
			}
			else {
				for ( var number : tree.get( "values" ).asArray() ) {
					expected.add( number.numberValue().toString() );
				}
			}
			List<String> actual = new ArrayList<>();
			try ( var parser = jackson.createParser( ObjectReadContext.empty(), json ) ) {
				JsonToken token;
				while ( ( token = parser.nextToken() ) != null ) {
					if ( token == JsonToken.VALUE_STRING
							|| token == JsonToken.VALUE_NUMBER_INT
							|| token == JsonToken.VALUE_NUMBER_FLOAT ) {
						actual.add( parser.getString() );
					}
				}
			}
			if ( !equivalentScalars( expected, actual )
					|| !equivalentScalars( expected, utf8Scalars() )
					|| !parsing.parse( json ).equals( searchParts.get( 0 ) ) ) {
				throw new IllegalStateException( "Scalar completion mismatch" );
			}
		}

		private boolean equivalentScalars(List<String> expected, List<String> actual) {
			if ( exactOutputBytes() ) {
				return expected.equals( actual );
			}
			if ( expected.size() != actual.size() ) {
				return false;
			}
			for ( int i = 0; i < expected.size(); i++ ) {
				if ( new java.math.BigDecimal( expected.get( i ) )
						.compareTo( new java.math.BigDecimal( actual.get( i ) ) ) != 0 ) {
					return false;
				}
			}
			return true;
		}

		private List<String> utf8Scalars() {
			List<String> actual = new ArrayList<>();
			try ( var parser = jackson.createNonBlockingByteBufferParser( ObjectReadContext.empty() ) ) {
				var feeder = (ByteBufferFeeder) parser.nonBlockingInputFeeder();
				for ( int offset = 0; offset < utf8.length; ) {
					int count = Math.min( 8192, utf8.length - offset );
					feeder.feedInput( ByteBuffer.wrap( utf8, offset, count ).slice() );
					collectScalars( parser, actual, false );
					offset += count;
				}
				feeder.endOfInput();
				collectScalars( parser, actual, true );
			}
			return actual;
		}

		private static void collectScalars(JsonParser parser, List<String> actual, boolean ended) {
			while ( true ) {
				JsonToken token = parser.nextToken();
				if ( token == null ) {
					return;
				}
				if ( token == JsonToken.NOT_AVAILABLE ) {
					if ( ended ) {
						throw new IllegalStateException( "Incomplete UTF-8 scalar validation" );
					}
					return;
				}
				if ( token == JsonToken.VALUE_STRING
						|| token == JsonToken.VALUE_NUMBER_INT
						|| token == JsonToken.VALUE_NUMBER_FLOAT ) {
					actual.add( parser.getString() );
				}
			}
		}

	}

	@Benchmark
	public long jacksonTokens(Scalars state, Blackhole blackhole) {
		long size = 0;
		try ( var parser = state.jackson.createParser( ObjectReadContext.empty(), state.json ) ) {
			JsonToken token;
			while ( ( token = parser.nextToken() ) != null ) {
				size++;
				if ( token == JsonToken.PROPERTY_NAME || token.isScalarValue() ) {
					String value = parser.getString();
					blackhole.consume( value );
					size += value.length();
				}
			}
		}
		return size;
	}

	@Benchmark
	public long jacksonUtf8Tokens(Scalars state, Blackhole blackhole) {
		long size = 0;
		try ( var parser = state.jackson.createNonBlockingByteBufferParser( ObjectReadContext.empty() ) ) {
			var feeder = (ByteBufferFeeder) parser.nonBlockingInputFeeder();
			for ( int offset = 0; offset < state.utf8.length; ) {
				int count = Math.min( 8192, state.utf8.length - offset );
				feeder.feedInput( ByteBuffer.wrap( state.utf8, offset, count ).slice() );
				size += drainUtf8( parser, blackhole, false );
				offset += count;
			}
			feeder.endOfInput();
			return size + drainUtf8( parser, blackhole, true );
		}
	}

	private static long drainUtf8(JsonParser parser, Blackhole blackhole, boolean ended) {
		long size = 0;
		while ( true ) {
			JsonToken token = parser.nextToken();
			if ( token == null ) {
				return size;
			}
			if ( token == JsonToken.NOT_AVAILABLE ) {
				if ( ended ) {
					throw new IllegalStateException( "Incomplete UTF-8 token consumption" );
				}
				return size;
			}
			size++;
			if ( token == JsonToken.PROPERTY_NAME || token.isScalarValue() ) {
				String value = parser.getString();
				blackhole.consume( value );
				size += value.length();
			}
		}
	}

	@Benchmark
	public JsonNode searchParser(Scalars state) {
		return state.parsing.parse( state.json );
	}

}
