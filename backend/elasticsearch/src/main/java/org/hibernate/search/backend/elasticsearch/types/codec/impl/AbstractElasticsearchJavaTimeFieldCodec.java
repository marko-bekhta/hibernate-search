package org.hibernate.search.backend.elasticsearch.types.codec.impl;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonElementTypes;

public abstract class AbstractElasticsearchJavaTimeFieldCodec<T extends TemporalAccessor>
		extends AbstractElasticsearchFieldCodec<T> {

	protected final DateTimeFormatter formatter;

	public AbstractElasticsearchJavaTimeFieldCodec(JsonCodecService json, DateTimeFormatter delegate) {
		super( json );
		this.formatter = delegate;
	}

	@Override
	public JsonElement encode(T value) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		return new JsonPrimitive( nullUnsafeFormat( value ) );
	}

	/**
	 * A different encoding is required for provided missing Java time values. See HSEARCH-3255.
	 *
	 * @param value to encode
	 * @return a {@link JsonElement} containing the long scalar value, if {@code value} is not null.
	 */
	@Override
	public JsonElement encodeForMissing(T value) {
		if ( value == null ) {
			return null;
		}

		return new JsonPrimitive( nullUnsafeScalar( value ) );
	}

	@Override
	public T decode(JsonElement element) {
		if ( element == null || element.isJsonNull() ) {
			return null;
		}
		return nullUnsafeParse( JsonElementTypes.STRING.fromElement( element ) );
	}

	@Override
	public T decodeAggregationKey(JsonElement key, JsonElement keyAsString) {
		return decode( keyAsString );
	}

	@Override
	public boolean isCompatibleWith(ElasticsearchFieldCodec<?> obj) {
		if ( obj == this ) {
			return true;
		}
		if ( obj.getClass() != getClass() ) {
			return false;
		}

		AbstractElasticsearchJavaTimeFieldCodec<?> other = (AbstractElasticsearchJavaTimeFieldCodec<?>) obj;
		return formatter.equals( other.formatter );
	}

	protected String nullUnsafeFormat(T value) {
		return formatter.format( value );
	}

	protected abstract T nullUnsafeParse(String stringValue);

	protected abstract Long nullUnsafeScalar(T value);
}
