package org.hibernate.search.util.json.spi;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.impl.JacksonJsonParserFactory;

/** Shareable parser factory with immutable constraints and no Gson/Jackson signatures. */
public final class JsonParsing {
	private final JacksonJsonParserFactory implementation;

	public JsonParsing() {
		this( JsonParsingOptions.DEFAULT );
	}

	public JsonParsing(JsonParsingOptions options) {
		implementation = new JacksonJsonParserFactory( options );
	}

	public JsonParserSession createParser() {
		return implementation.createParser();
	}

	/** Parse an explicitly selected source charset, transcoding incrementally with bounded buffers. */
	public JsonParserSession createParser(Charset charset) {
		return implementation.createParser( charset );
	}

	/** Parse an already materialized string, encoding UTF-8 in bounded chunks. */
	public JsonElement parse(String json) {
		try ( JsonParserSession parser = createParser() ) {
			var encoder = StandardCharsets.UTF_8.newEncoder().onMalformedInput( CodingErrorAction.REPORT );
			CharBuffer chars = CharBuffer.wrap( json );
			ByteBuffer bytes = ByteBuffer.allocate( 8192 );
			while ( true ) {
				CoderResult result = encoder.encode( chars, bytes, true );
				if ( result.isError() ) {
					result.throwException();
				}
				bytes.flip();
				while ( bytes.hasRemaining() ) {
					parser.feed( bytes );
				}
				bytes.clear();
				if ( result.isUnderflow() ) {
					break;
				}
			}
			return parser.endOfInput();
		}
		catch (CharacterCodingException e) {
			throw new JsonParseException( "Invalid Unicode input", e );
		}
	}
}
