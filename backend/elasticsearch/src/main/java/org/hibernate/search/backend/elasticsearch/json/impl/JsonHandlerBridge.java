package org.hibernate.search.backend.elasticsearch.json.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.hibernate.search.backend.elasticsearch.json.jackson.JacksonJsonHandlers;
import org.hibernate.search.backend.elasticsearch.logging.impl.QueryLog;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.json.JsonHandler;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.JsonTreeValidation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Shared explicit user-library boundary. Ordinary DSL/transport processing does not use handlers. */
public final class JsonHandlerBridge {
	private static final JsonParsing PARSING = new JsonParsing();

	private JsonHandlerBridge() {
	}

	public static boolean isTrusted(JsonHandler<?> handler) {
		return handler == JacksonJsonHandlers.object() || handler == JacksonJsonHandlers.value();
	}

	public static <T> JsonNode importValue(JsonHandler<T> handler, T value, String operation) {
		try {
			Class<T> javaType = Objects.requireNonNull( Objects.requireNonNull( handler ).javaType() );
			javaType.cast( Objects.requireNonNull( value ) );
			if ( isTrusted( handler ) ) {
				JsonNode node = (JsonNode) value;
				JsonTreeValidation.validateExternal( node );
				return node;
			}
			return PARSING.parse( Objects.requireNonNull( handler.toJson( value ) ) );
		}
		catch (RuntimeException e) {
			throw QueryLog.INSTANCE.jsonHandlerOperationFailed( operation, e );
		}
	}

	public static <T> ObjectNode importObject(JsonHandler<T> handler, T value, String operation) {
		return requireObject( importValue( handler, value, operation ), operation );
	}

	public static <T> T exportValue(JsonHandler<T> handler, JsonNode value, String operation) {
		try {
			Class<T> javaType = Objects.requireNonNull( Objects.requireNonNull( handler ).javaType() );
			JsonTreeValidation.validateExternal( Objects.requireNonNull( value ) );
			if ( isTrusted( handler ) ) {
				return javaType.cast( value );
			}
			return javaType.cast( Objects.requireNonNull( handler.fromJson( toJson( value ) ) ) );
		}
		catch (RuntimeException e) {
			throw QueryLog.INSTANCE.jsonHandlerOperationFailed( operation, e );
		}
	}

	/** Absent optional object results return Java null without invoking the handler. */
	public static <T> T exportObject(JsonHandler<T> handler, JsonNode value, String operation) {
		try {
			Objects.requireNonNull( Objects.requireNonNull( handler ).javaType() );
			return value == null ? null : exportValue( handler, requireObject( value, operation ), operation );
		}
		catch (SearchException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw QueryLog.INSTANCE.jsonHandlerOperationFailed( operation, e );
		}
	}

	public static ObjectNode parseObject(String json, String operation) {
		try {
			return requireObject( PARSING.parse( Objects.requireNonNull( json ) ), operation );
		}
		catch (SearchException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw QueryLog.INSTANCE.jsonHandlerOperationFailed( operation, e );
		}
	}

	public static JsonNode parseValue(String json) {
		return PARSING.parse( Objects.requireNonNull( json ) );
	}

	public static ObjectNode requireObject(JsonNode value, String operation) {
		if ( !( value instanceof ObjectNode object ) ) {
			throw QueryLog.INSTANCE.jsonHandlerOperationFailed( operation,
					new IllegalArgumentException( "Expected a JSON object root" ) );
		}
		return object;
	}

	/** Explicit text boundary, preserving all object members and nested nulls. */
	public static String toJson(JsonNode value) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			JsonBodies.of( value ).writeTo( output );
		}
		catch (IOException e) {
			throw new UncheckedIOException( e );
		}
		return output.toString( StandardCharsets.UTF_8 );
	}
}
