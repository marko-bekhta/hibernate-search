package org.hibernate.search.util.json.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonElementType;
import org.hibernate.search.util.json.spi.JsonElementTypes;

public class JsonBooleanAccessor extends AbstractTypingJsonAccessor<Boolean> {

	public JsonBooleanAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<Boolean> getExpectedElementType() {
		return JsonElementTypes.BOOLEAN;
	}

}
