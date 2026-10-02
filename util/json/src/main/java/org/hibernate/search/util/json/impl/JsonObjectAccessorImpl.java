package org.hibernate.search.util.json.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonElementType;
import org.hibernate.search.util.json.spi.JsonElementTypes;
import org.hibernate.search.util.json.spi.JsonObjectAccessor;
import org.hibernate.search.util.json.spi.UnknownTypeJsonAccessor;

/**
 * A {@link JsonAccessor} that ensures the accessed object is a {@link JsonObject}.
 *
 */
public class JsonObjectAccessorImpl extends AbstractTypingJsonAccessor<JsonObject>
		implements JsonObjectAccessor, JsonCompositeAccessor<JsonObject> {

	public JsonObjectAccessorImpl(JsonAccessor<JsonElement> parent) {
		super( parent );
	}

	@Override
	protected JsonElementType<JsonObject> getExpectedElementType() {
		return JsonElementTypes.OBJECT;
	}

	@Override
	public JsonObject getOrCreate(JsonObject root) {
		return getOrCreate( root, JsonObject::new );
	}

	@Override
	public UnknownTypeJsonAccessor property(String propertyName) {
		java.util.Objects.requireNonNull( propertyName );
		return new ObjectPropertyJsonAccessor( this, propertyName );
	}
}
