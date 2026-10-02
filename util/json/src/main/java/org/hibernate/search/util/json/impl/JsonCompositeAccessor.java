package org.hibernate.search.util.json.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;

/**
 * A {@link JsonAccessor} that can be crawled by a {@link AbstractCrawlingJsonAccessor}.
 *
 */
interface JsonCompositeAccessor<T extends JsonElement> extends JsonAccessor<T> {

	T getOrCreate(JsonObject root);

}
