/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.integrationtest.backend.tck.search.predicate;

import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThatQuery;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.types.dsl.SearchableProjectableIndexFieldTypeOptionsStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldTypeDescriptor;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.SimpleFieldModelsByType;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.BulkIndexer;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.SimpleMappedIndex;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// TODO HSEARCH-3593 test multiple field structures (in nested, ...)
public abstract class AbstractPredicateSingleFieldIT<V extends AbstractPredicateTestValues<?>> {

	@ParameterizedTest(name = "{1}")
	@MethodSource("params")
	void match(SimpleMappedIndex<IndexBinding> index, DataSet<?, V> dataSet) {
		int valueCount = dataSet.values.size();
		for ( int i = 0; i < valueCount; i++ ) {
			int matchingDocOrdinal = i;
			assertThatQuery( index.query()
					.where( f -> predicate( f, fieldPath( index, dataSet ), matchingDocOrdinal, dataSet ) )
					.routing( dataSet.routingKey ) )
					.hasDocRefHitsAnyOrder( index.typeName(), dataSet.docId( i ) );
		}
	}

	@ParameterizedTest(name = "{1}")
	@MethodSource("params")
	void matchParameter(SimpleMappedIndex<IndexBinding> index, DataSet<?, V> dataSet) {
		int valueCount = dataSet.values.size();

		var scope = index.createScope();
		var predicate = predicate( scope.predicate(), fieldPath( index, dataSet ), "query-parameter", dataSet ).toPredicate();

		for ( int matchingDocOrdinal = 0; matchingDocOrdinal < valueCount; matchingDocOrdinal++ ) {
			Map<String, Object> parameters = parameterValues( matchingDocOrdinal, dataSet, "query-parameter" );
			var query = index.query().where( predicate );
			for ( Map.Entry<String, Object> parameter : parameters.entrySet() ) {
				query.param( parameter.getKey(), parameter.getValue() );
			}
			assertThatQuery( query
					.routing( dataSet.routingKey ) )
					.hasDocRefHitsAnyOrder( index.typeName(), dataSet.docId( matchingDocOrdinal ) );
		}
	}

	protected abstract PredicateFinalStep predicate(SearchPredicateFactory f, String fieldPath, int matchingDocOrdinal,
			DataSet<?, V> dataSet);

	protected abstract PredicateFinalStep predicate(SearchPredicateFactory f, String fieldPath, String paramName,
			DataSet<?, V> dataSet);

	protected abstract Map<String, Object> parameterValues(int matchingDocOrdinal, DataSet<?, V> dataSet, String paramName);

	private String fieldPath(SimpleMappedIndex<IndexBinding> index, DataSet<?, V> dataSet) {
		return index.binding().field.get( dataSet.fieldType ).relativeFieldName;
	}

	public static final class IndexBinding {
		private final SimpleFieldModelsByType field;

		public IndexBinding(IndexSchemaElement root,
				Collection<? extends FieldTypeDescriptor<?,
						? extends SearchableProjectableIndexFieldTypeOptionsStep<?, ?>>> fieldTypes,
				Consumer<? super SearchableProjectableIndexFieldTypeOptionsStep<?, ?>> additionalConfiguration) {
			field = SimpleFieldModelsByType.mapAll( fieldTypes, root, "field0_", additionalConfiguration );
		}

		public IndexBinding(IndexSchemaElement root,
				Collection<? extends FieldTypeDescriptor<?,
						? extends SearchableProjectableIndexFieldTypeOptionsStep<?, ?>>> fieldTypes) {
			field = SimpleFieldModelsByType.mapAll( fieldTypes, root, "field0_" );
		}
	}

	public static final class DataSet<F, V extends AbstractPredicateTestValues<F>>
			extends AbstractPerFieldTypePredicateDataSet<F, V> {
		public DataSet(V values) {
			super( values );
		}

		public void contribute(SimpleMappedIndex<IndexBinding> index, BulkIndexer indexer) {
			int valueCount = values.size();
			for ( int i = 0; i < valueCount; i++ ) {
				F fieldValue = values.fieldValue( i );
				indexer.add( docId( i ), routingKey, document -> initDocument( index, document, fieldValue ) );
			}
		}

		private void initDocument(SimpleMappedIndex<IndexBinding> index, DocumentElement document,
				F fieldValue) {
			IndexBinding binding = index.binding();
			document.addValue( binding.field.get( fieldType ).reference, fieldValue );
		}
	}
}
