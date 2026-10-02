package org.hibernate.search.util.json.impl;

import java.math.BigDecimal;

/** Immutable number retaining its lexical representation and narrowing without a double round trip. */
public final class JsonNumber extends Number {
	private final String lexeme;
	private final boolean validated;
	private final boolean hasLong;
	private final long integral;

	public JsonNumber(String lexeme) {
		this( lexeme, false );
	}

	private JsonNumber(String lexeme, boolean validated) {
		this.lexeme = lexeme;
		this.validated = validated;
		int length = lexeme.length();
		int offset = 0;
		boolean negative = length != 0 && lexeme.charAt( 0 ) == '-';
		if ( negative || length != 0 && lexeme.charAt( 0 ) == '+' ) {
			offset++;
		}
		boolean fits = offset < length;
		long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
		long multiplicationLimit = limit / 10;
		long result = 0;
		while ( fits && offset < length ) {
			int digit = lexeme.charAt( offset++ ) - '0';
			if ( digit < 0 || digit > 9 || result < multiplicationLimit ) {
				fits = false;
			}
			else {
				result *= 10;
				if ( result < limit + digit ) {
					fits = false;
				}
				else {
					result -= digit;
				}
			}
		}
		hasLong = fits;
		integral = negative ? result : -result;
	}

	static JsonNumber fromParser(String lexeme) {
		return jsonNumber( lexeme );
	}

	/** Internal factory shared with the public model's validated-number construction. */
	public static JsonNumber jsonNumber(String lexeme) {
		int offset = lexeme.startsWith( "-" ) ? 1 : 0;
		int length = lexeme.length();
		if ( offset == length ) {
			throw invalid( lexeme );
		}
		if ( lexeme.charAt( offset ) == '0' ) {
			offset++;
		}
		else {
			char digit = lexeme.charAt( offset );
			if ( digit < '1' || digit > '9' ) {
				throw invalid( lexeme );
			}
			offset = digitsEnd( lexeme, offset );
		}
		if ( offset < length && lexeme.charAt( offset ) == '.' ) {
			int start = offset + 1;
			offset = digitsEnd( lexeme, start );
			if ( offset == start ) {
				throw invalid( lexeme );
			}
		}
		if ( offset < length && ( lexeme.charAt( offset ) == 'e' || lexeme.charAt( offset ) == 'E' ) ) {
			offset++;
			if ( offset < length && ( lexeme.charAt( offset ) == '+' || lexeme.charAt( offset ) == '-' ) ) {
				offset++;
			}
			int start = offset;
			offset = digitsEnd( lexeme, start );
			if ( offset == start ) {
				throw invalid( lexeme );
			}
		}
		if ( offset != length ) {
			throw invalid( lexeme );
		}
		return new JsonNumber( lexeme, true );
	}

	private static int digitsEnd(String text, int start) {
		int offset = start;
		while ( offset < text.length() && text.charAt( offset ) >= '0' && text.charAt( offset ) <= '9' ) {
			offset++;
		}
		return offset;
	}

	private static IllegalArgumentException invalid(String lexeme) {
		return new IllegalArgumentException( "Not a finite JSON number: " + lexeme );
	}

	/** Internal immutable marker: JSON-number grammar has been validated. */
	public boolean isValidated() {
		return validated;
	}

	@Override
	public int intValue() {
		return (int) longValue();
	}

	@Override
	public long longValue() {
		return hasLong ? integral : new BigDecimal( lexeme ).longValue();
	}

	@Override
	public float floatValue() {
		return Float.parseFloat( lexeme );
	}

	@Override
	public double doubleValue() {
		return Double.parseDouble( lexeme );
	}

	@Override
	public String toString() {
		return lexeme;
	}
}
