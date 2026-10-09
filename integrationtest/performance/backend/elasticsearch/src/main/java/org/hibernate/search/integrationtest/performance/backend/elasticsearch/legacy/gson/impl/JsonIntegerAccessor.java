package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl;

import com.google.gson.JsonElement;

public class JsonIntegerAccessor extends AbstractTypingJsonAccessor<Integer> {

	public JsonIntegerAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<Integer> getExpectedElementType() {
		return JsonElementTypes.INTEGER;
	}

}
