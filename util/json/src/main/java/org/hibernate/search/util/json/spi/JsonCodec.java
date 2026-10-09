package org.hibernate.search.util.json.spi;

import org.hibernate.search.util.json.JsonElement;

/** Explicit tree codec. Implementations construct known types without runtime field inspection. */
public interface JsonCodec<T> {
	T decode(JsonElement value);

	JsonElement encode(T value, boolean serializeNulls);
}
