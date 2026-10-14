package org.hibernate.search.util.json.spi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/** Composable codecs for explicitly selected tree, scalar, list and string-keyed map types. */
public final class JsonCodecs {
	private JsonCodecs() {
	}

	public static final JsonCodec<JsonNode> ELEMENT = new JsonCodec<>() {
		public JsonNode decode(JsonNode value) {
			return value;
		}

		public JsonNode encode(JsonNode value, boolean serializeNulls) {
			return copy( value, serializeNulls );
		}
	};
	public static final JsonCodec<String> STRING = scalar( JsonCodecs::readString, StringNode::valueOf );
	public static final JsonCodec<Boolean> BOOLEAN = scalar( JsonCodecs::readBoolean, BooleanNode::valueOf, false );
	public static final JsonCodec<Integer> INTEGER = scalar( v -> v.isNumber()
			? narrowingNumber( v ).intValue()
			: Integer.parseInt( readString( v ) ), JsonNumbers::fromNumber );
	public static final JsonCodec<Long> LONG = scalar( v -> v.isNumber()
			? narrowingNumber( v ).longValue()
			: Long.parseLong( readString( v ) ), JsonNumbers::fromNumber );
	public static final JsonCodec<Double> DOUBLE = scalar( v -> v.isNumber()
			? v.numberValue().doubleValue()
			: Double.parseDouble( readString( v ) ), JsonNumbers::fromNumber );
	public static final JsonCodec<Float> FLOAT = scalar( v -> v.isNumber()
			? v.numberValue().floatValue()
			: Float.parseFloat( readString( v ) ), JsonNumbers::fromNumber );

	private static Number narrowingNumber(JsonNode value) {
		Number number = value.numberValue();
		// Powers of ten with exponent >=64 have zero low64bits; avoid enormous decimal expansion.
		return number instanceof BigDecimal decimal && decimal.scale() <= -64 ? 0 : number;
	}

	private static String readString(JsonNode value) {
		if ( value.isString() ) {
			return value.asString();
		}
		if ( value.isNumber() ) {
			return value.numberValue().toString();
		}
		if ( value.isBoolean() ) {
			return Boolean.toString( value.asBoolean() );
		}
		throw new IllegalArgumentException( "Expected JSON string, number or boolean" );
	}

	private static Boolean readBoolean(JsonNode value) {
		if ( !value.isBoolean() && !value.isString() ) {
			throw new IllegalArgumentException( "Expected JSON boolean or boolean string" );
		}
		return value.isBoolean() ? value.asBoolean() : Boolean.parseBoolean( value.asString() );
	}

	public static boolean isNull(JsonNode value) {
		return value == null || value.isNull();
	}

	private static JsonNode scalarValue(JsonNode value) {
		while ( value.isArray() ) {
			if ( value.size() != 1 ) {
				throw new IllegalArgumentException( "Expected a singleton JSON array" );
			}
			value = value.get( 0 );
		}
		return value;
	}

	private static <T> JsonCodec<T> scalar(Function<JsonNode, T> read, Function<T, JsonNode> write) {
		return scalar( read, write, true );
	}

	private static <T> JsonCodec<T> scalar(Function<JsonNode, T> read, Function<T, JsonNode> write,
			boolean unwrapSingletonArrays) {
		return new JsonCodec<>() {
			public T decode(JsonNode value) {
				return isNull( value ) ? null : read.apply( unwrapSingletonArrays ? scalarValue( value ) : value );
			}

			public JsonNode encode(T value, boolean serializeNulls) {
				return value == null ? NullNode.instance : write.apply( value );
			}
		};
	}

	public static void put(ObjectNode target, String name, JsonNode value, boolean serializeNulls) {
		if ( serializeNulls || !isNull( value ) ) {
			target.set( name, value );
		}
	}

	/** Structural copy applies member-null omission recursively; null array slots are retained. */
	public static JsonNode copy(JsonNode value, boolean serializeNulls) {
		if ( isNull( value ) ) {
			return NullNode.instance;
		}
		if ( value instanceof ObjectNode object ) {
			ObjectNode result = JsonNodeFactory.instance.objectNode();
			object.properties()
					.forEach( e -> put( result, e.getKey(), copy( e.getValue(), serializeNulls ), serializeNulls ) );
			return result;
		}
		if ( value instanceof ArrayNode array ) {
			ArrayNode result = JsonNodeFactory.instance.arrayNode();
			array.forEach( e -> result.add( copy( e, serializeNulls ) ) );
			return result;
		}
		return value;
	}

	public static <T> JsonCodec<List<T>> list(JsonCodec<T> element) {
		return new JsonCodec<>() {
			public List<T> decode(JsonNode value) {
				if ( isNull( value ) ) {
					return null;
				}
				if ( !( value instanceof ArrayNode array ) ) {
					throw new IllegalArgumentException( "Expected JSON array" );
				}
				List<T> result = new ArrayList<>();
				array.forEach( e -> result.add( element.decode( e ) ) );
				return result;
			}

			public JsonNode encode(List<T> value, boolean serializeNulls) {
				if ( value == null ) {
					return NullNode.instance;
				}
				ArrayNode result = JsonNodeFactory.instance.arrayNode();
				value.forEach( e -> result.add( element.encode( e, serializeNulls ) ) );
				return result;
			}
		};
	}

	public static <T> JsonCodec<Map<String, T>> map(JsonCodec<T> element) {
		return new JsonCodec<>() {
			public Map<String, T> decode(JsonNode value) {
				if ( isNull( value ) ) {
					return null;
				}
				if ( !( value instanceof ObjectNode object ) ) {
					throw new IllegalArgumentException( "Expected JSON object" );
				}
				Map<String, T> result = new LinkedHashMap<>();
				object.properties().forEach( e -> result.put( e.getKey(), element.decode( e.getValue() ) ) );
				return result;
			}

			public JsonNode encode(Map<String, T> value, boolean serializeNulls) {
				if ( value == null ) {
					return NullNode.instance;
				}
				ObjectNode result = JsonNodeFactory.instance.objectNode();
				value.forEach( (k, v) -> put( result, k, element.encode( v, serializeNulls ), serializeNulls ) );
				return result;
			}
		};
	}
}
