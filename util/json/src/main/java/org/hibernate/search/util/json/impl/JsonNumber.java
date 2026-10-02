package org.hibernate.search.util.json.impl;

import java.math.BigDecimal;

/** Immutable number retaining its lexical representation and narrowing without a double round trip. */
public final class JsonNumber extends Number {
	private final String lexeme;

	public JsonNumber(String lexeme) {
		this.lexeme = lexeme;
	}

	@Override
	public int intValue() {
		return new BigDecimal( lexeme ).intValue();
	}

	@Override
	public long longValue() {
		return new BigDecimal( lexeme ).longValue();
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
