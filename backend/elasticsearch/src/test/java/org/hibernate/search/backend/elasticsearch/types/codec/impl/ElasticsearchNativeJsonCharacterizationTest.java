package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;

class ElasticsearchNativeJsonCharacterizationTest {

	@Test
	void nativeValuesShareTreeIdentityAndMapNulls() {
		ElasticsearchJsonElementFieldCodec codec = new ElasticsearchJsonElementFieldCodec(
				org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper.create( false ).getCodec() );
		JsonElement value = new JsonParsing().parse( "{\"native\":[1,9007199254740993,null,{\"text\":\"é漢😀\"}]}" );
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
