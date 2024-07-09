/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.lucene.types.predicate.impl;

import org.hibernate.search.backend.lucene.lowlevel.common.impl.MetadataFields;
import org.hibernate.search.backend.lucene.search.common.impl.AbstractLuceneValueFieldSearchQueryElementFactory;
import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexScope;
import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexValueFieldContext;
import org.hibernate.search.backend.lucene.search.predicate.impl.AbstractLuceneLeafSingleFieldPredicate;
import org.hibernate.search.backend.lucene.search.predicate.impl.PredicateRequestContext;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.spi.ExistsPredicateBuilder;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class LuceneExistsPredicate extends AbstractLuceneLeafSingleFieldPredicate {

	private LuceneExistsPredicate(AbstractBuilder<?, ?> builder) {
		super( builder );
	}

	private abstract static class AbstractBuilder<F, E> extends AbstractLuceneLeafSingleFieldPredicate.AbstractBuilder<F, E>
			implements ExistsPredicateBuilder {
		private AbstractBuilder(LuceneSearchIndexScope<?> scope, LuceneSearchIndexValueFieldContext<F, E> field) {
			super( scope, field );
			// Score is always constant for this query
			constantScore();
		}

		@Override
		public SearchPredicate build() {
			return new LuceneExistsPredicate( this );
		}

		@Override
		protected abstract Query buildQuery(PredicateRequestContext context);
	}

	public static class DocValuesOrNormsBasedFactory<F, E>
			extends AbstractLuceneValueFieldSearchQueryElementFactory<ExistsPredicateBuilder, F, E> {
		@Override
		public DocValuesOrNormsBasedBuilder<F, E> create(LuceneSearchIndexScope<?> scope,
				LuceneSearchIndexValueFieldContext<F, E> field) {
			return new DocValuesOrNormsBasedBuilder<>( scope, field );
		}
	}

	private static class DocValuesOrNormsBasedBuilder<F, E> extends AbstractBuilder<F, E> implements ExistsPredicateBuilder {
		private DocValuesOrNormsBasedBuilder(LuceneSearchIndexScope<?> scope, LuceneSearchIndexValueFieldContext<F, E> field) {
			super( scope, field );
		}

		@Override
		protected Query buildQuery(PredicateRequestContext context) {
			return new FieldExistsQuery( absoluteFieldPath );
		}
	}

	public static class DefaultFactory<F, E>
			extends AbstractLuceneValueFieldSearchQueryElementFactory<ExistsPredicateBuilder, F, E> {
		@Override
		public DefaultBuilder<F, E> create(LuceneSearchIndexScope<?> scope, LuceneSearchIndexValueFieldContext<F, E> field) {
			return new DefaultBuilder<>( scope, field );
		}
	}

	private static class DefaultBuilder<F, E> extends AbstractBuilder<F,E> implements ExistsPredicateBuilder {
		private DefaultBuilder(LuceneSearchIndexScope<?> scope, LuceneSearchIndexValueFieldContext<F, E> field) {
			super( scope, field );
		}

		@Override
		protected Query buildQuery(PredicateRequestContext context) {
			return new TermQuery( new Term( MetadataFields.fieldNamesFieldName(), absoluteFieldPath ) );
		}
	}

}
