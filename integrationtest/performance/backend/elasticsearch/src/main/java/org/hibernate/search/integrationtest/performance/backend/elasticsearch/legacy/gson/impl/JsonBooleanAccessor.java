package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl;

import com.google.gson.JsonElement;

public class JsonBooleanAccessor extends AbstractTypingJsonAccessor<Boolean> {

	public JsonBooleanAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<Boolean> getExpectedElementType() {
		return JsonElementTypes.BOOLEAN;
	}

}
