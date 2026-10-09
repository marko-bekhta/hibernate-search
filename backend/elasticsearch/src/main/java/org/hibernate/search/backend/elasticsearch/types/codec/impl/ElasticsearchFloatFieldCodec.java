package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.engine.cfg.spi.NumberUtils;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonElementTypes;

public final class ElasticsearchFloatFieldCodec extends AbstractElasticsearchFieldCodec<Float> {

	public ElasticsearchFloatFieldCodec(JsonCodecService json) {
		super( json );
	}

	@Override
	public JsonElement encode(Float value) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		return new JsonPrimitive( value );
	}

	@Override
	public Float decode(JsonElement element) {
		if ( element == null || element.isJsonNull() ) {
			return null;
		}
		return JsonElementTypes.FLOAT.fromElement( element );
	}

	@Override
	public Float decode(Double value) {
		return NumberUtils.toFloat( value );
	}

	@Override
	public boolean isCompatibleWith(ElasticsearchFieldCodec<?> other) {
		return other instanceof ElasticsearchFloatFieldCodec;
	}
}
