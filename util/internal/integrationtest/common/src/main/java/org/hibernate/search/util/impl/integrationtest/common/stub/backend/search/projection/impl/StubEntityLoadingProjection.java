/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.impl.integrationtest.common.stub.backend.search.projection.impl;

import java.util.Iterator;

import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.loading.spi.ProjectionHitMapper;

public class StubEntityLoadingProjection<T> extends StubSearchProjection<T> {

	@SuppressWarnings("rawtypes")
	private static final StubSearchProjection INSTANCE = new StubEntityLoadingProjection();

	@SuppressWarnings("unchecked")
	public static <T> StubEntityLoadingProjection<T> get() {
		return (StubEntityLoadingProjection<T>) INSTANCE;
	}

	private StubEntityLoadingProjection() {
	}

	@Override
	public Object extract(ProjectionHitMapper<?> projectionHitMapper, Iterator<?> projectionFromIndex,
			StubSearchProjectionContext context) {
		return projectionHitMapper.planLoading( (DocumentReference) projectionFromIndex.next() );
	}

	@SuppressWarnings("unchecked")
	@Override
	public T transform(LoadingResult<?> loadingResult, Object extractedData,
			StubSearchProjectionContext context) {
		T loaded = (T) loadingResult.get( extractedData );
		if ( loaded == null ) {
			context.reportFailedLoad();
		}
		return loaded;
	}

	@Override
	protected String typeName() {
		return "entity";
	}

	@Override
	protected void toNode(StubProjectionNode.Builder self) {
		// Nothing to do
	}
}
