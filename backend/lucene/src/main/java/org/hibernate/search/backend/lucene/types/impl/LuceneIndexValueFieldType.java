/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.lucene.types.impl;

import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexScope;
import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexValueFieldContext;
import org.hibernate.search.backend.lucene.search.common.impl.LuceneSearchIndexValueFieldTypeContext;
import org.hibernate.search.backend.lucene.types.codec.impl.LuceneFieldCodec;
import org.hibernate.search.engine.backend.types.IndexFieldType;
import org.hibernate.search.engine.backend.types.converter.spi.DslConverter;
import org.hibernate.search.engine.backend.types.converter.spi.ProjectionConverter;
import org.hibernate.search.engine.backend.types.spi.AbstractIndexValueFieldType;

import org.apache.lucene.analysis.Analyzer;

public final class LuceneIndexValueFieldType<F, E>
		extends AbstractIndexValueFieldType<
				LuceneSearchIndexScope<?>,
				LuceneSearchIndexValueFieldContext<F, E>,
				F,
				E>
		implements IndexFieldType<F>, LuceneSearchIndexValueFieldTypeContext<F, E> {

	private final LuceneFieldCodec<F, E> codec;
	private final Analyzer indexingAnalyzerOrNormalizer;
	private final Analyzer searchAnalyzerOrNormalizer;
	private final boolean hasTermVectorsConfigured;
	private final ProjectionConverter<F, ?> rawProjectionConverter;
	private final DslConverter<E, E, E> rawDslConverter;

	private LuceneIndexValueFieldType(Builder<F, E> builder) {
		super( builder );
		this.codec = builder.codec();
		this.indexingAnalyzerOrNormalizer = builder.indexingAnalyzerOrNormalizer;
		this.searchAnalyzerOrNormalizer = builder.searchAnalyzerOrNormalizer;
		this.hasTermVectorsConfigured = builder.hasTermVectorsConfigured;
		this.rawProjectionConverter = rawProjectionConverter( codec );
		this.rawDslConverter = rawDslConverter( codec );
	}

	private static <F, E> ProjectionConverter<F, E> rawProjectionConverter(LuceneFieldCodec<F, E> codec) {
		return new ProjectionConverter<>( codec.encodedType(), null );
	}

	private static <E> DslConverter<E, E, E> rawDslConverter(LuceneFieldCodec<?, E> codec) {
		return DslConverter.passThrough( codec.encodedType() );
	}

	@Override
	public LuceneFieldCodec<F, E> codec() {
		return codec;
	}

	public Analyzer indexingAnalyzerOrNormalizer() {
		return indexingAnalyzerOrNormalizer;
	}

	@Override
	public Analyzer searchAnalyzerOrNormalizer() {
		return searchAnalyzerOrNormalizer;
	}

	@Override
	public boolean hasTermVectorsConfigured() {
		return hasTermVectorsConfigured;
	}

	@Override
	public DslConverter<E, E, E> rawDslConverter() {
		return rawDslConverter;
	}

	@Override
	public ProjectionConverter<F, ?> rawProjectionConverter() {
		return rawProjectionConverter;
	}

	public static class Builder<F, E>
			extends AbstractIndexValueFieldType.Builder<
					LuceneSearchIndexScope<?>,
					LuceneSearchIndexValueFieldContext<F, E>,
					F,
					E> {

		private Analyzer indexingAnalyzerOrNormalizer;
		private Analyzer searchAnalyzerOrNormalizer;
		private boolean hasTermVectorsConfigured;

		public Builder(Class<F> valueClass) {
			super( valueClass );
		}

		LuceneFieldCodec<F, E> codec() {
			return (LuceneFieldCodec<F, E>) codec;
		}

		public void indexingAnalyzerOrNormalizer(Analyzer analyzer) {
			this.indexingAnalyzerOrNormalizer = analyzer;
		}

		public Analyzer indexingAnalyzerOrNormalizer() {
			return indexingAnalyzerOrNormalizer;
		}

		public void searchAnalyzerOrNormalizer(Analyzer analyzer) {
			this.searchAnalyzerOrNormalizer = analyzer;
		}

		public void hasTermVectorsConfigured(boolean hasTermVectorsConfigured) {
			this.hasTermVectorsConfigured = hasTermVectorsConfigured;
		}

		@Override
		public LuceneIndexValueFieldType<F, E> build() {
			return new LuceneIndexValueFieldType<>( this );
		}
	}
}
