package org.hibernate.search.backend.elasticsearch.search.projection.impl;

import org.hibernate.search.util.json.JsonObject;

public interface ProjectionExtractionHelper<T> {

	void request(JsonObject requestBody, ProjectionRequestContext context);

	T extract(JsonObject hit, ProjectionExtractContext context);

}
