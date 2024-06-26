/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.search.backend.elasticsearch.gson.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
		if ( !array.contains( newValue ) ) {
			array.add( newValue );
		}
	}
}
