package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.util.json.impl.JsonNumbers;
import org.hibernate.search.util.json.spi.JsonParsingOptions;

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
import org.openjdk.jmh.infra.Blackhole;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.async.ByteBufferFeeder;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Valid numeric fixtures only: upstream feeding/completion diagnostics, not strict Search SPI competitors. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 8, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonNumericUtf8DiagnosticBenchmark {
	@State(Scope.Thread)
	public static class Input {
		@Param({ "integers", "mixed-supported" })
		public String fixture;
		@Param({ "tokens", "numbers", "tree" })
		public String work;
		byte[] utf8;
		String[] lexemes;
		JsonFactory jackson;

		@Setup
		public void setup() {
			JsonBenchmarkSupport.verifyAffinity();
			var options = JsonParsingOptions.DEFAULT;
			jackson = JsonFactory.builder().streamReadConstraints( StreamReadConstraints.builder()
					.maxNestingDepth( options.maxDepth() ).maxStringLength( options.maxStringLength() )
					.maxNameLength( options.maxNameLength() ).maxNumberLength( options.maxNumberLength() )
					.maxDocumentLength( -1 ).maxTokenCount( -1 ).build() ).build();
			lexemes = new String[10_000];
			// Core normalizes -0; the complete-tree mixed fixture covers it separately.
			String[] mixed = { "-42", "0.125", "1.00", "6.022e23", "-7E-9", "9223372036854775808", "1e2147483648" };
			for ( int i = 0; i < lexemes.length; i++ ) {
				lexemes[i] = switch ( fixture ) {
					case "integers" -> Integer.toString( i );
					case "mixed" -> mixed[i % mixed.length];
					case "mixed-supported" -> "1e2147483648".equals( mixed[i % mixed.length] )
							? "1e1000"
							: mixed[i % mixed.length];
					default -> throw new IllegalArgumentException( fixture );
				};
			}
			utf8 = ( "{\"values\":[" + String.join( ",", lexemes ) + "]}" ).getBytes( StandardCharsets.UTF_8 );
			if ( !List.of( "tokens", "numbers", "tree" ).contains( work ) ) {
				throw new IllegalArgumentException( work );
			}
			for ( String input : List.of( "buffer", "array", "sync" ) ) {
				List<String> actual = new ArrayList<>();
				consume( this, input, null, actual );
				if ( !List.of( lexemes ).equals( actual ) ) {
					throw new IllegalStateException( "Diagnostic numeric lexeme mismatch: " + input );
				}
			}
		}
	}

	@Benchmark
	public long asyncByteBuffer(Input state, Blackhole blackhole) {
		return consume( state, "buffer", blackhole, null );
	}

	@Benchmark
	public long asyncByteArray(Input state, Blackhole blackhole) {
		return consume( state, "array", blackhole, null );
	}

	@Benchmark
	public long synchronousUtf8(Input state, Blackhole blackhole) {
		return consume( state, "sync", blackhole, null );
	}

	@Benchmark
	public ObjectNode treeFromOwnedLexemes(Input state) {
		ObjectNode root = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
		ArrayNode values = tools.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
		root.set( "values", values );
		for ( String lexeme : state.lexemes ) {
			values.add( number( lexeme ) );
		}
		return root;
	}

	private static tools.jackson.databind.JsonNode number(String lexeme) {
		// Measure the production conversion/storage policy, without a second parser per scalar.
		return JsonNumbers.fromToken( lexeme,
				lexeme.indexOf( '.' ) >= 0 || lexeme.indexOf( 'e' ) >= 0 || lexeme.indexOf( 'E' ) >= 0 );
	}

	private static BigDecimal decimal(tools.jackson.databind.JsonNode number) {
		return new BigDecimal( number.numberValue().toString() );
	}

	private static long consume(Input state, String input, Blackhole blackhole, List<String> actual) {
		try ( JsonParser parser = switch ( input ) {
			case "buffer" -> state.jackson.createNonBlockingByteBufferParser( ObjectReadContext.empty() );
			case "array" -> state.jackson.createNonBlockingByteArrayParser( ObjectReadContext.empty() );
			case "sync" -> state.jackson.createParser( ObjectReadContext.empty(), state.utf8 );
			default -> throw new IllegalArgumentException( input );
		} ) {
			ObjectNode root = null;
			ArrayNode values = null;
			if ( "tree".equals( state.work ) ) {
				root = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
				values = tools.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
				root.set( "values", values );
			}
			long count = 0;
			if ( "sync".equals( input ) ) {
				count = drain( parser, state.work, values, blackhole, actual, true );
			}
			else {
				for ( int offset = 0; offset < state.utf8.length; ) {
					int end = Math.min( offset + 8192, state.utf8.length );
					if ( "buffer".equals( input ) ) {
						( (ByteBufferFeeder) parser.nonBlockingInputFeeder() )
								.feedInput( ByteBuffer.wrap( state.utf8, offset, end - offset ).slice() );
					}
					else {
						( (ByteArrayFeeder) parser.nonBlockingInputFeeder() ).feedInput( state.utf8, offset, end );
					}
					count += drain( parser, state.work, values, blackhole, actual, false );
					offset = end;
				}
				parser.nonBlockingInputFeeder().endOfInput();
				count += drain( parser, state.work, values, blackhole, actual, true );
			}
			if ( actual != null && values != null ) {
				if ( values.size() != state.lexemes.length ) {
					throw new IllegalStateException( "Incomplete diagnostic tree" );
				}
				for ( int i = 0; i < values.size(); i++ ) {
					if ( new BigDecimal( state.lexemes[i] ).compareTo( decimal( values.get( i ) ) ) != 0 ) {
						throw new IllegalStateException( "Diagnostic tree numeric value mismatch" );
					}
				}
			}
			if ( blackhole != null && root != null ) {
				blackhole.consume( root );
			}
			if ( count != state.lexemes.length ) {
				throw new IllegalStateException( "Incomplete numeric diagnostic" );
			}
			return count;
		}
	}

	private static long drain(JsonParser parser, String work, ArrayNode values, Blackhole blackhole,
			List<String> actual, boolean ended) {
		long count = 0;
		while ( true ) {
			JsonToken token = parser.nextToken();
			if ( token == null ) {
				return count;
			}
			if ( token == JsonToken.NOT_AVAILABLE ) {
				if ( ended ) {
					throw new IllegalStateException( "Incomplete numeric diagnostic at EOF" );
				}
				return count;
			}
			if ( token == JsonToken.PROPERTY_NAME ) {
				if ( blackhole != null ) {
					blackhole.consume( parser.currentName() );
				}
			}
			else if ( token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT ) {
				String lexeme = parser.getString();
				count++;
				if ( actual != null ) {
					actual.add( lexeme );
				}
				if ( values != null ) {
					values.add( number( lexeme ) );
				}
				else if ( "numbers".equals( work ) ) {
					tools.jackson.databind.JsonNode number = number( lexeme );
					if ( actual != null && new BigDecimal( lexeme ).compareTo( decimal( number ) ) != 0 ) {
						throw new IllegalStateException( "Diagnostic number value mismatch" );
					}
					if ( blackhole != null ) {
						blackhole.consume( number );
					}
				}
				else if ( blackhole != null ) {
					blackhole.consume( lexeme );
				}
			}
		}
	}
}
