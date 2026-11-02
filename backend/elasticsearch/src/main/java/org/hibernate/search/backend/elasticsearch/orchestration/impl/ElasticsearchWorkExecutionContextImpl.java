package org.hibernate.search.backend.elasticsearch.orchestration.impl;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonProvider;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchClient;
import org.hibernate.search.backend.elasticsearch.work.impl.ElasticsearchWorkExecutionContext;

/**
 * The execution context for works.
 * <p>
 * This context is immutable and thread-safe.
 */
class ElasticsearchWorkExecutionContextImpl implements ElasticsearchWorkExecutionContext {

	private final ElasticsearchClient client;
	private final JsonProvider jsonProvider;

	public ElasticsearchWorkExecutionContextImpl(ElasticsearchClient client, JsonProvider jsonProvider) {
		this.client = client;
		this.jsonProvider = jsonProvider;
	}

	@Override
	public ElasticsearchClient getClient() {
		return client;
	}

	@Override
	public JsonProvider getJsonProvider() {
		return jsonProvider;
	}

}
