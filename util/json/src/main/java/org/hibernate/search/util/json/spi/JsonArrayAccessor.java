package org.hibernate.search.util.json.spi;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;

public interface JsonArrayAccessor extends JsonAccessor<JsonArray> {
	UnknownTypeJsonAccessor element(int index);

	/**
	 * Add the given JsonElement to the array this accessor points to for the given {@code root},
	 * unless it is already present.
	 *
	 * @param root The root to be accessed.
	 * @param newValue The value to add.
	 * @throws UnexpectedJsonElementTypeException If an element in the path has unexpected type, preventing
	 * write access to the array this accessor points to.
	 */
	void addElementIfAbsent(JsonObject root, JsonElement newValue);
}
