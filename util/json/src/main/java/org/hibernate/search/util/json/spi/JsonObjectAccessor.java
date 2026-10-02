package org.hibernate.search.util.json.spi;

import org.hibernate.search.util.json.JsonObject;

public interface JsonObjectAccessor extends JsonAccessor<JsonObject> {

	UnknownTypeJsonAccessor property(String propertyName);

}
