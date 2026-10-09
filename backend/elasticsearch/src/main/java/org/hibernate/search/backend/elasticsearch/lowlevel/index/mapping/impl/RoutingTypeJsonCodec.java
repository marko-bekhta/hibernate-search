package org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl;

import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecs;

public final class RoutingTypeJsonCodec implements JsonCodec<RoutingType> {
	public RoutingType decode(JsonElement value) {
		if ( JsonCodecs.isNull( value ) ) {
			return null;
		}
		RoutingType result = null;
		for ( var e : value.getAsJsonObject().entrySet() ) {
			if ( !e.getKey().equals( "required" ) ) {
				throw new AssertionFailure(
						"Unexpected property for attribute of type " + RoutingType.class + ": " + e.getKey() );
			}
			if ( !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isBoolean() ) {
				throw new IllegalArgumentException( "Expected routing required boolean" );
			}
			result = e.getValue().getAsBoolean() ? RoutingType.REQUIRED : RoutingType.OPTIONAL;
		}
		return result;
	}

	public JsonElement encode(RoutingType value, boolean serializeNulls) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		if ( value != RoutingType.REQUIRED ) {
			throw new AssertionFailure( "Unexpected value for attribute of type " + RoutingType.class + ": " + value );
		}
		JsonObject result = new JsonObject();
		result.addProperty( "required", true );
		return result;
	}
}
