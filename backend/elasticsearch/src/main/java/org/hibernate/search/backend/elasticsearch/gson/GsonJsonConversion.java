package org.hibernate.search.backend.elasticsearch.gson;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.JsonPrimitive;

/**
 * Compatibility conversion for applications migrating Gson inputs and outputs.
 * Every conversion walks the tree and allocates independent mutable containers;
 * numeric lexemes, member order and JSON nulls are preserved without rendering/reparsing.
 * Java null remains Java null. Ordinary backend processing does not use these methods.
 * @deprecated Migrate to Search JSON nodes. This utility and all Gson input bridges will be
 * removed together in a future breaking release.
 */
@Deprecated(forRemoval = true)
public final class GsonJsonConversion {
	private GsonJsonConversion() {
	}

	public static JsonElement fromGson(com.google.gson.JsonElement value) {
		if ( value == null ) {
			return null;
		}
		if ( value.isJsonNull() ) {
			return JsonNull.INSTANCE;
		}
		if ( value.isJsonObject() ) {
			return fromGson( value.getAsJsonObject() );
		}
		if ( value.isJsonArray() ) {
			return fromGson( value.getAsJsonArray() );
		}
		var p = value.getAsJsonPrimitive();
		return p.isNumber() ? JsonPrimitive.number( p.getAsString() )
				: p.isBoolean() ? new JsonPrimitive( p.getAsBoolean() )
				: new JsonPrimitive( p.getAsString() );
	}

	public static JsonObject fromGson(com.google.gson.JsonObject value) {
		if ( value == null ) {
			return null;
		}
		JsonObject result = new JsonObject();
		value.entrySet().forEach( e -> result.add( e.getKey(), fromGson( e.getValue() ) ) );
		return result;
	}

	public static JsonArray fromGson(com.google.gson.JsonArray value) {
		if ( value == null ) {
			return null;
		}
		JsonArray result = new JsonArray();
		value.forEach( e -> result.add( fromGson( e ) ) );
		return result;
	}

	public static com.google.gson.JsonElement toGson(JsonElement value) {
		if ( value == null ) {
			return null;
		}
		if ( value.isJsonNull() ) {
			return com.google.gson.JsonNull.INSTANCE;
		}
		if ( value.isJsonObject() ) {
			return toGson( value.getAsJsonObject() );
		}
		if ( value.isJsonArray() ) {
			return toGson( value.getAsJsonArray() );
		}
		var p = value.getAsJsonPrimitive();
		return p.isNumber() ? new com.google.gson.JsonPrimitive( new LexemeNumber( p.getAsString() ) )
				: p.isBoolean() ? new com.google.gson.JsonPrimitive( p.getAsBoolean() )
				: new com.google.gson.JsonPrimitive( p.getAsString() );
	}

	public static com.google.gson.JsonObject toGson(JsonObject value) {
		if ( value == null ) {
			return null;
		}
		var result = new com.google.gson.JsonObject();
		value.entrySet().forEach( e -> result.add( e.getKey(), toGson( e.getValue() ) ) );
		return result;
	}

	public static com.google.gson.JsonArray toGson(JsonArray value) {
		if ( value == null ) {
			return null;
		}
		var result = new com.google.gson.JsonArray();
		value.forEach( e -> result.add( toGson( e ) ) );
		return result;
	}

	private static final class LexemeNumber extends Number {
		private final String lexeme;

		private LexemeNumber(String lexeme) {
			this.lexeme = lexeme;
		}

		public int intValue() {
			return new java.math.BigDecimal( lexeme ).intValue();
		}

		public long longValue() {
			return new java.math.BigDecimal( lexeme ).longValue();
		}

		public float floatValue() {
			return Float.parseFloat( lexeme );
		}

		public double doubleValue() {
			return Double.parseDouble( lexeme );
		}

		public String toString() {
			return lexeme;
		}
	}
}
