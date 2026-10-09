package org.hibernate.search.integrationtest.java.modules.pojo.standalone.elasticsearch.service;

import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonParsing;

/** Compile with only the existing backend requires: its Search JSON dependency must be transitive. */
public final class JsonApiExample {
	private JsonApiExample() {
	}

	public static long requestValue() {
		JsonObject body = new JsonParsing().parse( "{\"value\":42}" ).getAsJsonObject();
		ElasticsearchRequest request = ElasticsearchRequest.post().body( body ).build();
		return JsonAccessor.root().property( "value" ).asLong().get( request.bodyParts().get( 0 ) ).orElseThrow();
	}
}
