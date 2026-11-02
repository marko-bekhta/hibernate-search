package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import org.hibernate.search.backend.elasticsearch.gson.GsonJsonHandlers;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;

import com.google.gson.JsonElement;

/**
 * Codec for the deprecated native-field convenience API.
 * Arrays retain the historical interpretation as multiple field values.
 * Typed native fields instead decode a root array as a single JSON value.
 */
@Deprecated
public class ElasticsearchJsonElementFieldCodec extends ElasticsearchJsonHandlerFieldCodec<JsonElement> {
	public ElasticsearchJsonElementFieldCodec(JsonCodecService json) {
		super( json, GsonJsonHandlers.value() );
	}

	@Override
	public boolean canDecodeArrays() {
		return false;
	}
}
