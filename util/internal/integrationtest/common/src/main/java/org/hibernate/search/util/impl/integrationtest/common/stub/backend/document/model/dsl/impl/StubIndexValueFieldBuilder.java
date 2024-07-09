/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.model.dsl.impl;

import java.util.function.BiConsumer;

import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaFieldOptionsStep;
import org.hibernate.search.engine.common.tree.spi.TreeNodeInclusion;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.impl.StubIndexFieldReference;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.model.StubIndexSchemaDataNode;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.model.impl.StubIndexCompositeNode;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.model.impl.StubIndexField;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.document.model.impl.StubIndexValueField;
import org.hibernate.search.util.impl.integrationtest.common.stub.backend.types.impl.StubIndexValueFieldType;

class StubIndexValueFieldBuilder<F, E>
		implements IndexSchemaFieldOptionsStep<StubIndexValueFieldBuilder<F, E>, IndexFieldReference<F>>,
		StubIndexFieldBuilder {

	private final StubIndexSchemaDataNode.Builder schemaDataNodeBuilder;
	private final TreeNodeInclusion inclusion;
	private final StubIndexValueFieldType<F, E> type;

	private boolean multiValued;

	private IndexFieldReference<F> reference;

	StubIndexValueFieldBuilder(StubIndexSchemaDataNode.Builder schemaDataNodeBuilder, TreeNodeInclusion inclusion,
			StubIndexValueFieldType<F, E> type) {
		this.schemaDataNodeBuilder = schemaDataNodeBuilder;
		this.inclusion = inclusion;
		this.type = type;
	}

	@Override
	public StubIndexValueFieldBuilder<F, E> multiValued() {
		this.multiValued = true;
		schemaDataNodeBuilder.multiValued( true );
		return this;
	}

	@Override
	public IndexFieldReference<F> toReference() {
		if ( reference == null ) {
			reference = new StubIndexFieldReference<>(
					schemaDataNodeBuilder.getAbsolutePath(), schemaDataNodeBuilder.getRelativeName(), inclusion
			);
		}
		return reference;
	}

	@Override
	public StubIndexField build(StubIndexCompositeNode parent, BiConsumer<String, StubIndexField> fieldCollector) {
		return new StubIndexValueField<>( parent, schemaDataNodeBuilder.getRelativeName(), type, inclusion, multiValued,
				schemaDataNodeBuilder.build() );
	}
}
