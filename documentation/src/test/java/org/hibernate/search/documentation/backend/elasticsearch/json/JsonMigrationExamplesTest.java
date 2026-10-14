package org.hibernate.search.documentation.backend.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchResponse;
import org.hibernate.search.backend.elasticsearch.gson.GsonJsonHandlers;
import org.hibernate.search.backend.elasticsearch.json.jackson.JacksonJsonHandlers;
import org.hibernate.search.util.json.JsonHandler;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class JsonMigrationExamplesTest {
	@Test
	void handlersAndTreeOwnership() {
		// tag::migration[]
		JsonHandler<ObjectNode> jackson = JacksonJsonHandlers.object();
		ObjectNode query = jackson.fromJson( "{\"term\":{\"name\":\"test\"},\"custom\":null}" );
		ObjectNode copy = query.deepCopy();
		copy.put( "boost", 2 );

		ElasticsearchRequest request = ElasticsearchRequest.builder( "POST" )
				.wholeEncodedPath( "/_search" ).bodyJson( query ).build();
		ObjectNode borrowed = request.bodyParts( jackson ).get( 0 );
		com.google.gson.JsonObject independentGsonCopy = request.bodyParts( GsonJsonHandlers.object() ).get( 0 );
		// end::migration[]
		assertThat( query.has( "boost" ) ).isFalse();
		assertThat( borrowed ).isSameAs( query );
		assertThat( independentGsonCopy.get( "custom" ).isJsonNull() ).isTrue();
		independentGsonCopy.addProperty( "changed", true );
		assertThat( query.has( "changed" ) ).isFalse();
	}

	@Test
	@SuppressWarnings("deprecation")
	void deprecatedGsonConveniencesKeepTheirGsonTypes() {
		// tag::legacy-gson[]
		com.google.gson.JsonObject input = GsonJsonHandlers.object().fromJson(
				"{\"number\":9007199254740993,\"custom\":null}" );
		ElasticsearchRequest request = ElasticsearchRequest.builder( "POST" )
				.wholeEncodedPath( "/_search" ).body( input ).build();
		List<com.google.gson.JsonObject> oldParts = request.bodyParts();
		ElasticsearchResponse response = new ElasticsearchResponse( "HTTP/1.1", 200, "OK", input );
		com.google.gson.JsonObject oldBody = response.body();
		// end::legacy-gson[]
		assertThat( oldParts.get( 0 ).get( "number" ).getAsLong() ).isEqualTo( 9007199254740993L );
		assertThat( oldBody.get( "custom" ).isJsonNull() ).isTrue();
		oldBody.addProperty( "changed", true );
		assertThat( response.body( JacksonJsonHandlers.object() ).has( "changed" ) ).isFalse();
		input.addProperty( "later", true );
		assertThat( request.bodyPartsJson().get( 0 ).has( "later" ) ).isFalse();
	}

	@Test
	void treeNavigation() {
		// tag::tree-navigation[]
		ObjectNode root = JacksonJsonHandlers.object().fromJson( "{\"parent\":[{\"value\":[42]}]}" );
		ObjectNode parent = (ObjectNode) root.path( "parent" ).path( 0 );
		long value = parent.path( "value" ).path( 0 ).longValue();
		parent.put( "value", 7L );
		boolean absent = root.path( "missing" ).isMissingNode();
		// end::tree-navigation[]
		assertThat( value ).isEqualTo( 42L );
		assertThat( absent ).isTrue();
		assertThat( root.toString() ).isEqualTo( "{\"parent\":[{\"value\":7}]}" );
	}

	@Test
	void exactNumbersAndGeneralValues() {
		// tag::numeric-values[]
		JsonHandler<JsonNode> values = JacksonJsonHandlers.value();
		JsonNode tree = values.fromJson( "[9007199254740993,0.123456789012345678901234567890,-0.0,null]" );
		BigInteger integer = tree.get( 0 ).bigIntegerValue();
		BigDecimal decimal = tree.get( 1 ).decimalValue();
		// end::numeric-values[]
		assertThat( integer ).isEqualTo( new BigInteger( "9007199254740993" ) );
		assertThat( decimal ).isEqualTo( new BigDecimal( "0.123456789012345678901234567890" ) );
		assertThat( Double.doubleToRawLongBits( tree.get( 2 ).doubleValue() ) ).isEqualTo( Long.MIN_VALUE );
		assertThat( tree.get( 3 ).isNull() ).isTrue();
		assertThat( values.fromJson( "null" ).isNull() ).isTrue();
		assertThat( values.fromJson( "true" ).booleanValue() ).isTrue();
		assertThatThrownBy( () -> values.fromJson( "1e2147483648" ) ).isInstanceOf( RuntimeException.class );
	}

	// tag::parser[]
	static JsonNode parseUtf8(JsonParsing parsing, Iterable<ByteBuffer> chunks) {
		try ( JsonParserSession session = parsing.createParser() ) {
			for ( ByteBuffer chunk : chunks ) {
				while ( chunk.hasRemaining() ) {
					session.feed( chunk );
				}
			}
			return session.endOfInput();
		}
	}
	// end::parser[]

	@Test
	void fragmentedInputAndCursorReplay() throws Exception {
		JsonNode root = parseUtf8( new JsonParsing(), List.of(
				ByteBuffer.wrap( "{\"v\":".getBytes( StandardCharsets.UTF_8 ) ),
				ByteBuffer.wrap( "42}".getBytes( StandardCharsets.UTF_8 ) ) ) );
		// tag::cursor[]
		JsonBody body = JsonBodies.of( root );
		ByteBuffer destination = ByteBuffer.allocate( 5 );
		try ( JsonWriteCursor cursor = body.openCursor() ) {
			while ( !cursor.isComplete() ) {
				destination.clear();
				cursor.write( destination );
				destination.flip();
				consumeFully( destination ); // Finish consuming before reusing the storage.
			}
		}
		// end::cursor[]
		assertThat( body.contentLength() ).isEqualTo( 8 );
		try ( var signingRead = body.openStream(); var replay = body.openStream() ) {
			assertThat( signingRead.readAllBytes() ).isEqualTo( replay.readAllBytes() );
		}
	}

	static void consumeFully(ByteBuffer destination) {
		destination.position( destination.limit() );
	}
}
