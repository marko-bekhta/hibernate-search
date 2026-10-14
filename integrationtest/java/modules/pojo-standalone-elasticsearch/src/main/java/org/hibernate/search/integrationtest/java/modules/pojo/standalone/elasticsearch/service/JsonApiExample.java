package org.hibernate.search.integrationtest.java.modules.pojo.standalone.elasticsearch.service;

import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.json.JsonHandler;
import org.hibernate.search.backend.elasticsearch.json.jackson.JacksonJsonHandlers;
import org.hibernate.search.util.json.spi.JsonParsing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Compile with only the backend requires: native Jackson and deprecated Gson APIs remain readable transitively. */
public final class JsonApiExample {
	private JsonApiExample() {
	}

	public static long requestValue() {
		ObjectNode body = (ObjectNode) new JsonParsing().parse( "{\"value\":42}" );
		ElasticsearchRequest request = ElasticsearchRequest.post().bodyJson( body ).build();
		return request.bodyPartsJson().get( 0 ).get( "value" ).longValue();
	}

	/** A custom handler with the same native type still executes its conversion callbacks. */
	public static ObjectNode customHandlerResult() {
		JsonHandler<ObjectNode> custom = new JsonHandler<>() {
			@Override
			public Class<ObjectNode> javaType() {
				return ObjectNode.class;
			}

			@Override
			public String toJson(ObjectNode value) {
				return JacksonJsonHandlers.object().toJson( value );
			}

			@Override
			public ObjectNode fromJson(String json) {
				ObjectNode result = (ObjectNode) new JsonParsing().parse( json );
				result.put( "custom", true );
				return result;
			}
		};
		ObjectNode body = (ObjectNode) new JsonParsing().parse( "{\"value\":9007199254740993,\"nil\":null}" );
		return ElasticsearchRequest.post().bodyJson( body ).build().bodyParts( custom ).get( 0 );
	}

	public static JsonNode nativeScalar() {
		return JacksonJsonHandlers.value().fromJson( "-0.0" );
	}

	@SuppressWarnings("deprecation")
	public static long deprecatedGsonValue() {
		com.google.gson.JsonObject body = new com.google.gson.JsonObject();
		body.addProperty( "value", 42 );
		return ElasticsearchRequest.post().body( body ).build().bodyParts().get( 0 ).get( "value" ).getAsLong();
	}
}
