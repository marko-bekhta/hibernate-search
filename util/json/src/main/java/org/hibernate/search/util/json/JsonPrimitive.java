package org.hibernate.search.util.json;

import java.math.BigInteger;
import java.util.Objects;

import org.hibernate.search.util.json.impl.JsonNumber;

/** An immutable string, boolean or number. Numeric equality is exact and ignores decimal scale. */
public final class JsonPrimitive extends JsonElement {
	private final Object value;

	public JsonPrimitive(String value) {
		this.value = Objects.requireNonNull( value );
	}

	public JsonPrimitive(Character value) {
		this( Objects.requireNonNull( value ).toString() );
	}

	public JsonPrimitive(Boolean value) {
		this.value = Objects.requireNonNull( value );
	}

	public JsonPrimitive(Number value) {
		Objects.requireNonNull( value );
		if ( value instanceof JsonNumber number && number.isValidated() ) {
			// Immutable parser numbers already contain a validated snapshot; no regex or second wrapper.
			this.value = number;
		}
		else {
			this.value = JsonNumber.jsonNumber( value.toString() );
		}
	}

	/** Create a number preserving its original JSON lexeme. */
	public static JsonPrimitive number(String lexeme) {
		return new JsonPrimitive( JsonNumber.jsonNumber( lexeme ) );
	}

	public boolean isString() {
		return value instanceof String;
	}

	public boolean isBoolean() {
		return value instanceof Boolean;
	}

	public boolean isNumber() {
		return value instanceof Number;
	}

	@Override
	public String getAsString() {
		return value.toString();
	}

	@Override
	public Number getAsNumber() {
		if ( isNumber() ) {
			return (Number) value;
		}
		if ( isString() ) {
			return new JsonNumber( getAsString() );
		}
		throw new UnsupportedOperationException( "Primitive is neither a number nor a string" );
	}


	@Override
	public int getAsInt() {
		return isNumber() ? getAsNumber().intValue() : Integer.parseInt( getAsString() );
	}

	@Override
	public long getAsLong() {
		return isNumber() ? getAsNumber().longValue() : Long.parseLong( getAsString() );
	}

	@Override
	public short getAsShort() {
		return isNumber() ? getAsNumber().shortValue() : Short.parseShort( getAsString() );
	}

	@Override
	public byte getAsByte() {
		return isNumber() ? getAsNumber().byteValue() : Byte.parseByte( getAsString() );
	}

	@Override
	public float getAsFloat() {
		return isNumber() ? getAsNumber().floatValue() : Float.parseFloat( getAsString() );
	}

	@Override
	public double getAsDouble() {
		return isNumber() ? getAsNumber().doubleValue() : Double.parseDouble( getAsString() );
	}

	@Override
	public boolean getAsBoolean() {
		return isBoolean() ? (Boolean) value : super.getAsBoolean();
	}

	@Override
	public JsonPrimitive deepCopy() {
		return this;
	}

	private String canonicalNumber() {
		String number = getAsString();
		int exponentStart = Math.max( number.indexOf( 'e' ), number.indexOf( 'E' ) );
		String coefficient = exponentStart < 0 ? number : number.substring( 0, exponentStart );
		BigInteger exponent = exponentStart < 0 ? BigInteger.ZERO : new BigInteger( number.substring( exponentStart + 1 ) );
		int decimalPoint = coefficient.indexOf( '.' );
		if ( decimalPoint >= 0 ) {
			exponent = exponent.subtract( BigInteger.valueOf( coefficient.length() - decimalPoint - 1 ) );
			coefficient = coefficient.replace( ".", "" );
		}
		boolean negative = coefficient.charAt( 0 ) == '-';
		int first = negative ? 1 : 0;
		while ( first < coefficient.length() && coefficient.charAt( first ) == '0' ) {
			first++;
		}
		if ( first == coefficient.length() ) {
			return "0";
		}
		int last = coefficient.length();
		while ( coefficient.charAt( last - 1 ) == '0' ) {
			last--;
		}
		exponent = exponent.add( BigInteger.valueOf( coefficient.length() - last ) );
		return ( negative ? "-" : "" ) + coefficient.substring( first, last ) + "e" + exponent;
	}

	@Override
	public boolean equals(Object other) {
		if ( this == other ) {
			return true;
		}
		if ( !( other instanceof JsonPrimitive ) ) {
			return false;
		}
		JsonPrimitive primitive = (JsonPrimitive) other;
		if ( isNumber() && primitive.isNumber() ) {
			return canonicalNumber().equals( primitive.canonicalNumber() );
		}
		return value.equals( primitive.value );
	}

	@Override
	public int hashCode() {
		return isNumber() ? canonicalNumber().hashCode() : value.hashCode();
	}
}
