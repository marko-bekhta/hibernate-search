package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.util.concurrent.TimeUnit;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Already materialized String to complete tree through character and UTF-8 parser paths. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 8, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonStringUtf8Benchmark {
	@State(Scope.Thread)
	public static class Input {
		@Param({ "scalar-1m", "unicode-1m", "small", "fields-1m" })
		public String fixture;
		final JsonParsing parsing = new JsonParsing();
		String json;

		@Setup
		public void setup() {
			JsonBenchmarkSupport.verifyAffinity();
			ObjectNode expected = JsonNodeFactory.instance.objectNode();
			switch ( fixture ) {
				case "scalar-1m":
					String ascii = "x".repeat( 1024 * 1024 );
					json = "{\"text\":\"" + ascii + "\"}";
					expected.put( "text", ascii );
					break;
				case "unicode-1m":
					String unit = "é漢😀\n\"\\";
					json = "{\"text\":\"" + "é漢😀\\n\\\"\\\\".repeat( 100_000 ) + "\"}";
					expected.put( "text", unit.repeat( 100_000 ) );
					break;
				case "small":
					json = "{\"query\":{\"term\":{\"name\":\"test\"}},\"size\":10}";
					ObjectNode query = JsonNodeFactory.instance.objectNode();
					ObjectNode term = JsonNodeFactory.instance.objectNode();
					term.put( "name", "test" );
					query.set( "term", term );
					expected.set( "query", query );
					expected.put( "size", 10 );
					break;
				case "fields-1m":
					StringBuilder document = new StringBuilder( "{" );
					String value = "short-value-" + "x".repeat( 36 );
					for ( int i = 0; i < 18_000; i++ ) {
						if ( i != 0 ) {
							document.append( ',' );
						}
						String name = "field" + i;
						document.append( '"' ).append( name ).append( "\":\"" ).append( value ).append( '"' );
						expected.put( name, value );
					}
					json = document.append( '}' ).toString();
					break;
				default:
					throw new IllegalArgumentException( fixture );
			}
			verify( expected, parsing.parse( json ) );
			verify( expected, JsonStringUtf8Encoding.parse( parsing, json, JsonStringUtf8Encoding.BYTE_CAPACITY ) );
			verify( expected, JsonStringUtf8Encoding.parseStaged( parsing, json,
					JsonStringUtf8Encoding.BYTE_CAPACITY, JsonStringUtf8Encoding.CHAR_CAPACITY ) );
			// Lexical/escape invariants independently checked even on fixtures with no numeric fields.
			JsonStringUtf8Verification.verifyLexemes( parsing );
		}

		private static void verify(JsonNode expected, JsonNode actual) {
			if ( !expected.equals( actual ) || !expected.toString().equals( actual.toString() ) ) {
				throw new IllegalStateException( "Complete String fixture mismatch" );
			}
		}

	}

	@Benchmark
	public void searchCharacterString(Input state, Blackhole blackhole) {
		blackhole.consume( state.parsing.parse( state.json ) );
	}

	@Benchmark
	public void searchEncodedString(Input state, Blackhole blackhole) {
		blackhole.consume( JsonStringUtf8Encoding.parse( state.parsing, state.json, JsonStringUtf8Encoding.BYTE_CAPACITY ) );
	}

	@Benchmark
	public void searchStagedEncodedString(Input state, Blackhole blackhole) {
		blackhole.consume( JsonStringUtf8Encoding.parseStaged( state.parsing, state.json,
				JsonStringUtf8Encoding.BYTE_CAPACITY, JsonStringUtf8Encoding.CHAR_CAPACITY ) );
	}

	@Benchmark
	public long diagnosticStagedEncodingOnly(Input state, Blackhole blackhole) {
		return JsonStringUtf8Encoding.encodeStaged( state.json, null,
				JsonStringUtf8Encoding.BYTE_CAPACITY, JsonStringUtf8Encoding.CHAR_CAPACITY, blackhole );
	}

	@Benchmark
	public long diagnosticEncodingOnly(Input state, Blackhole blackhole) {
		return JsonStringUtf8Encoding.encode( state.json, null, JsonStringUtf8Encoding.BYTE_CAPACITY, blackhole );
	}
}
