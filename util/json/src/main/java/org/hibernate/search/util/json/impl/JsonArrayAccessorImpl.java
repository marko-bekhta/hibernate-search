package org.hibernate.search.util.json.impl;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonArrayAccessor;
import org.hibernate.search.util.json.spi.JsonElementType;
import org.hibernate.search.util.json.spi.JsonElementTypes;
import org.hibernate.search.util.json.spi.UnknownTypeJsonAccessor;

public class JsonArrayAccessorImpl extends AbstractTypingJsonAccessor<JsonArray>
		implements JsonArrayAccessor, JsonCompositeAccessor<JsonArray> {

	public JsonArrayAccessorImpl(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<JsonArray> getExpectedElementType() {
		return JsonElementTypes.ARRAY;
	}

	@Override
	public JsonArray getOrCreate(JsonObject root) {
		return getOrCreate( root, JsonArray::new );
	}

	@Override
	public UnknownTypeJsonAccessor element(int index) {
		return new ArrayElementJsonAccessor( this, index );
	}

	@Override
	public void addElementIfAbsent(JsonObject root, JsonElement newValue) {
		JsonArray array = getOrCreate( root );
		if ( array == null ) {
			throw new IllegalStateException( "Cannot modify an absent adapted array at " + this );
		}
		if ( !array.contains( newValue ) ) {
			array.add( newValue );
		}
	}
}
