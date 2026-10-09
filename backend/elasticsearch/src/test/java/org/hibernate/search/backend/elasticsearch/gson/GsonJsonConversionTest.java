package org.hibernate.search.backend.elasticsearch.gson;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class GsonJsonConversionTest {
	@Test
	void directTreeConversionPreservesLexemesOrderAndIndependentContainers() {
		var original = com.google.gson.JsonParser
				.parseString( "{\"z\":1.2300e+04,\"a\":[9007199254740993,null,{\"null\":null}],\"s\":\"<>&\",\"b\":true}" )
				.getAsJsonObject();
		JsonObject search = GsonJsonConversion.fromGson( original );
		assertThat( search.keySet() ).containsExactly( "z", "a", "s", "b" );
		assertThat( search.get( "z" ).toString() ).isEqualTo( "1.2300e+04" );
		assertThat( search.getAsJsonArray( "a" ).get( 0 ).getAsLong() ).isEqualTo( 9007199254740993L );
		var roundTrip = GsonJsonConversion.toGson( search );
		assertThat( roundTrip.toString() ).isEqualTo( original.toString() );
		search.getAsJsonArray( "a" ).get( 2 ).getAsJsonObject().addProperty( "new", 1 );
		assertThat( original.getAsJsonArray( "a" ).get( 2 ).getAsJsonObject().has( "new" ) ).isFalse();
		assertThat( roundTrip.getAsJsonArray( "a" ).get( 2 ).getAsJsonObject().has( "new" ) ).isFalse();
		roundTrip.addProperty( "added", false );
		assertThat( search.has( "added" ) ).isFalse();
	}

	@Test
	void allOverloadsPreserveJavaNullAndJsonNull() {
		assertThat( GsonJsonConversion.fromGson( (com.google.gson.JsonElement) null ) ).isNull();
		assertThat( GsonJsonConversion.fromGson( (com.google.gson.JsonObject) null ) ).isNull();
		assertThat( GsonJsonConversion.fromGson( (com.google.gson.JsonArray) null ) ).isNull();
		assertThat( GsonJsonConversion.toGson( (JsonElement) null ) ).isNull();
		assertThat( GsonJsonConversion.toGson( (JsonObject) null ) ).isNull();
		assertThat( GsonJsonConversion.toGson( (JsonArray) null ) ).isNull();
		assertThat( GsonJsonConversion.fromGson( com.google.gson.JsonNull.INSTANCE ).isJsonNull() ).isTrue();
		var array = new JsonParsing().parse( "[1.00,null,{}]" ).getAsJsonArray();
		assertThat( GsonJsonConversion.fromGson( GsonJsonConversion.toGson( array ) ) ).isEqualTo( array );
	}
}
