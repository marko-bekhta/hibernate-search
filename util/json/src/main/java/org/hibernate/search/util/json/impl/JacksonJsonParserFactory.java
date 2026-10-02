package org.hibernate.search.util.json.impl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.hibernate.search.util.json.spi.JsonParseException;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsingOptions;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;

public final class JacksonJsonParserFactory {
	private final JsonFactory factory;
	private final JsonParsingOptions options;

	public JacksonJsonParserFactory(JsonParsingOptions options) {
		this.options = Objects.requireNonNull( options );
		factory = new ResumableJsonFactory( JsonFactory.builder().streamReadConstraints( StreamReadConstraints.builder()
				.maxNestingDepth( options.maxDepth() ).maxStringLength( options.maxStringLength() )
				.maxNameLength( options.maxNameLength() ).maxNumberLength( options.maxNumberLength() )
				.maxDocumentLength( -1 ).maxTokenCount( -1 ).build() ).build() );
	}

	public JsonParserSession createParser() {
		try {
			return new JacksonJsonParserSession( factory.createNonBlockingByteBufferParser(), options );
		}
		catch (IOException e) {
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
