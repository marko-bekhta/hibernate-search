/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.document.model.dsl.impl;

import java.util.Map;
import java.util.TreeMap;

import org.hibernate.search.backend.elasticsearch.document.impl.ElasticsearchIndexObjectFieldReference;
import org.hibernate.search.backend.elasticsearch.document.model.impl.ElasticsearchIndexCompositeNode;
import org.hibernate.search.backend.elasticsearch.document.model.impl.ElasticsearchIndexField;
import org.hibernate.search.backend.elasticsearch.document.model.impl.ElasticsearchIndexObjectField;
import org.hibernate.search.backend.elasticsearch.logging.impl.MappingLog;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.AbstractTypeMapping;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.DynamicType;
import org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl.PropertyMapping;
import org.hibernate.search.backend.elasticsearch.types.impl.ElasticsearchIndexCompositeNodeType;
import org.hibernate.search.engine.backend.common.spi.FieldPaths;
import org.hibernate.search.engine.backend.document.IndexObjectFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.spi.IndexObjectFieldBuilder;
import org.hibernate.search.engine.backend.types.ObjectStructure;
import org.hibernate.search.engine.common.tree.spi.TreeNodeInclusion;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.util.common.reporting.EventContext;

class ElasticsearchIndexObjectFieldBuilder extends AbstractElasticsearchIndexCompositeNodeBuilder
		implements IndexObjectFieldBuilder, ElasticsearchIndexNodeContributor {

	private final AbstractElasticsearchIndexCompositeNodeBuilder parent;
	private final String absoluteFieldPath;
	private final String relativeFieldName;
	private final TreeNodeInclusion inclusion;

	private boolean multiValued = false;

	private ElasticsearchIndexObjectFieldReference reference;

	ElasticsearchIndexObjectFieldBuilder(AbstractElasticsearchIndexCompositeNodeBuilder parent,
			String relativeFieldName, TreeNodeInclusion inclusion, ObjectStructure structure) {
		super( new ElasticsearchIndexCompositeNodeType.Builder( structure ) );
		this.parent = parent;
		String parentAbsolutePath = parent.getAbsolutePath();
		this.absoluteFieldPath = parentAbsolutePath == null
				? relativeFieldName
				: FieldPaths.compose( parentAbsolutePath, relativeFieldName );
		this.relativeFieldName = relativeFieldName;
		this.inclusion = inclusion;
	}

	@Override
	public EventContext eventContext() {
		return getRootNodeBuilder().getIndexEventContext()
				.append( EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath ) );
	}

	@Override
	public void multiValued() {
		this.multiValued = true;
	}

	@Override
	public IndexObjectFieldReference toReference() {
		if ( reference != null ) {
			throw MappingLog.INSTANCE.cannotCreateReferenceMultipleTimes( eventContext() );
		}
		this.reference = new ElasticsearchIndexObjectFieldReference();
		return reference;
	}

	@Override
	public void contribute(ElasticsearchIndexNodeCollector collector,
			ElasticsearchIndexCompositeNode parentNode,
			Map<String, ElasticsearchIndexField> staticChildrenByNameForParent,
			AbstractTypeMapping parentMapping) {
		if ( reference == null ) {
			throw MappingLog.INSTANCE.incompleteFieldDefinition( eventContext() );
		}

		Map<String, ElasticsearchIndexField> staticChildrenByName = new TreeMap<>();
		ElasticsearchIndexObjectField fieldNode = new ElasticsearchIndexObjectField(
				parentNode, relativeFieldName, typeBuilder.build(), inclusion, multiValued,
				staticChildrenByName );

		staticChildrenByNameForParent.put( relativeFieldName, fieldNode );
		collector.collect( absoluteFieldPath, fieldNode );

		reference.setSchemaNode( fieldNode );

		DynamicType dynamicType = resolveSelfDynamicType( parentMapping.getDynamic() );

		PropertyMapping mapping = fieldNode.type().createMapping( dynamicType );

		if ( TreeNodeInclusion.INCLUDED.equals( fieldNode.inclusion() ) ) {
			parentMapping.addProperty( relativeFieldName, mapping );
		}

		contributeChildren( mapping, fieldNode, collector, staticChildrenByName );
	}

	@Override
	ElasticsearchIndexRootBuilder getRootNodeBuilder() {
		return parent.getRootNodeBuilder();
	}

	@Override
	String getAbsolutePath() {
		return absoluteFieldPath;
	}

}
