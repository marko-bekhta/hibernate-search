package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.hibernate.search.util.json.spi.JsonParseException;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.openjdk.jmh.infra.Blackhole;

import tools.jackson.databind.JsonNode;

/** Experiment only: standard UTF-8 encoding with fixed scratch, never an encoded whole body. */
final class JsonStringUtf8Encoding {
	static final int BYTE_CAPACITY = 8192;
	static final int CHAR_CAPACITY = 4096;

	private JsonStringUtf8Encoding() {
	}

	static JsonNode parse(JsonParsing parsing, String json, int byteCapacity) {
		try ( JsonParserSession parser = parsing.createParser() ) {
			encode( json, parser, byteCapacity );
			return parser.endOfInput();
		}
	}

	static JsonNode parseStaged(JsonParsing parsing, String json, int byteCapacity, int charCapacity) {
		try ( JsonParserSession parser = parsing.createParser() ) {
			encodeStaged( json, parser, byteCapacity, charCapacity, null );
			return parser.endOfInput();
		}
	}

	static long encodeStaged(String json, JsonParserSession parser, int byteCapacity, int charCapacity,
			Blackhole blackhole) {
		if ( byteCapacity < 4 || charCapacity < 2 ) {
			throw new IllegalArgumentException( "Scratch must hold one supplementary code point" );
		}
		CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
				.onMalformedInput( CodingErrorAction.REPORT ).onUnmappableCharacter( CodingErrorAction.REPORT );
		CharBuffer chars = CharBuffer.allocate( charCapacity );
		ByteBuffer bytes = ByteBuffer.allocate( byteCapacity );
		int offset = 0;
		long count = 0;
		while ( true ) {
			int length = Math.min( chars.remaining(), json.length() - offset );
			json.getChars( offset, offset + length, chars.array(), chars.position() );
			chars.position( chars.position() + length );
			offset += length;
			boolean end = offset == json.length();
			chars.flip();
			while ( true ) {
				CoderResult result = encoder.encode( chars, bytes, end );
				check( result );
				count += drain( bytes, parser, blackhole );
				if ( result.isUnderflow() ) {
					break;
				}
			}
			if ( end ) {
				break;
			}
			// Preserve an unconsumed high surrogate; refill its low surrogate in the next window.
			chars.compact();
		}
		while ( true ) {
			CoderResult result = encoder.flush( bytes );
			check( result );
			count += drain( bytes, parser, blackhole );
			if ( result.isUnderflow() ) {
				return count;
			}
		}
	}

	/** A null parser measures encoding alone, a diagnostic rather than a full-tree competitor. */
	static long encode(String json, JsonParserSession parser, int byteCapacity) {
		return encode( json, parser, byteCapacity, null );
	}

	static long encode(String json, JsonParserSession parser, int byteCapacity, Blackhole blackhole) {
		if ( byteCapacity < 4 ) {
			throw new IllegalArgumentException( "UTF-8 output must hold one supplementary code point" );
		}
		CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
				.onMalformedInput( CodingErrorAction.REPORT ).onUnmappableCharacter( CodingErrorAction.REPORT );
		CharBuffer chars = CharBuffer.wrap( json );
		ByteBuffer bytes = ByteBuffer.allocate( byteCapacity );
		long count = 0;
		while ( true ) {
			CoderResult result = encoder.encode( chars, bytes, true );
			check( result );
			count += drain( bytes, parser, blackhole );
			if ( result.isUnderflow() ) {
				break;
			}
		}
		while ( true ) {
			CoderResult result = encoder.flush( bytes );
			check( result );
			count += drain( bytes, parser, blackhole );
			if ( result.isUnderflow() ) {
				return count;
			}
		}
	}

	private static long drain(ByteBuffer bytes, JsonParserSession parser, Blackhole blackhole) {
		bytes.flip();
		int count = bytes.remaining();
		if ( parser != null ) {
			while ( bytes.hasRemaining() ) {
				parser.feed( bytes );
			}
		}
		if ( blackhole != null ) {
			blackhole.consume( bytes.array() );
		}
		bytes.clear();
		return count;
	}

	private static void check(CoderResult result) {
		if ( result.isError() ) {
			try {
				result.throwException();
			}
			catch (CharacterCodingException e) {
				throw new JsonParseException( "Invalid Unicode input", e );
			}
		}
	}
}
