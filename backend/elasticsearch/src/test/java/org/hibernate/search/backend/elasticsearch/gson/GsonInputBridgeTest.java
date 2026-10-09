package org.hibernate.search.backend.elasticsearch.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hibernate.search.backend.elasticsearch.search.aggregation.dsl.ElasticsearchSearchAggregationFactory;
import org.hibernate.search.backend.elasticsearch.search.predicate.dsl.ElasticsearchSearchPredicateFactory;
import org.hibernate.search.backend.elasticsearch.search.sort.dsl.ElasticsearchSearchSortFactory;
import org.hibernate.search.backend.elasticsearch.types.dsl.ElasticsearchNativeIndexFieldTypeMappingStep;
import org.hibernate.search.util.json.JsonObject;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

@SuppressWarnings("removal")
class GsonInputBridgeTest {
	@Test
	void eachDeprecatedInputBridgeConvertsOnceAndCallsThePrimarySearchOverload() {
		var input = com.google.gson.JsonParser.parseString( "{\"field\":{\"number\":9007199254740993}}" ).getAsJsonObject();
		var predicate = mock( ElasticsearchSearchPredicateFactory.class, CALLS_REAL_METHODS );
		predicate.fromJson( input );
		var captured = ArgumentCaptor.forClass( JsonObject.class );
		verify( predicate ).fromJson( captured.capture() );
		assertIndependent( input, captured.getValue() );

		var sort = mock( ElasticsearchSearchSortFactory.class, CALLS_REAL_METHODS );
		sort.fromJson( input );
		verify( sort ).fromJson( captured.capture() );
		assertIndependent( input, captured.getValue() );

		var aggregation = mock( ElasticsearchSearchAggregationFactory.class, CALLS_REAL_METHODS );
		aggregation.fromJson( input );
		verify( aggregation ).fromJson( captured.capture() );
		assertIndependent( input, captured.getValue() );

		var mapping = mock( ElasticsearchNativeIndexFieldTypeMappingStep.class, CALLS_REAL_METHODS );
		mapping.mapping( input );
		verify( mapping ).mapping( captured.capture() );
		assertIndependent( input, captured.getValue() );
	}

	@Test
	void javaNullRemainsJavaNull() {
		var predicate = mock( ElasticsearchSearchPredicateFactory.class, CALLS_REAL_METHODS );
		predicate.fromJson( (com.google.gson.JsonObject) null );
		verify( predicate ).fromJson( (JsonObject) null );
	}

	private static void assertIndependent(com.google.gson.JsonObject input, JsonObject converted) {
		assertThat( converted.getAsJsonObject( "field" ).get( "number" ).getAsLong() ).isEqualTo( 9007199254740993L );
		converted.getAsJsonObject( "field" ).addProperty( "changed", true );
		assertThat( input.getAsJsonObject( "field" ).has( "changed" ) ).isFalse();
	}
}
