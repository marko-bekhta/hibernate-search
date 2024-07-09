/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.impl.integrationtest.common.stub.backend.search.common.impl;

import java.util.List;

import org.hibernate.search.engine.search.common.spi.AbstractMultiIndexSearchIndexValueFieldContext;
import org.hibernate.search.engine.search.common.spi.SearchIndexSchemaElementContextHelper;

public class StubMultiIndexSearchIndexValueFieldContext<F, E>
		extends AbstractMultiIndexSearchIndexValueFieldContext<
				StubSearchIndexValueFieldContext<F, E>,
				StubSearchIndexScope,
				StubSearchIndexValueFieldTypeContext<F, E>,
				F,
				E>
		implements StubSearchIndexValueFieldContext<F, E>, StubSearchIndexValueFieldTypeContext<F, E> {

	public StubMultiIndexSearchIndexValueFieldContext(StubSearchIndexScope scope, String absolutePath,
			List<? extends StubSearchIndexValueFieldContext<F, E>> fieldForEachIndex) {
		super( scope, absolutePath, fieldForEachIndex );
	}

	@Override
	protected StubSearchIndexValueFieldContext<F, E> self() {
		return this;
	}

	@Override
	protected StubSearchIndexValueFieldTypeContext<F, E> selfAsNodeType() {
		return this;
	}

	@Override
	protected StubSearchIndexValueFieldTypeContext<F, E> typeOf(StubSearchIndexValueFieldContext<F, E> indexElement) {
		return indexElement.type();
	}

	@Override
	public StubSearchIndexCompositeNodeContext toComposite() {
		return SearchIndexSchemaElementContextHelper.throwingToComposite( this );
	}
}
