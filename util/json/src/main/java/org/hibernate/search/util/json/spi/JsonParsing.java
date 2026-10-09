package org.hibernate.search.util.json.spi;

import java.nio.charset.Charset;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.impl.JacksonJsonParserFactory;

/** Shareable parser factory with immutable constraints and library-neutral signatures. */
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

	/** Parse an already materialized string without creating a secondary encoded body. */
	public JsonElement parse(String json) {
		return implementation.parse( json );
	}
}
