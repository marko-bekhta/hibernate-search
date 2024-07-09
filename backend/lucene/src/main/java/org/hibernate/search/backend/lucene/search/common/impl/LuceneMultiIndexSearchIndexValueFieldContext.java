/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.lucene.search.common.impl;

import java.util.List;

import org.hibernate.search.backend.lucene.types.codec.impl.LuceneFieldCodec;
import org.hibernate.search.engine.search.common.spi.AbstractMultiIndexSearchIndexValueFieldContext;
import org.hibernate.search.engine.search.common.spi.SearchIndexSchemaElementContextHelper;

import org.apache.lucene.analysis.Analyzer;

public class LuceneMultiIndexSearchIndexValueFieldContext<F, E>
		extends AbstractMultiIndexSearchIndexValueFieldContext<
				LuceneSearchIndexValueFieldContext<F, E>,
				LuceneSearchIndexScope<?>,
				LuceneSearchIndexValueFieldTypeContext<F, E>,
				F,
				E>
		implements LuceneSearchIndexValueFieldContext<F, E>, LuceneSearchIndexValueFieldTypeContext<F, E> {

	public LuceneMultiIndexSearchIndexValueFieldContext(LuceneSearchIndexScope<?> scope, String absolutePath,
			List<? extends LuceneSearchIndexValueFieldContext<F, E>> fieldForEachIndex) {
		super( scope, absolutePath, fieldForEachIndex );
	}

	@Override
	protected LuceneSearchIndexValueFieldContext<F, E> self() {
		return this;
	}

	@Override
	protected LuceneSearchIndexValueFieldTypeContext<F, E> selfAsNodeType() {
		return this;
	}

	@Override
	protected LuceneSearchIndexValueFieldTypeContext<F, E> typeOf(LuceneSearchIndexValueFieldContext<F, E> indexElement) {
		return indexElement.type();
	}

	@Override
	public LuceneSearchIndexCompositeNodeContext toComposite() {
		return SearchIndexSchemaElementContextHelper.throwingToComposite( this );
	}

	@Override
	public LuceneSearchIndexCompositeNodeContext toObjectField() {
		return SearchIndexSchemaElementContextHelper.throwingToObjectField( this );
	}

	@Override
	public Analyzer searchAnalyzerOrNormalizer() {
		return fromTypeIfCompatible( LuceneSearchIndexValueFieldTypeContext::searchAnalyzerOrNormalizer, Object::equals,
				"searchAnalyzerOrNormalizer" );
	}

	@Override
	public boolean hasTermVectorsConfigured() {
		return fromTypeIfCompatible( LuceneSearchIndexValueFieldTypeContext::hasTermVectorsConfigured, Object::equals,
				"hasTermVectorsConfigured" );
	}

	@Override
	public LuceneFieldCodec<F, E> codec() {
		return fromTypeIfCompatible( LuceneSearchIndexValueFieldTypeContext::codec, LuceneFieldCodec::isCompatibleWith,
				"codec" );
	}
}
