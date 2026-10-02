package org.hibernate.search.util.json;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.hibernate.search.util.json.impl.JsonDiagnosticRenderer;

/** A Search-owned JSON tree node. Containers are mutable; primitives and null are immutable.
 * Trees submitted as request bodies must remain unchanged until all retries complete.
 */
public abstract class JsonElement {
	public final boolean isJsonObject() {
		return this instanceof JsonObject;
	}

	public final boolean isJsonArray() {
		return this instanceof JsonArray;
	}

	public final boolean isJsonPrimitive() {
		return this instanceof JsonPrimitive;
	}

	public final boolean isJsonNull() {
		return this instanceof JsonNull;
	}

	public JsonObject getAsJsonObject() {
		return (JsonObject) this;
	}

	public JsonArray getAsJsonArray() {
		return (JsonArray) this;
	}

	public JsonPrimitive getAsJsonPrimitive() {
		return (JsonPrimitive) this;
	}

	public String getAsString() {
		throw new UnsupportedOperationException( getClass().getSimpleName() );
	}

	public Number getAsNumber() {
		throw new UnsupportedOperationException( getClass().getSimpleName() );
	}

	public boolean getAsBoolean() {
		return Boolean.parseBoolean( getAsString() );
	}

	public int getAsInt() {
		return getAsNumber().intValue();
	}

	public long getAsLong() {
		return getAsNumber().longValue();
	}

	public float getAsFloat() {
		return getAsNumber().floatValue();
	}

	public double getAsDouble() {
		return getAsNumber().doubleValue();
	}

	public byte getAsByte() {
		return getAsNumber().byteValue();
	}

	public short getAsShort() {
		return getAsNumber().shortValue();
	}

	public BigDecimal getAsBigDecimal() {
		return new BigDecimal( getAsString() );
	}

	public BigInteger getAsBigInteger() {
		return new BigInteger( getAsString() );
	}

	public abstract JsonElement deepCopy();

	/** Diagnostic rendering; may allocate a complete string. */
	@Override
	public final String toString() {
		return JsonDiagnosticRenderer.render( this );
	}
}
