/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.elasticsearch.search.query.impl;

import static org.hibernate.search.backend.elasticsearch.search.projection.impl.ElasticsearchSearchProjection.transformUnsafe;

import java.util.Collections;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.search.projection.impl.ElasticsearchSearchProjection;
import org.hibernate.search.backend.elasticsearch.search.projection.impl.SearchProjectionTransformContext;
import org.hibernate.search.backend.elasticsearch.search.query.ElasticsearchSearchResult;
import org.hibernate.search.engine.mapper.session.context.spi.SessionContextImplementor;
import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.loading.spi.ProjectionHitMapper;

/**
 * A search result from the backend that offers a method to load data from the mapper.
 * <p>
 * Allows to run loading in the user thread, and not in the backend HTTP request threads.
 * <p>
 * <strong>WARNING:</strong> loading should only be triggered once.
 * <p>
 * <strong>WARNING:</strong> this class is not thread-safe.
 *
 * @param <H> The type of hits in the search result.
 */
public class ElasticsearchLoadableSearchResult<H> {
	private final ProjectionHitMapper<?, ?> projectionHitMapper;
	private final ElasticsearchSearchProjection<?, H> rootProjection;

	private final long hitCount;
	private List<Object> extractedData;

	ElasticsearchLoadableSearchResult(ProjectionHitMapper<?, ?> projectionHitMapper,
			ElasticsearchSearchProjection<?, H> rootProjection,
			long hitCount, List<Object> extractedData) {
		this.projectionHitMapper = projectionHitMapper;
		this.rootProjection = rootProjection;
		this.hitCount = hitCount;
		this.extractedData = extractedData;
	}

	ElasticsearchSearchResult<H> loadBlocking(SessionContextImplementor sessionContext) {
		SearchProjectionTransformContext transformContext = new SearchProjectionTransformContext( sessionContext );

		LoadingResult<?> loadingResult = projectionHitMapper.loadBlocking();

		for ( int i = 0; i < extractedData.size(); i++ ) {
			H transformed = transformUnsafe( rootProjection, loadingResult, extractedData.get( i ), transformContext );
			extractedData.set( i, transformed );
		}

		// The cast is safe, since all elements extend H and we make the list unmodifiable
		@SuppressWarnings("unchecked")
		List<H> loadedHits = Collections.unmodifiableList( (List<? extends H>) extractedData );

		// Make sure that if someone uses this object incorrectly, it will always fail, and will fail early.
		extractedData = null;

		return new ElasticsearchSearchResultImpl<>( hitCount, loadedHits );
	}
}
