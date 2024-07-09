/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.lucene.search.predicate.impl;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

import org.hibernate.search.backend.lucene.logging.impl.Log;
import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexScope;
import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexValueFieldContext;
import org.hibernate.search.engine.search.common.ValueModel;
import org.hibernate.search.util.common.data.Range;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

import org.apache.lucene.search.Query;

public abstract class AbstractLuceneLeafSingleFieldPredicate extends AbstractLuceneSingleFieldPredicate {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final AbstractBuilder<?, ?> builder;

	protected AbstractLuceneLeafSingleFieldPredicate(AbstractBuilder<?, ?> builder) {
		super( builder );
		this.builder = builder;
	}

	@Override
	protected final Query doToQuery(PredicateRequestContext context) {
		return builder.buildQuery( context );
	}

	public abstract static class AbstractBuilder<F, E>
			extends AbstractLuceneSingleFieldPredicate.AbstractBuilder {
		protected final LuceneSearchIndexValueFieldContext<F, E> field;

		protected AbstractBuilder(LuceneSearchIndexScope<?> scope, LuceneSearchIndexValueFieldContext<F, E> field) {
			super( scope, field );
			this.field = field;
		}

		protected abstract Query buildQuery(PredicateRequestContext context);

		protected E convertAndEncode(Object value, ValueModel valueModel) {
			try {
				return field.type().dslConverter( valueModel )
						.unknownTypeToDocumentValue( value, scope.toDocumentValueConvertContext() );
			}
			catch (RuntimeException e) {
				throw log.cannotConvertDslParameter( e.getMessage(), e, field.eventContext() );
			}
		}

		protected Range<E> convertAndEncode(Range<?> range,
											ValueModel lowerBoundModel,
											ValueModel upperBoundModel) {
			return Range.between(
					convertAndEncode( range.lowerBoundValue(), lowerBoundModel ),
					range.lowerBoundInclusion(),
					convertAndEncode( range.upperBoundValue(), upperBoundModel ),
					range.upperBoundInclusion()
			);
		}

		private E convertAndEncode(Optional<?> valueOptional, ValueModel valueModel) {
			if ( valueOptional.isEmpty() ) {
				return null;
			}
			else {
				return convertAndEncode( valueOptional.get(), valueModel );
			}
		}
	}
}
