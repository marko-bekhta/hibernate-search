package org.hibernate.search.backend.elasticsearch.search.query.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;

import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;

class ElasticsearchSearchRequestTransformerCharacterizationTest {

	@Test
	void copiesBodyOnFirstAccessAndKeepsItForSubsequentAccesses() {
		JsonObject body = new JsonParsing().parse( "{\"query\":{\"term\":{\"name\":\"original\"}}}" ).getAsJsonObject();
		ElasticsearchRequest original = ElasticsearchRequest.builder( "POST" ).wholeEncodedPath( "/index/_search" )
				.param( "size", "10" ).body( body ).build();
		Function<ElasticsearchRequest, ElasticsearchRequest> transform =
				ElasticsearchSearchRequestTransformerContextImpl.createTransformerFunction( context -> {
					JsonObject copy = context.body();
					assertThat( context.body() ).isSameAs( copy ).isNotSameAs( body );
					copy.getAsJsonObject( "query" ).getAsJsonObject( "term" ).addProperty( "name", "changed" );
					context.parametersMap().put( "size", "20" );
				} );
		ElasticsearchRequest transformed = transform.apply( original );
		assertThat( body.getAsJsonObject( "query" ).getAsJsonObject( "term" ).get( "name" ).getAsString() )
				.isEqualTo( "original" );
		assertThat( original.parameters().get( "size" ) ).isEqualTo( "10" );
		assertThat( transformed.parameters().get( "size" ) ).isEqualTo( "20" );
		assertThat( transformed.bodyParts().get( 0 ) ).isNotSameAs( body );
		assertThat( transform.apply( original ).bodyParts().get( 0 ) ).isNotSameAs( transformed.bodyParts().get( 0 ) );
	}

	@Test
	void readingBodyCopiesItAndPathOnlyTransformationSharesIt() {
		JsonObject body = new JsonObject();
		ElasticsearchRequest original =
				ElasticsearchRequest.builder( "POST" ).wholeEncodedPath( "/_search" ).body( body ).build();
		assertThat( ElasticsearchSearchRequestTransformerContextImpl.createTransformerFunction( context -> context.body() )
				.apply( original ).bodyParts().get( 0 ) ).isNotSameAs( body );
		ElasticsearchRequest pathOnly = ElasticsearchSearchRequestTransformerContextImpl
				.createTransformerFunction( context -> context.path( "/other/_search" ) ).apply( original );
		assertThat( pathOnly.bodyParts().get( 0 ) ).isSameAs( body );
		assertThat( pathOnly.path() ).isEqualTo( "/other/_search" );
		assertThat( ElasticsearchSearchRequestTransformerContextImpl.createTransformerFunction( null ) ).isNull();
	}

	@Test
	void requiresExactlyOneBodyPart() {
		Function<ElasticsearchRequest, ElasticsearchRequest> transform =
				ElasticsearchSearchRequestTransformerContextImpl.createTransformerFunction( context -> {} );
		assertThatThrownBy(
				() -> transform.apply( ElasticsearchRequest.builder( "POST" ).wholeEncodedPath( "/_search" ).build() ) )
				.isInstanceOf( AssertionFailure.class );
		assertThatThrownBy( () -> transform.apply( ElasticsearchRequest.builder( "POST" ).wholeEncodedPath( "/_search" )
				.body( new JsonObject() ).body( new JsonObject() ).build() ) ).isInstanceOf( AssertionFailure.class );
	}
}
