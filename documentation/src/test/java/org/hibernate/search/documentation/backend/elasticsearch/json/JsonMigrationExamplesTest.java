package org.hibernate.search.documentation.backend.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.gson.GsonJsonConversion;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

import org.junit.jupiter.api.Test;

class JsonMigrationExamplesTest {
	@Test
	@SuppressWarnings("removal")
	void migratingTrees() {
		// tag::migration[]
		JsonObject query = new JsonParsing().parse( "{\"term\":{\"name\":\"test\"}}" ).getAsJsonObject();
		JsonObject copy = query.deepCopy();
		copy.addProperty( "boost", 2 );

		com.google.gson.JsonObject legacy = new com.google.gson.JsonObject();
		legacy.addProperty( "name", "test" );
		JsonObject migrated = GsonJsonConversion.fromGson( legacy );
		com.google.gson.JsonObject independentLegacyCopy = GsonJsonConversion.toGson( migrated );
		// end::migration[]
		assertThat( query.has( "boost" ) ).isFalse();
		assertThat( migrated.get( "name" ).getAsString() ).isEqualTo( "test" );
		independentLegacyCopy.addProperty( "changed", true );
		assertThat( migrated.has( "changed" ) ).isFalse();
	}

	@Test
	void accessorAdaptation() {
		// tag::accessors[]
		var strict = JsonAccessor.root().property( "parent" ).property( "value" ).asLong();
		var tolerant = JsonAccessor.root().property( "parent" ).unwrapSingletonArrays()
				.property( "value" ).unwrapSingletonArrays().asLong();
		JsonObject root = new JsonParsing().parse( "{\"parent\":[{\"value\":[42]}]}" ).getAsJsonObject();
		long value = tolerant.get( root ).orElseThrow();
		tolerant.set( root, 7L ); // Preserves the parent array, replaces the value array.
		// end::accessors[]
		assertThat( value ).isEqualTo( 42L );
		assertThat( root.toString() ).isEqualTo( "{\"parent\":[{\"value\":7}]}" );
		assertThat( strict.get( new JsonParsing().parse( "{\"parent\":{\"value\":42}}" ).getAsJsonObject() ) )
				.contains( 42L );
	}

	// tag::parser[]
	static JsonElement parseUtf8(JsonParsing parsing, Iterable<ByteBuffer> chunks) {
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
		JsonElement root = parseUtf8( new JsonParsing(), List.of(
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
