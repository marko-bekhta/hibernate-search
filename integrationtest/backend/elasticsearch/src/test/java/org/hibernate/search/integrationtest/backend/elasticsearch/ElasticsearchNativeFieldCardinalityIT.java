package org.hibernate.search.integrationtest.backend.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.backend.elasticsearch.json.jackson.JacksonJsonHandlers;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFactory;
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFinalStep;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.extension.SearchSetupHelper;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.SimpleMappedIndex;
import org.hibernate.search.util.json.JsonHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class ElasticsearchNativeFieldCardinalityIT {
	@RegisterExtension
	public final SearchSetupHelper setupHelper = SearchSetupHelper.create();

	private final SimpleMappedIndex<IndexBinding> singleIndex =
			SimpleMappedIndex.of( root -> new IndexBinding( root, false ) ).name( "native-single" );
	private final SimpleMappedIndex<IndexBinding> multiIndex =
			SimpleMappedIndex.of( root -> new IndexBinding( root, true ) ).name( "native-multi" );

	@BeforeEach
	void setup() {
		setupHelper.start().withIndexes( singleIndex, multiIndex ).setup();
	}

	@Test
	void multipleObjectsProjectIndependentlyWithObjectAndGeneralHandlers() {
		ObjectNode first = JacksonJsonHandlers.object().fromJson( "{\"value\":1,\"nested\":null}" );
		ObjectNode second = JacksonJsonHandlers.object().fromJson( "{\"value\":2}" );
		multiIndex.bulkIndexer().add( "two", document -> {
			document.addValue( multiIndex.binding().objects, first );
			document.addValue( multiIndex.binding().objects, second );
			document.addValue( multiIndex.binding().values, first );
			document.addValue( multiIndex.binding().values, second );
		} ).add( "one", document -> document.addValue( multiIndex.binding().objects, first ) )
				.add( "missing", document -> {} )
				.add( "null", document -> document.addValue( multiIndex.binding().objects, null ) ).join();
		var scope = multiIndex.createScope();
		assertThat( scope.query().select( f -> f.field( "objects", ObjectNode.class ).list() )
				.where( f -> f.id().matching( "two" ) ).fetchSingleHit().orElseThrow() ).containsExactly( first, second );
		assertThat( scope.query().select( f -> f.field( "values", JsonNode.class ).list() )
				.where( f -> f.id().matching( "two" ) ).fetchSingleHit().orElseThrow() ).containsExactly( first, second );
		assertThat( scope.query().select( f -> f.field( "objects", ObjectNode.class ).list() )
				.where( f -> f.id().matching( "one" ) ).fetchSingleHit().orElseThrow() ).containsExactly( first );
		assertThat( scope.query().select( f -> f.field( "objects", ObjectNode.class ).list() )
				.where( f -> f.id().matching( "missing" ) ).fetchSingleHit().orElseThrow() ).isEmpty();
		assertThat( scope.query().select( f -> f.field( "objects", ObjectNode.class ).list() )
				.where( f -> f.id().matching( "null" ) ).fetchSingleHit().orElseThrow() ).containsExactly( (ObjectNode) null );
	}

	@Test
	void atomicArraysSurviveSingleAndMultiValuedIndexingAndMixedIndexSearch() {
		JsonNode value = JacksonJsonHandlers.value().fromJson( "[1,null,{\"nested\":null}]" );
		JsonNode empty = JacksonJsonHandlers.value().fromJson( "[]" );
		singleIndex.bulkIndexer().add( "single", document -> document.addValue( singleIndex.binding().values, value ) ).join();
		multiIndex.bulkIndexer().add( "multi", document -> {
			document.addValue( multiIndex.binding().values, value );
			document.addValue( multiIndex.binding().values, value );
			document.addValue( multiIndex.binding().values, empty );
		} ).join();
		assertThat( value ).hasSize( 3 );
		assertThat( singleIndex.createScope().query().select( f -> f.field( "values", JsonNode.class ) )
				.where( f -> f.matchAll() ).fetchSingleHit().orElseThrow() ).isEqualTo( value );
		assertThat( multiIndex.createScope().query().select( f -> f.field( "values", JsonNode.class ).list() )
				.where( f -> f.matchAll() ).fetchSingleHit().orElseThrow() ).containsExactly( value, value, empty );
		assertThat( singleIndex.createScope( multiIndex ).query().select( f -> f.field( "values", JsonNode.class ).list() )
				.where( f -> f.matchAll() ).fetchAllHits() )
				.containsExactlyInAnyOrder( List.of( value ), List.of( value, value, empty ) );
	}

	private static class IndexBinding {
		final IndexFieldReference<ObjectNode> objects;
		final IndexFieldReference<JsonNode> values;

		IndexBinding(IndexSchemaElement root, boolean multiValued) {
			objects = field( root, "objects", JacksonJsonHandlers.object(), multiValued );
			values = field( root, "values", JacksonJsonHandlers.value(), multiValued );
		}

		private static <T> IndexFieldReference<T> field(IndexSchemaElement root, String name,
				JsonHandler<T> handler, boolean multiValued) {
			var field = root.field( name, f -> nativeType( f, handler ) );
			if ( multiValued ) {
				field.multiValued();
			}
			return field.toReference();
		}

		private static <T> IndexFieldTypeFinalStep<T> nativeType(
				IndexFieldTypeFactory factory, JsonHandler<T> handler) {
			return factory.extension( ElasticsearchExtension.get() ).asNative( handler )
					.mapping( "{\"type\":\"object\",\"enabled\":false}" );
		}
	}
}
