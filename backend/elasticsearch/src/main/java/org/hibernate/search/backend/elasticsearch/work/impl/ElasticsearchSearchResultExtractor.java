package org.hibernate.search.backend.elasticsearch.work.impl;

import org.hibernate.search.engine.common.timing.Deadline;

import org.hibernate.search.util.json.JsonObject;

public interface ElasticsearchSearchResultExtractor<R> {

	R extract(JsonObject responseBody, Deadline deadline);

}
