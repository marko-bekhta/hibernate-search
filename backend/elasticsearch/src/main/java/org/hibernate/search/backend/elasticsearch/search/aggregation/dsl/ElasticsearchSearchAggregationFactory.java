package org.hibernate.search.backend.elasticsearch.search.aggregation.dsl;

import org.hibernate.search.backend.elasticsearch.search.predicate.dsl.ElasticsearchSearchPredicateFactory;
import org.hibernate.search.engine.search.aggregation.dsl.AggregationFinalStep;
import org.hibernate.search.engine.search.aggregation.dsl.ExtendedSearchAggregationFactory;

import org.hibernate.search.util.json.JsonObject;

public interface ElasticsearchSearchAggregationFactory<SR>
		extends
		ExtendedSearchAggregationFactory<SR,
				ElasticsearchSearchAggregationFactory<SR>,
				ElasticsearchSearchPredicateFactory<SR>> {

	/**
	 * Create an aggregation from JSON.
	 * <p>
	 * The created aggregation will return the result as a {@link JsonObject}.
	 *
	 * @param jsonObject A {@link JsonObject} representing an Elasticsearch aggregation.
	 * The JSON object must be a syntactically correct Elasticsearch aggregation.
	 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html">the Elasticsearch documentation</a>.
	 * @return The final step of the aggregation DSL.
	 */
	AggregationFinalStep<JsonObject> fromJson(JsonObject jsonObject);

	/**
	 * Create an aggregation from JSON.
	 * <p>
	 * The created aggregation will return the result as a {@link JsonObject}.
	 *
	 * @param jsonString A JSON-formatted string representing an Elasticsearch aggregation.
	 * The JSON object must be a syntactically correct Elasticsearch aggregation.
	 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html">the Elasticsearch documentation</a>.
	 * @return The final step of the aggregation DSL.
	 */
	AggregationFinalStep<JsonObject> fromJson(String jsonString);

	/**
	* @deprecated Use Search JSON nodes. Conversion walks and allocates a new tree.
	* This bridge and GsonJsonConversion will be removed together in a future breaking release.
	* A literal null argument is ambiguous; cast it to the intended input type.
	*/
	@Deprecated(forRemoval = true)
	@SuppressWarnings("removal")
	default AggregationFinalStep<JsonObject> fromJson(com.google.gson.JsonObject jsonObject) {
		return fromJson( org.hibernate.search.backend.elasticsearch.gson.GsonJsonConversion.fromGson( jsonObject ) );
	}
}
