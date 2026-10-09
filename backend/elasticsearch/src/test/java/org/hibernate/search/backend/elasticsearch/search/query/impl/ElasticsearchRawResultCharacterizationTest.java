package org.hibernate.search.backend.elasticsearch.search.query.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.hibernate.search.engine.search.query.spi.SimpleSearchResultTotal;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;

class ElasticsearchRawResultCharacterizationTest {

	@Test
	void rawResponseReturnsOriginalMutableTree() {
		JsonObject response = new JsonParsing().parse( "{\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},"
				+ "\"hits\":[{\"_id\":\"1\",\"_source\":{\"number\":9007199254740993}}]},\"custom\":null}" ).getAsJsonObject();
		ElasticsearchSearchResultImpl<String> result = new ElasticsearchSearchResultImpl<>( response,
				SimpleSearchResultTotal.exact( 1 ), List.of( "1" ), Map.of(), 2, false, null );
		assertThat( result.responseBody() ).isSameAs( response );
		result.responseBody().addProperty( "changed", true );
		assertThat( response.get( "changed" ).getAsBoolean() ).isTrue();
		assertThat( response.getAsJsonObject( "hits" ).getAsJsonArray( "hits" ).get( 0 ).getAsJsonObject()
				.getAsJsonObject( "_source" ).get( "number" ).getAsLong() ).isEqualTo( 9007199254740993L );
	}
}
