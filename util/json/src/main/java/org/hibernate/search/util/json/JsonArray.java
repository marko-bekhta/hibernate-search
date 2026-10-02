package org.hibernate.search.util.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Mutable insertion-ordered array; Java null additions become JSON null. */
public final class JsonArray extends JsonElement implements Iterable<JsonElement> {
	private final List<JsonElement> elements;

	public JsonArray() {
		this.elements = new ArrayList<>();
	}

	public JsonArray(int capacity) {
		this.elements = new ArrayList<>( capacity );
	}

	public void add(JsonElement value) {
		elements.add( value == null ? JsonNull.INSTANCE : value );
	}

	public void add(String value) {
		add( value == null ? null : new JsonPrimitive( value ) );
	}

	public void add(Number value) {
		add( value == null ? null : new JsonPrimitive( value ) );
	}

	public void add(Boolean value) {
		add( value == null ? null : new JsonPrimitive( value ) );
	}

	public void add(Character value) {
		add( value == null ? null : new JsonPrimitive( value ) );
	}

	public void addAll(JsonArray values) {
		elements.addAll( values.elements );
	}

	public JsonElement get(int index) {
		return elements.get( index );
	}

	public JsonElement set(int index, JsonElement value) {
		return elements.set( index, value == null ? JsonNull.INSTANCE : value );
	}

	public JsonElement remove(int index) {
		return elements.remove( index );
	}

	public boolean remove(JsonElement value) {
		return elements.remove( value );
	}

	public boolean contains(JsonElement value) {
		return elements.contains( value );
	}

	public int size() {
		return elements.size();
	}

	public boolean isEmpty() {
		return elements.isEmpty();
	}

	@Override
	public Iterator<JsonElement> iterator() {
		return elements.iterator();
	}

	@Override
	public JsonArray deepCopy() {
		JsonArray copy = new JsonArray( size() );
		elements.forEach( value -> copy.add( value.deepCopy() ) );
		return copy;
	}

	private JsonElement single() {
		if ( size() != 1 ) {
			throw new IllegalStateException( "Expected a singleton array" );
		}
		return get( 0 );
	}

	@Override
	public String getAsString() {
		return single().getAsString();
	}

	@Override
	public Number getAsNumber() {
		return single().getAsNumber();
	}


	@Override
	public int getAsInt() {
		return single().getAsInt();
	}

	@Override
	public long getAsLong() {
		return single().getAsLong();
	}

	@Override
	public short getAsShort() {
		return single().getAsShort();
	}

	@Override
	public byte getAsByte() {
		return single().getAsByte();
	}

	@Override
	public float getAsFloat() {
		return single().getAsFloat();
	}

	@Override
	public double getAsDouble() {
		return single().getAsDouble();
	}

	@Override
	public boolean getAsBoolean() {
		return single().getAsBoolean();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof JsonArray && elements.equals( ( (JsonArray) other ).elements );
	}

	@Override
	public int hashCode() {
		return elements.hashCode();
	}
}
