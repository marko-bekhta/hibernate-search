package org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl;

import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.StringNode;

public final class FormatJsonCodec implements JsonCodec<java.util.List<String>> {
	public java.util.List<String> decode(JsonNode value) {
		if ( JsonCodecs.isNull( value ) ) {
			return null;
		}
		if ( !( value.isString() || value.isNumber() ) ) {
			throw new IllegalArgumentException( "Expected format string" );
		}
		return java.util.Arrays.asList( JsonCodecs.STRING.decode( value ).split( "\\|\\|" ) );
	}

	public JsonNode encode(java.util.List<String> value, boolean serializeNulls) {
		return value == null ? NullNode.instance : StringNode.valueOf( String.join( "||", value ) );
	}
}
