package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.engine.cfg.spi.NumberUtils;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonElementTypes;

public final class ElasticsearchIntegerFieldCodec extends AbstractElasticsearchFieldCodec<Integer> {

	public ElasticsearchIntegerFieldCodec(JsonCodecService json) {
		super( json );
	}

	@Override
	public JsonElement encode(Integer value) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		return new JsonPrimitive( value );
	}

	@Override
	public Integer decode(JsonElement element) {
		if ( element == null || element.isJsonNull() ) {
			return null;
		}
		return JsonElementTypes.INTEGER.fromElement( element );
	}

	@Override
	public Integer decode(Double value) {
		return NumberUtils.toInteger( value );
	}

	@Override
	public boolean isCompatibleWith(ElasticsearchFieldCodec<?> other) {
		return other instanceof ElasticsearchIntegerFieldCodec;
	}
}
