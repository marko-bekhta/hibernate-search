package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;

class ElasticsearchNativeJsonCharacterizationTest {

	@Test
	void nativeValuesShareTreeIdentityAndMapNulls() {
		ElasticsearchJsonElementFieldCodec codec = new ElasticsearchJsonElementFieldCodec( new Gson() );
		JsonElement value = JsonParser.parseString( "{\"native\":[1,9007199254740993,null,{\"text\":\"é漢😀\"}]}" );
		assertThat( codec.encode( value ) ).isSameAs( value );
		assertThat( codec.decode( value ) ).isSameAs( value );
		assertThat( codec.encode( null ) ).isSameAs( JsonNull.INSTANCE );
		assertThat( codec.decode( (JsonElement) null ) ).isNull();
		assertThat( codec.decode( JsonNull.INSTANCE ) ).isNull();
		JsonElement reparsed = codec.fromJsonStringToElement( codec.fromJsonElementToString( value ) );
		assertThat( reparsed ).isEqualTo( value ).isNotSameAs( value );
		assertThat( reparsed.getAsJsonObject().getAsJsonArray( "native" ).get( 1 ).getAsLong() )
				.isEqualTo( 9007199254740993L );
	}
}
