package org.hibernate.search.backend.elasticsearch.lowlevel.index.mapping.impl;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecRegistry;
import org.hibernate.search.util.json.spi.JsonCodecs;

public final class NamedDynamicTemplateJsonCodec implements JsonCodec<NamedDynamicTemplate> {
	private final JsonCodec<DynamicTemplate> template;

	public NamedDynamicTemplateJsonCodec(JsonCodecRegistry registry) {
		template = registry.reference( DynamicTemplate.class );
	}

	public NamedDynamicTemplate decode(JsonElement value) {
		if ( JsonCodecs.isNull( value ) ) {
			return null;
		}
		JsonObject object = value.getAsJsonObject();
		if ( object.size() != 1 ) {
			throw new IllegalArgumentException( "Expected exactly one named dynamic template" );
		}
		var e = object.entrySet().iterator().next();
		return new NamedDynamicTemplate( e.getKey(), template.decode( e.getValue() ) );
	}

	public JsonElement encode(NamedDynamicTemplate value, boolean serializeNulls) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		JsonObject result = new JsonObject();
		JsonCodecs.put( result, value.name, template.encode( value.template, serializeNulls ), serializeNulls );
		return result;
	}
}
