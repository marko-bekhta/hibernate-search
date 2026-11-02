package org.hibernate.search.backend.elasticsearch.work.impl;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonProvider;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchClient;

public interface ElasticsearchWorkExecutionContext {

	ElasticsearchClient getClient();

	JsonProvider getJsonProvider();

}
