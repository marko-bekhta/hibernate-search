package org.hibernate.search.util.json;

/** The singleton JSON null value, distinct from a missing object member. */
public final class JsonNull extends JsonElement {
	public static final JsonNull INSTANCE = new JsonNull();

	private JsonNull() {
	}

	@Override
	public JsonNull deepCopy() {
		return this;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof JsonNull;
	}

	@Override
	public int hashCode() {
		return JsonNull.class.hashCode();
	}
}
