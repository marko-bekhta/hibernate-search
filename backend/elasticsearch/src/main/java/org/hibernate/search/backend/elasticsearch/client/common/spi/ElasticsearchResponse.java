package org.hibernate.search.backend.elasticsearch.client.common.spi;

import org.hibernate.search.backend.elasticsearch.gson.GsonJsonHandlers;
import org.hibernate.search.backend.elasticsearch.json.JsonHandler;
import org.hibernate.search.backend.elasticsearch.json.impl.JsonHandlerBridge;
import org.hibernate.search.util.json.spi.JsonTreeValidation;

import com.google.gson.JsonObject;

import tools.jackson.databind.node.ObjectNode;

public final class ElasticsearchResponse {
	private final String hostAndPort;
	private final int statusCode;
	private final String statusMessage;
	private final ObjectNode body;

	/** @deprecated Use {@link #withJsonBody(String, int, String, ObjectNode)}. */
	@Deprecated
	public ElasticsearchResponse(String hostAndPort, int statusCode, String statusMessage, JsonObject body) {
		this( body == null ? null : JsonHandlerBridge.importObject( GsonJsonHandlers.object(), body, "response body import" ),
				hostAndPort, statusCode, statusMessage );
	}

	/** The optional body is validated and retained as a stable borrowed tree. */
	public static ElasticsearchResponse withJsonBody(String hostAndPort, int statusCode,
			String statusMessage, ObjectNode body) {
		return new ElasticsearchResponse( body, hostAndPort, statusCode, statusMessage );
	}

	private ElasticsearchResponse(ObjectNode body, String hostAndPort, int statusCode, String statusMessage) {
		if ( body != null ) {
			JsonTreeValidation.validateExternal( body );
		}
		this.hostAndPort = hostAndPort;
		this.statusCode = statusCode;
		this.statusMessage = statusMessage;
		this.body = body;
	}

	public String hostAndPort() {
		return hostAndPort;
	}

	public int statusCode() {
		return statusCode;
	}

	public String statusMessage() {
		return statusMessage;
	}

	public ObjectNode bodyJson() {
		return body;
	}

	public <T> T body(JsonHandler<T> handler) {
		return JsonHandlerBridge.exportObject( handler, body, "response body export" );
	}

	/** @deprecated Use {@link #bodyJson()} or {@link #body(JsonHandler)}. */
	@Deprecated
	public JsonObject body() {
		return body( GsonJsonHandlers.object() );
	}
}
