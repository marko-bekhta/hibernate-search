package org.hibernate.search.backend.elasticsearch.lowlevel.syntax.search.impl;

import java.util.List;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.JsonPrimitive;

public interface ElasticsearchSearchSyntax {

	String getTermAggregationOrderByTermToken();

	void requestDocValues(JsonObject requestBody, JsonPrimitive fieldName);

	void requestNestedSort(List<String> nestedPathHierarchy, JsonObject innerObject, JsonObject filterOrNull);

	void requestGeoDistanceSortIgnoreUnmapped(JsonObject innerObject);

	JsonElement encodeLongForAggregation(Long value);

}
