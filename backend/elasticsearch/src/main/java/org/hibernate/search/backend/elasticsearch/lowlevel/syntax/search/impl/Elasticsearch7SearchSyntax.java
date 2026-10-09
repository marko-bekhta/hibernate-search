package org.hibernate.search.backend.elasticsearch.lowlevel.syntax.search.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;

/**
 * The search syntax for ES7.0 to ES8.0.
 */
public class Elasticsearch7SearchSyntax extends Elasticsearch81SearchSyntax {

	@Override
	public JsonElement encodeLongForAggregation(Long value) {
		// Workaround for https://github.com/elastic/elasticsearch/issues/81529
		return value == null ? JsonNull.INSTANCE : new JsonPrimitive( value.toString() );
	}
}
