package org.hibernate.search.backend.elasticsearch.search.predicate.dsl;

import org.hibernate.search.engine.search.predicate.dsl.ExtendedSearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;

import org.hibernate.search.util.json.JsonObject;

/**
 * A factory for search predicates with some Elasticsearch-specific methods.
 *
 * @param <SR> Scope root type.
 */
public interface ElasticsearchSearchPredicateFactory<SR>
		extends ExtendedSearchPredicateFactory<SR, ElasticsearchSearchPredicateFactory<SR>> {

	/**
	 * Create a predicate from JSON.
	 *
	 * @param jsonString A JSON-formatted string representing an Elasticsearch query.
	 * The JSON object must be a syntactically correct Elasticsearch query.
	 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html">the Elasticsearch documentation</a>.
	 * @return The final step of the predicate DSL.
	 */
	PredicateFinalStep fromJson(String jsonString);

	/**
	 * Create a predicate from JSON.
	 *
	 * @param jsonObject A {@link JsonObject} representing an Elasticsearch query.
	 * The JSON object must be a syntactically correct Elasticsearch query.
	 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html">the Elasticsearch documentation</a>.
	 * @return The final step of the predicate DSL.
	 */
	PredicateFinalStep fromJson(JsonObject jsonObject);

	/**
	* @deprecated Use Search JSON nodes. Conversion walks and allocates a new tree.
	* This bridge and GsonJsonConversion will be removed together in a future breaking release.
	* A literal null argument is ambiguous; cast it to the intended input type.
	*/
	@Deprecated(forRemoval = true)
	@SuppressWarnings("removal")
	default PredicateFinalStep fromJson(com.google.gson.JsonObject jsonObject) {
		return fromJson( org.hibernate.search.backend.elasticsearch.gson.GsonJsonConversion.fromGson( jsonObject ) );
	}
}
