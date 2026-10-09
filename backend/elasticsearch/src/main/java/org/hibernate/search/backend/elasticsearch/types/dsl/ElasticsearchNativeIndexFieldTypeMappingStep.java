package org.hibernate.search.backend.elasticsearch.types.dsl;

import org.hibernate.search.util.json.JsonObject;

public interface ElasticsearchNativeIndexFieldTypeMappingStep {

	/**
	 * @param jsonObject A {@link JsonObject} representing an Elasticsearch field mapping.
	 * The JSON object must be a syntactically correct Elasticsearch field mapping.
	 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html">the Elasticsearch documentation</a>.
	 * @return A DSL step where the index field type can be defined in more details.
	 */
	ElasticsearchNativeIndexFieldTypeOptionsStep<?> mapping(JsonObject jsonObject);

	/**
	 * @param jsonString A JSON-formatted string representing an Elasticsearch field mapping.
	 * The JSON object must be a syntactically correct Elasticsearch field mapping.
	 * See <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html">the Elasticsearch documentation</a>.
	 * @return A DSL step where the index field type can be defined in more details.
	 */
	ElasticsearchNativeIndexFieldTypeOptionsStep<?> mapping(String jsonString);

	/**
	* @deprecated Use Search JSON nodes. Conversion walks and allocates a new tree.
	* This bridge and GsonJsonConversion will be removed together in a future breaking release.
	* A literal null argument is ambiguous; cast it to the intended input type.
	*/
	@Deprecated(forRemoval = true)
	@SuppressWarnings("removal")
	default ElasticsearchNativeIndexFieldTypeOptionsStep<?> mapping(com.google.gson.JsonObject jsonObject) {
		return mapping( org.hibernate.search.backend.elasticsearch.gson.GsonJsonConversion.fromGson( jsonObject ) );
	}
}
