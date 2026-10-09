package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl;

import com.google.gson.JsonElement;

public class JsonStringAccessor extends AbstractTypingJsonAccessor<String> {

	public JsonStringAccessor(JsonAccessor<JsonElement> parentAccessor) {
		super( parentAccessor );
	}

	@Override
	protected JsonElementType<String> getExpectedElementType() {
		return JsonElementTypes.STRING;
	}

}
