package org.hibernate.search.util.json.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.JsonPrimitive;

/** Composable codecs for explicitly selected tree, scalar, list and string-keyed map types. */
public final class JsonCodecs {
	private JsonCodecs() {
	}

	public static final JsonCodec<JsonElement> ELEMENT = new JsonCodec<>() {
		public JsonElement decode(JsonElement value) {
			return value;
		}

		public JsonElement encode(JsonElement value, boolean serializeNulls) {
			return copy( value, serializeNulls );
		}
	};
	public static final JsonCodec<String> STRING = scalar( JsonElement::getAsString, JsonPrimitive::new );
	public static final JsonCodec<Boolean> BOOLEAN = scalar( JsonCodecs::readBoolean, JsonPrimitive::new );
	public static final JsonCodec<Integer> INTEGER = scalar( JsonElement::getAsInt, JsonPrimitive::new );
	public static final JsonCodec<Long> LONG = scalar( JsonElement::getAsLong, JsonPrimitive::new );
	public static final JsonCodec<Double> DOUBLE = scalar( JsonElement::getAsDouble, JsonPrimitive::new );
	public static final JsonCodec<Float> FLOAT = scalar( JsonElement::getAsFloat, JsonPrimitive::new );

	private static Boolean readBoolean(JsonElement value) {
		if ( !value.isJsonPrimitive() || value.getAsJsonPrimitive().isNumber() ) {
			throw new IllegalArgumentException( "Expected JSON boolean or boolean string" );
		}
		return value.getAsBoolean();
	}

	public static boolean isNull(JsonElement value) {
		return value == null || value.isJsonNull();
	}

	private static <T> JsonCodec<T> scalar(Function<JsonElement, T> read, Function<T, JsonElement> write) {
		return new JsonCodec<>() {
			public T decode(JsonElement value) {
				return isNull( value ) ? null : read.apply( value );
			}

			public JsonElement encode(T value, boolean serializeNulls) {
				return value == null ? JsonNull.INSTANCE : write.apply( value );
			}
		};
	}

	public static void put(JsonObject target, String name, JsonElement value, boolean serializeNulls) {
		if ( serializeNulls || !isNull( value ) ) {
			target.add( name, value );
		}
	}

	/** Structural copy applies member-null omission recursively; null array slots are retained. */
	public static JsonElement copy(JsonElement value, boolean serializeNulls) {
		if ( isNull( value ) ) {
			return JsonNull.INSTANCE;
		}
		if ( value.isJsonObject() ) {
			JsonObject result = new JsonObject();
			value.getAsJsonObject().entrySet()
					.forEach( e -> put( result, e.getKey(), copy( e.getValue(), serializeNulls ), serializeNulls ) );
			return result;
		}
		if ( value.isJsonArray() ) {
			JsonArray result = new JsonArray();
			value.getAsJsonArray().forEach( e -> result.add( copy( e, serializeNulls ) ) );
			return result;
		}
		return value;
	}

	public static <T> JsonCodec<List<T>> list(JsonCodec<T> element) {
		return new JsonCodec<>() {
			public List<T> decode(JsonElement value) {
				if ( isNull( value ) ) {
					return null;
				}
				List<T> result = new ArrayList<>();
				value.getAsJsonArray().forEach( e -> result.add( element.decode( e ) ) );
				return result;
			}

			public JsonElement encode(List<T> value, boolean serializeNulls) {
				if ( value == null ) {
					return JsonNull.INSTANCE;
				}
				JsonArray result = new JsonArray();
				value.forEach( e -> result.add( element.encode( e, serializeNulls ) ) );
				return result;
			}
		};
	}

	public static <T> JsonCodec<Map<String, T>> map(JsonCodec<T> element) {
		return new JsonCodec<>() {
			public Map<String, T> decode(JsonElement value) {
				if ( isNull( value ) ) {
					return null;
				}
				Map<String, T> result = new LinkedHashMap<>();
				value.getAsJsonObject().entrySet().forEach( e -> result.put( e.getKey(), element.decode( e.getValue() ) ) );
				return result;
			}

			public JsonElement encode(Map<String, T> value, boolean serializeNulls) {
				if ( value == null ) {
					return JsonNull.INSTANCE;
				}
				JsonObject result = new JsonObject();
				value.forEach( (k, v) -> put( result, k, element.encode( v, serializeNulls ), serializeNulls ) );
				return result;
			}
		};
	}
}
