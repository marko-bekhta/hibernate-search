package org.hibernate.search.util.json.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.COLLECTION;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.hibernate.search.util.json.spi.JsonParseException;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;

class JsonParserCleanupTest {
	@Test
	void utf8SessionsReleasePartialTreesAndParserReferencesOnEveryTerminalPath() {
		JsonParsing parsing = new JsonParsing();
		JsonParserSession cancelled = parsing.createParser();
		cancelled.feed( ByteBuffer.wrap( "{\"a\":[1,".getBytes( StandardCharsets.UTF_8 ) ) );
		assertThat( cancelled ).extracting( "builder" ).extracting( "root" )
				.isEqualTo( parsing.parse( "{\"a\":[1]}" ) );
		assertThat( cancelled ).extracting( "parser" ).extracting( "_inputBuffer" )
				.satisfies( value -> assertThat( ( (ByteBuffer) value ).capacity() ).isZero() );
		cancelled.close();
		assertUtf8Clean( cancelled );
		JsonParserSession failed = parsing.createParser();
		failed.feed( ByteBuffer.wrap( "{\"a\":[1,".getBytes( StandardCharsets.UTF_8 ) ) );
		assertThatThrownBy( failed::endOfInput ).isInstanceOf( JsonParseException.class );
		assertUtf8Clean( failed );
		JsonParserSession completed = parsing.createParser();
		completed.feed( ByteBuffer.wrap( "{\"a\":[1]}".getBytes( StandardCharsets.UTF_8 ) ) );
		assertThat( completed.endOfInput().getAsJsonObject().getAsJsonArray( "a" ) ).hasSize( 1 );
		assertUtf8Clean( completed );
	}

	@Test
	void transcodingSessionsReleaseAllBuffersOnCancellationFailureAndCompletion() {
		JsonParsing parsing = new JsonParsing();
		JsonParserSession cancelled = parsing.createParser( StandardCharsets.UTF_16LE );
		cancelled.feed( ByteBuffer.wrap( "{\"a\":[1,".getBytes( StandardCharsets.UTF_16LE ) ) );
		cancelled.close();
		assertTranscoderClean( cancelled );
		JsonParserSession failed = parsing.createParser( StandardCharsets.UTF_16LE );
		failed.feed( ByteBuffer.wrap( new byte[] { '"', 0, 'a' } ) );
		assertThatThrownBy( failed::endOfInput ).isInstanceOf( JsonParseException.class );
		assertTranscoderClean( failed );
		JsonParserSession completed = parsing.createParser( StandardCharsets.UTF_16LE );
		completed.feed( ByteBuffer.wrap( "{}".getBytes( StandardCharsets.UTF_16LE ) ) );
		completed.endOfInput();
		assertTranscoderClean( completed );
	}

	private static void assertUtf8Clean(JsonParserSession parser) {
		assertThat( parser ).extracting( "parser", "feeder" ).containsExactly( null, null );
		assertThat( parser ).extracting( "builder" ).extracting( "root" ).isNull();
		assertThat( parser ).extracting( "builder" ).extracting( "frames" ).asInstanceOf( COLLECTION ).isEmpty();
	}

	private static void assertTranscoderClean(JsonParserSession parser) {
		assertThat( parser ).extracting( "delegate", "decoder", "encoder", "pending", "chars", "utf8" )
				.containsExactly( null, null, null, null, null, null );
	}
}
