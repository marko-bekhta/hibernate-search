package org.hibernate.search.util.json.impl;

import java.nio.ByteBuffer;
import java.util.Objects;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonParseException;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsingOptions;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.async.ByteBufferFeeder;

final class JacksonJsonParserSession implements JsonParserSession {
	private static final ByteBuffer EMPTY = ByteBuffer.allocate( 0 ).asReadOnlyBuffer();
	private JsonParser parser;
	private ByteBufferFeeder feeder;
	private final JsonTreeBuilder builder = new JsonTreeBuilder();
	private final Utf8Validator utf8 = new Utf8Validator();
	private final JsonParsingOptions options;
	private long bytes;

	JacksonJsonParserSession(JsonParser parser, JsonParsingOptions options) {
		this.parser = parser;
		feeder = (ByteBufferFeeder) parser.nonBlockingInputFeeder();
		this.options = options;
	}

	@Override
	public void feed(ByteBuffer input) {
		requireOpen();
		Objects.requireNonNull( input );
		if ( !input.hasRemaining() ) {
			return;
		}
		int size = Math.min( input.remaining(), options.maxInputChunkSize() );
		if ( options.maxInputBytes() != 0 && size > options.maxInputBytes() - bytes ) {
			fail( new JsonParseException( "JSON input exceeds byte limit " + options.maxInputBytes() ) );
		}
		ByteBuffer slice = input.slice();
		slice.limit( size );
		try {
			if ( !feeder.needMoreInput() ) {
				throw new IllegalStateException( "JSON parser still owns previous input" );
			}
			utf8.accept( slice );
			feeder.feedInput( slice );
			bytes += size;
			drain( false );
			// Jackson uses absolute reads without advancing the buffer position.
			input.position( input.position() + size );
			// Drop Jackson's reference to caller storage before returning ownership.
			feeder.feedInput( EMPTY );
		}
		catch (RuntimeException e) {
			fail( e );
		}
	}

	@Override
	public JsonElement endOfInput() {
		requireOpen();
		try {
			utf8.endOfInput();
			feeder.endOfInput();
			drain( true );
			JsonElement result = builder.finish();
			close();
			return result;
		}
		catch (RuntimeException e) {
			fail( e );
			return null;
		}
	}

	private void drain(boolean ended) {
		while ( true ) {
			JsonToken token = parser.nextToken();
			if ( token == JsonToken.NOT_AVAILABLE ) {
				if ( ended ) {
					throw new JsonParseException( "Truncated JSON input" );
				}
				return;
			}
			if ( token == null ) {
				return;
			}
			switch ( token ) {
				case START_OBJECT:
					builder.startObject();
					break;
				case START_ARRAY:
					builder.startArray();
					break;
				case END_OBJECT:
				case END_ARRAY:
					builder.endContainer();
					break;
				case PROPERTY_NAME:
					builder.name( parser.currentName() );
					break;
				case VALUE_STRING:
					builder.add( new JsonPrimitive( parser.getString() ) );
					break;
				case VALUE_NUMBER_INT:
				case VALUE_NUMBER_FLOAT:
					String number = parser.getString();
					// Jackson normalizes integer -0 to 0. Async token offsets start after the first byte;
					// the remaining span is one for -0, and zero for 0 (also at EOF).
					if ( "0".equals( number )
							&& parser.currentLocation().getByteOffset()
									- parser.currentTokenLocation().getByteOffset() == 1 ) {
						number = "-0";
					}
					builder.add( new JsonPrimitive( JsonNumber.fromParser( number ) ) );
					break;
				case VALUE_TRUE:
					builder.add( new JsonPrimitive( true ) );
					break;
				case VALUE_FALSE:
					builder.add( new JsonPrimitive( false ) );
					break;
				case VALUE_NULL:
					builder.add( JsonNull.INSTANCE );
					break;
				default:
					throw new JsonParseException( "Unexpected JSON token " + token );
			}
		}
	}

	private void fail(Exception failure) {
		close();
		if ( failure instanceof JsonParseException ) {
			throw (JsonParseException) failure;
		}
		throw new JsonParseException( "Invalid JSON input: " + failure.getMessage(), failure );
	}

	private void requireOpen() {
		if ( parser == null ) {
			throw new IllegalStateException( "JSON parser session is closed" );
		}
	}

	@Override
	public long inputBytes() {
		return bytes;
	}

	@Override
	public void close() {
		JsonParser current = parser;
		parser = null;
		feeder = null;
		builder.clear();
		if ( current != null ) {
			try {
				current.close();
			}
			catch (RuntimeException e) {
				throw new JsonParseException( "Cannot close JSON parser", e );
			}
		}
	}
}
