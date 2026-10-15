package org.hibernate.search.backend.elasticsearch.logging.impl;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonLogHelper;

import tools.jackson.databind.node.ObjectNode;

/**
 * Used with JBoss Logging's {@link org.jboss.logging.annotations.FormatWith}
 * to display {@link ObjectNode}s in log messages.
 */
public final class ElasticsearchJsonObjectFormatter {

	private final ObjectNode object;

	public ElasticsearchJsonObjectFormatter(ObjectNode object) {
		this.object = object;
	}

	@Override
	public String toString() {
		return JsonLogHelper.get().toString( object );
	}
}
