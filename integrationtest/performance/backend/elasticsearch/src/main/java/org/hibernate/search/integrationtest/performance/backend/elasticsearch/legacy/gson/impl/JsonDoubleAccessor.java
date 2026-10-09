package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl;

import com.google.gson.JsonElement;

public class JsonDoubleAccessor extends AbstractTypingJsonAccessor<Double> {

	public JsonDoubleAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<Double> getExpectedElementType() {
		return JsonElementTypes.DOUBLE;
	}

}
