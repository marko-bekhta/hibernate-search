package org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl;

import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecs;

public final class DynamicTypeJsonCodec implements JsonCodec<DynamicType> {
	public DynamicType decode(JsonElement value) {
		if ( JsonCodecs.isNull( value ) ) {
			return null;
		}
		if ( !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() ) {
			throw new IllegalArgumentException( "Expected dynamic string" );
		}
		return switch ( value.getAsString() ) {
			case "true" -> DynamicType.TRUE;
			case "false" -> DynamicType.FALSE;
			case "strict" -> DynamicType.STRICT;
			default -> throw new AssertionFailure( "Unknown dynamic type value: " + value );
		};
	}

	public JsonElement encode(DynamicType value, boolean serializeNulls) {
		return value == null ? JsonNull.INSTANCE : new JsonPrimitive( switch ( value ) {
			case TRUE -> "true";
			case FALSE -> "false";
			case STRICT -> "strict";
		} );
	}
}
