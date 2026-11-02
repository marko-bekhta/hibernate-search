package org.hibernate.search.util.json.spi;

import com.google.gson.JsonObject;
import tools.jackson.core.JsonParser;

public interface JsonCodec {
	JsonParser jacksonAllowed();

	JsonObject gsonStillForbidden();
}
