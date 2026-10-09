package org.hibernate.search.backend.elasticsearch.logging.impl;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonLogHelper;

import org.hibernate.search.util.json.JsonObject;

/**
 * Used with JBoss Logging's {@link org.jboss.logging.annotations.FormatWith}
 * to display {@link JsonObject}s in log messages.
 *
 */
public final class ElasticsearchJsonObjectFormatter {

	private final JsonObject object;

	public ElasticsearchJsonObjectFormatter(JsonObject object) {
		this.object = object;
	}

	@Override
	public String toString() {
		return JsonLogHelper.get().toString( object );
	}
}
