package org.hibernate.search.util.json.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.hibernate.search.util.common.annotation.Incubating;

import tools.jackson.databind.JsonNode;

/** Immutable explicit registrations. Unknown classes fail; there is no reflective fallback. */
@Incubating
public final class JsonCodecRegistry {
	private final Map<Class<?>, JsonCodec<?>> codecs;

	public JsonCodecRegistry(Map<Class<?>, Function<JsonCodecRegistry, JsonCodec<?>>> factories) {
		Map<Class<?>, JsonCodec<?>> registrations = new LinkedHashMap<>();
		codecs = Collections.unmodifiableMap( registrations );
		factories.forEach( (type, factory) -> registrations.put( type, factory.apply( this ) ) );
	}

	@SuppressWarnings("unchecked")
	public <T> JsonCodec<T> get(Class<T> type) {
		JsonCodec<?> codec = codecs.get( type );
		if ( codec == null ) {
			throw new IllegalArgumentException( "No JSON codec registered for " + type.getName() );
		}
		return (JsonCodec<T>) codec;
	}

	/** Deferred direct codec lookup allows recursive model graphs without reflective mapping. */
	public <T> JsonCodec<T> reference(Class<T> type) {
		return new JsonCodec<>() {
			public T decode(JsonNode value) {
				return get( type ).decode( value );
			}

			public JsonNode encode(T value, boolean serializeNulls) {
				return get( type ).encode( value, serializeNulls );
			}
		};
	}
}
