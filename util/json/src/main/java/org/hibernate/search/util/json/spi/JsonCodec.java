package org.hibernate.search.util.json.spi;

import tools.jackson.databind.JsonNode;

/** Explicit tree codec. Implementations construct known types without runtime field inspection. */
public interface JsonCodec<T> {
	T decode(JsonNode value);

	JsonNode encode(T value, boolean serializeNulls);
}
