package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl;

import com.google.gson.JsonElement;

public interface UnknownTypeJsonAccessor extends JsonAccessor<JsonElement> {

	JsonObjectAccessor asObject();

	JsonArrayAccessor asArray();

	JsonAccessor<String> asString();

	JsonAccessor<Boolean> asBoolean();

	JsonAccessor<Integer> asInteger();

	JsonAccessor<Long> asLong();

	JsonAccessor<Float> asFloat();

	JsonAccessor<Double> asDouble();

	UnknownTypeJsonAccessor element(int index);

	UnknownTypeJsonAccessor property(String propertyName);

}
