package org.hibernate.search.backend.elasticsearch.client.common.json.spi;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.util.json.spi.JsonCodecRegistry;

/** Library-neutral JSON services supplied to client extensions. */
public final class JsonProvider {
	private final JsonCodecService codec, codecNoSerializeNulls;
	private final JsonLogHelper logHelper;

	public JsonProvider(JsonCodecRegistry registry, boolean logPrettyPrinting) {
		codec = new JsonCodecService( registry, true, false );
		codecNoSerializeNulls = new JsonCodecService( registry, false, false );
		logHelper = JsonLogHelper.create( new JsonCodecService( registry, false, logPrettyPrinting ), logPrettyPrinting );
	}

	public JsonCodecService getCodec() {
		return codec;
	}

	public JsonCodecService getCodecNoSerializeNulls() {
		return codecNoSerializeNulls;
	}

	public JsonLogHelper getLogHelper() {
		return logHelper;
	}
}
