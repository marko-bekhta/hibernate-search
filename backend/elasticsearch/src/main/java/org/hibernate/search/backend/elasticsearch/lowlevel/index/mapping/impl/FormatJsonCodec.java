package org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecs;

public final class FormatJsonCodec implements JsonCodec<java.util.List<String>> {
	public java.util.List<String> decode(JsonElement value) {
		if ( JsonCodecs.isNull( value ) ) {
			return null;
		}
		if ( !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean() ) {
			throw new IllegalArgumentException( "Expected format string" );
		}
		return java.util.Arrays.asList( value.getAsString().split( "\\|\\|" ) );
	}

	public JsonElement encode(java.util.List<String> value, boolean serializeNulls) {
		return value == null ? JsonNull.INSTANCE : new JsonPrimitive( String.join( "||", value ) );
	}
}
