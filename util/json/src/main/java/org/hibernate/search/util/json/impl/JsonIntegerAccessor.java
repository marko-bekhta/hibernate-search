package org.hibernate.search.util.json.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonElementType;
import org.hibernate.search.util.json.spi.JsonElementTypes;

public class JsonIntegerAccessor extends AbstractTypingJsonAccessor<Integer> {

	public JsonIntegerAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<Integer> getExpectedElementType() {
		return JsonElementTypes.INTEGER;
	}

}
