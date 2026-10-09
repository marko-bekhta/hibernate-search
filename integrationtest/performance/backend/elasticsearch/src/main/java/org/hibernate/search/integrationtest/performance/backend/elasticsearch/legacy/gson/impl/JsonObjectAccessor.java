package org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.gson.impl;

import com.google.gson.JsonObject;

public interface JsonObjectAccessor extends JsonAccessor<JsonObject> {

	UnknownTypeJsonAccessor property(String propertyName);

}
