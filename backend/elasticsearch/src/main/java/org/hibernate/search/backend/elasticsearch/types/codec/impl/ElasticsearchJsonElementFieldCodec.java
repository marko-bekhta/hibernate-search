package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;

public class ElasticsearchJsonElementFieldCodec implements ElasticsearchFieldCodec<JsonElement> {

	private final JsonCodecService json;

	public ElasticsearchJsonElementFieldCodec(JsonCodecService json) {
		this.json = json;
	}

	@Override
	public JsonElement encode(JsonElement value) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		return value;
	}

	@Override
	public JsonElement decode(JsonElement element) {
		if ( element == null || element.isJsonNull() ) {
			return null;
		}
		return element;
	}

	@Override
	public JsonElement decode(Double value) {
		return new JsonPrimitive( value );
	}

	@Override
	public boolean isCompatibleWith(ElasticsearchFieldCodec<?> other) {
		if ( other == this ) {
			return true;
		}
		if ( other == null || !getClass().equals( other.getClass() ) ) {
			return false;
		}

		ElasticsearchJsonElementFieldCodec castedOther = (ElasticsearchJsonElementFieldCodec) other;
		return json.equals( castedOther.json );
	}

	@Override
	public JsonElement fromJsonStringToElement(String value) {
		return json.decode( value, JsonElement.class );
	}

	@Override
	public String fromJsonElementToString(JsonElement value) {
		return json.write( value );
	}
}
