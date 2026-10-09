package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.lowlevel.syntax.search.impl.ElasticsearchSearchSyntax;
import org.hibernate.search.engine.cfg.spi.NumberUtils;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonElementTypes;

public final class ElasticsearchLongFieldCodec extends AbstractElasticsearchFieldCodec<Long> {

	public ElasticsearchLongFieldCodec(JsonCodecService json) {
		super( json );
	}

	@Override
	public JsonElement encode(Long value) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		return new JsonPrimitive( value );
	}

	@Override
	public JsonElement encodeForAggregation(ElasticsearchSearchSyntax searchSyntax, Long value) {
		return searchSyntax.encodeLongForAggregation( value );
	}

	@Override
	public Long decode(JsonElement element) {
		if ( element == null || element.isJsonNull() ) {
			return null;
		}
		return JsonElementTypes.LONG.fromElement( element );
	}

	@Override
	public Long decode(Double value) {
		return NumberUtils.toLong( value );
	}

	@Override
	public boolean isCompatibleWith(ElasticsearchFieldCodec<?> other) {
		return other instanceof ElasticsearchLongFieldCodec;
	}
}
