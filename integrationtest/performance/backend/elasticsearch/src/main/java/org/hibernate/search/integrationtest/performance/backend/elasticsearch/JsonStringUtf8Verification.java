package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonParseException;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.JsonParsingOptions;

/** Standalone correctness checks for the experiment; run from the benchmark JAR, outside timing. */
public final class JsonStringUtf8Verification {
	private JsonStringUtf8Verification() {
	}

	static void verifyLexemes(JsonParsing parsing) {
		String json = "{\"\\uD83D\\uDE00\":[-0,1.2300e+04,9007199254740993,1e999999999],"
				+ "\"escaped\":\"\\uD800\\n\\u0041\\\"\\\\\"}";
		String expected = "{\"\\ud83d\\ude00\":[-0,1.2300e+04,9007199254740993,1e999999999],"
				+ "\"escaped\":\"\\ud800\\nA\\\"\\\\\"}";
		JsonElement actual = JsonStringUtf8Encoding.parse( parsing, json, 4 );
		if ( !expected.equals( actual.toString() )
				|| !parsing.parse( json ).equals( actual )
				|| !actual.equals( JsonStringUtf8Encoding.parseStaged( parsing, json, 4, 2 ) ) ) {
			throw new IllegalStateException( "Numeric lexeme or escaped-surrogate mismatch: " + actual );
		}
	}

	public static void main(String[] args) {
		JsonParsing parsing = new JsonParsing();
		verifyLexemes( parsing );
		int cases = 1;
		for ( int capacity : new int[] { 4, 5, 7, 16, 8192 } ) {
			for ( int padding = 0; padding < 20; padding++ ) {
				String text = "x".repeat( padding ) + "é漢😀" + "x".repeat( 8190 ) + "😀é漢";
				String json = "{\"nameé😀\":\"" + text + "\\n\\uD83D\\uDE00\",\"n\":-0}";
				JsonElement actual = JsonStringUtf8Encoding.parse( parsing, json, capacity );
				if ( !parsing.parse( json ).equals( actual )
						|| !( text + "\n😀" ).equals( actual.getAsJsonObject().get( "nameé😀" ).getAsString() ) ) {
					throw new IllegalStateException( "Unicode boundary mismatch" );
				}
				try ( TrackingSession parser = new TrackingSession( parsing.createParser(), capacity ) ) {
					long encoded = JsonStringUtf8Encoding.encode( json, parser, capacity );
					if ( encoded != json.getBytes( StandardCharsets.UTF_8 ).length
							|| encoded != parser.inputBytes() || !actual.equals( parser.endOfInput() ) ) {
						throw new IllegalStateException( "Bounded feed/count/EOF mismatch" );
					}
				}
				for ( int chars : new int[] { 2, 3, 4096 } ) {
					try ( TrackingSession parser = new TrackingSession( parsing.createParser(), capacity ) ) {
						long encoded = JsonStringUtf8Encoding.encodeStaged( json, parser, capacity, chars, null );
						if ( encoded != json.getBytes( StandardCharsets.UTF_8 ).length
								|| encoded != parser.inputBytes() || !actual.equals( parser.endOfInput() ) ) {
							throw new IllegalStateException( "Staged boundary/count/EOF mismatch" );
						}
					}
					cases++;
				}
				cases++;
			}
			for ( String invalid : new String[] {
					"",
					"{",
					"[1,",
					"{}{}",
					"\"x",
					"\"\uD800\"",
					"\"\uDC00\"",
					"\"\uD800x\"",
					"\"x\uD800",
					"01",
					"[true,]" } ) {
				expectFailure( parsing, invalid, capacity );
				cases++;
			}
		}
		for ( String malformed : new String[] {
				"\"" + "x".repeat( 4094 ) + "\uD800x\"",
				"\"" + "x".repeat( 4094 ) + "\uDC00\"",
				"\"" + "x".repeat( 8191 ) + "\uD800" } ) {
			expectFailure( parsing, malformed, 8192 );
			cases++;
		}
		String json = "{\"é\":\"😀\"}";
		long count = json.getBytes( StandardCharsets.UTF_8 ).length;
		if ( !parsing.parse( json ).equals( JsonStringUtf8Encoding.parse(
				new JsonParsing( JsonParsingOptions.DEFAULT.withMaxInputBytes( count ) ), json, 4 ) ) ) {
			throw new IllegalStateException( "Exact byte limit mismatch" );
		}
		expectFailure( new JsonParsing( JsonParsingOptions.DEFAULT.withMaxInputBytes( count - 1 ) ), json, 4 );
		expectFailure( new JsonParsing( new JsonParsingOptions( 1, 10, 10, 10, 1, 0 ) ), "[[1]]", 4 );
		expectFailure( new JsonParsing( new JsonParsingOptions( 10, 2, 10, 10, 1, 0 ) ), "\"abc\"", 4 );
		expectFailure( new JsonParsing( new JsonParsingOptions( 10, 10, 2, 10, 1, 0 ) ), "{\"éé\":1}", 4 );
		expectFailure( new JsonParsing( new JsonParsingOptions( 10, 10, 10, 2, 1, 0 ) ), "123", 4 );
		System.out.println( "String UTF-8 verification passed: " + ( cases + 6 )
				+ " scenarios; fixed byte bounds 4..8192 and char bounds 2/3/4096" );
	}

	private static void expectFailure(JsonParsing parsing, String json, int capacity) {
		for ( int chars : new int[] { 0, 2, 3, 4096 } ) {
			expectFailure( parsing, json, capacity, chars );
		}
	}

	private static void expectFailure(JsonParsing parsing, String json, int capacity, int chars) {
		TrackingSession parser = new TrackingSession( parsing.createParser(), capacity );
		boolean failed = false;
		try ( parser ) {
			if ( chars == 0 ) {
				JsonStringUtf8Encoding.encode( json, parser, capacity );
			}
			else {
				JsonStringUtf8Encoding.encodeStaged( json, parser, capacity, chars, null );
			}
			parser.endOfInput();
		}
		catch (JsonParseException expected) {
			failed = true;
		}
		if ( !failed || !parser.closed ) {
			throw new IllegalStateException( "Missing rejection/cleanup for " + json );
		}
	}

	private static final class TrackingSession implements JsonParserSession {
		private final JsonParserSession delegate;
		private final int capacity;
		private boolean closed;

		private TrackingSession(JsonParserSession delegate, int capacity) {
			this.delegate = delegate;
			this.capacity = capacity;
		}

		@Override
		public void feed(ByteBuffer input) {
			if ( input.capacity() != capacity || input.remaining() > capacity ) {
				throw new IllegalStateException( "Unbounded encoded scratch" );
			}
			delegate.feed( input );
		}

		@Override
		public JsonElement endOfInput() {
			return delegate.endOfInput();
		}

		@Override
		public long inputBytes() {
			return delegate.inputBytes();
		}

		@Override
		public void close() {
			delegate.close();
			closed = true;
		}
	}
}
