package org.hibernate.search.util.json.impl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonParseException;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsingOptions;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;

public final class JacksonJsonParserFactory {
	private final ResumableJsonFactory factory;
	private final JsonParsingOptions options;

	public JacksonJsonParserFactory(JsonParsingOptions options) {
		this.options = Objects.requireNonNull( options );
		factory = new ResumableJsonFactory( JsonFactory.builder().streamReadConstraints( StreamReadConstraints.builder()
				.maxNestingDepth( options.maxDepth() ).maxStringLength( options.maxStringLength() )
				.maxNameLength( options.maxNameLength() ).maxNumberLength( options.maxNumberLength() )
				.maxDocumentLength( -1 ).maxTokenCount( -1 ).build() ).build() );
	}

	/** Character input is already owned/materialized; Core retains it only for this synchronous call. */
	public JsonElement parse(String json) {
		if ( options.maxInputBytes() == 0 ) {
			validateUtf16( json );
		}
		else if ( utf8Length( json ) > options.maxInputBytes() ) {
			throw new JsonParseException( "JSON input exceeds byte limit " + options.maxInputBytes() );
		}
		JsonTreeBuilder builder = new JsonTreeBuilder();
		try ( JsonParser parser = factory.createParser( ObjectReadContext.empty(), json ) ) {
			JsonToken token;
			while ( ( token = parser.nextToken() ) != null ) {
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
						String name = parser.currentName();
						// The async parser constrains names by decoded UTF-8 byte length, not UTF-16 units.
						if ( utf8Length( name ) > options.maxNameLength() ) {
							throw new JsonParseException( "JSON name exceeds length limit " + options.maxNameLength() );
						}
						builder.name( name );
						break;
					case VALUE_STRING:
						String value = parser.getString();
						factory.streamReadConstraints().validateStringLength( value.length() );
						builder.add( new JsonPrimitive( value ) );
						break;
					case VALUE_NUMBER_INT:
					case VALUE_NUMBER_FLOAT:
						String number = parser.getString();
						long offset = parser.currentTokenLocation().getCharOffset();
						if ( "0".equals( number ) && offset >= 0 && json.charAt( (int) offset ) == '-' ) {
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
			return builder.finish();
		}
		catch (JsonParseException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw new JsonParseException( "Invalid JSON input: " + e.getMessage(), e );
		}
		finally {
			builder.clear();
		}
	}

	private static void validateUtf16(String text) {
		int index = 0;
		while ( index < text.length() ) {
			char character = text.charAt( index++ );
			if ( Character.isSurrogate( character ) ) {
				if ( !Character.isHighSurrogate( character )
						|| index == text.length()
						|| !Character.isLowSurrogate( text.charAt( index++ ) ) ) {
					throw new JsonParseException( "Invalid Unicode input" );
				}
			}
		}
	}

	/** Validate raw UTF-16 and count its UTF-8 representation without allocating another input. */
	private static long utf8Length(String text) {
		long result = 0;
		int index = 0;
		while ( index < text.length() ) {
			char character = text.charAt( index++ );
			if ( character < 0x80 ) {
				result++;
			}
			else if ( character < 0x800 ) {
				result += 2;
			}
			else if ( Character.isHighSurrogate( character )
					&& index < text.length()
					&& Character.isLowSurrogate( text.charAt( index ) ) ) {
				index++;
				result += 4;
			}
			else if ( Character.isSurrogate( character ) ) {
				throw new JsonParseException( "Invalid Unicode input" );
			}
			else {
				result += 3;
			}
		}
		return result;
	}

	public JsonParserSession createParser() {
		try {
			return new JacksonJsonParserSession( factory.createResumableByteBufferParser( ObjectReadContext.empty() ),
					options );
		}
		catch (RuntimeException e) {
			throw new JsonParseException( "Cannot create JSON parser", e );
		}
	}

	public JsonParserSession createParser(Charset charset) {
		Objects.requireNonNull( charset );
		if ( StandardCharsets.UTF_8.equals( charset ) ) {
			return createParser();
		}
		return new TranscodingJsonParserSession( new JacksonJsonParserFactory( options.withMaxInputBytes( 0 ) ).createParser(),
				charset, options );
	}
}
