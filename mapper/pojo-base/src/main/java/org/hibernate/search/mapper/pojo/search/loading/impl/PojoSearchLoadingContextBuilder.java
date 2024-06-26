/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.mapper.pojo.search.loading.impl;

import java.util.Map;

import org.hibernate.search.engine.search.loading.spi.SearchLoadingContext;
import org.hibernate.search.engine.search.loading.spi.SearchLoadingContextBuilder;
import org.hibernate.search.mapper.pojo.bridge.runtime.spi.BridgeSessionContext;
import org.hibernate.search.mapper.pojo.common.spi.PojoEntityReferenceFactoryDelegate;
import org.hibernate.search.mapper.pojo.loading.spi.PojoSelectionLoadingContextBuilder;

public class PojoSearchLoadingContextBuilder<E, LOS> implements SearchLoadingContextBuilder<E, LOS> {

	private final Map<String, PojoSearchLoadingIndexedTypeContext<? extends E>> targetTypesByEntityName;
	private final PojoEntityReferenceFactoryDelegate entityReferenceFactoryDelegate;
	private final BridgeSessionContext sessionContext;
	private final PojoSelectionLoadingContextBuilder<LOS> delegate;

	public PojoSearchLoadingContextBuilder(
			Map<String, PojoSearchLoadingIndexedTypeContext<? extends E>> targetTypesByEntityName,
			PojoEntityReferenceFactoryDelegate entityReferenceFactoryDelegate,
			BridgeSessionContext sessionContext,
			PojoSelectionLoadingContextBuilder<LOS> delegate) {
		this.targetTypesByEntityName = targetTypesByEntityName;
		this.entityReferenceFactoryDelegate = entityReferenceFactoryDelegate;
		this.sessionContext = sessionContext;
		this.delegate = delegate;
	}

	@Override
	public LOS toAPI() {
		return delegate.toAPI();
	}

	@Override
	public SearchLoadingContext<E> build() {
		return new PojoSearchLoadingContext<>( targetTypesByEntityName, entityReferenceFactoryDelegate, sessionContext,
				delegate.build() );
	}
}
