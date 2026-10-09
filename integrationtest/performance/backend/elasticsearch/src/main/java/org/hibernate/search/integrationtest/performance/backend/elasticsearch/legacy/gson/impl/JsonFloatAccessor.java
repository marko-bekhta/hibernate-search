package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl;

import com.google.gson.JsonElement;

public class JsonFloatAccessor extends AbstractTypingJsonAccessor<Float> {

	public JsonFloatAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<Float> getExpectedElementType() {
		return JsonElementTypes.FLOAT;
	}

}
