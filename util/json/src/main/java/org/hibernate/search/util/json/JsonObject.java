package org.hibernate.search.util.json;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable object with insertion-ordered members. Duplicate additions replace values in place. */
public final class JsonObject extends JsonElement {
	private final Map<String, JsonElement> members = new LinkedHashMap<>();

	public void add(String name, JsonElement value) {
		members.put( Objects.requireNonNull( name ), value == null ? JsonNull.INSTANCE : value );
	}

	public void addProperty(String name, String value) {
		add( name, value == null ? null : new JsonPrimitive( value ) );
	}

	public void addProperty(String name, Number value) {
		add( name, value == null ? null : new JsonPrimitive( value ) );
	}

	public void addProperty(String name, Boolean value) {
		add( name, value == null ? null : new JsonPrimitive( value ) );
	}

	public void addProperty(String name, Character value) {
		add( name, value == null ? null : new JsonPrimitive( value ) );
	}

	public JsonElement get(String name) {
		return members.get( name );
	}

	public JsonObject getAsJsonObject(String name) {
		return (JsonObject) get( name );
	}

	public JsonArray getAsJsonArray(String name) {
		return (JsonArray) get( name );
	}

	public JsonPrimitive getAsJsonPrimitive(String name) {
		return (JsonPrimitive) get( name );
	}

	public JsonElement remove(String name) {
		return members.remove( name );
	}

	public boolean has(String name) {
		return members.containsKey( name );
	}

	public int size() {
		return members.size();
	}

	public boolean isEmpty() {
		return members.isEmpty();
	}

	public Set<String> keySet() {
		return members.keySet();
	}

	public Set<Map.Entry<String, JsonElement>> entrySet() {
		return members.entrySet();
	}

	public Collection<JsonElement> values() {
		return members.values();
	}

	@Override
	public JsonObject deepCopy() {
		JsonObject copy = new JsonObject();
		members.forEach( (name, value) -> copy.add( name, value.deepCopy() ) );
		return copy;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof JsonObject && members.equals( ( (JsonObject) other ).members );
	}

	@Override
	public int hashCode() {
		return members.hashCode();
	}
}
