package org.hibernate.search.util.json.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BigIntegerNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.FloatNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.LongNode;
import tools.jackson.databind.node.ShortNode;

/**
 * Shared exact-number policy for character and asynchronous token construction.
 */
public final class JsonNumbers {

	public static final int MAX_DECIMAL_EXPONENT = Integer.MAX_VALUE;

	private static final Pattern JSON_NUMBER =
			Pattern.compile( "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?" );

	private static final BigInteger MAX_EXPONENT = BigInteger.valueOf( MAX_DECIMAL_EXPONENT );

	private JsonNumbers() {
	}

	/** Construct an integer token whose JSON syntax has already been validated by the parser. */
	public static JsonNode fromIntegerToken(String text) {
		// Keep the cheap int cache/long route without routing exact values through double.
		if ( text.length() <= 20 ) {
			try {
				long value = Long.parseLong( text );
				return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
						? IntNode.valueOf( (int) value )
						: LongNode.valueOf( value );
			}
			catch (NumberFormatException tooLarge) {
				/* Exact BigInteger route below. */
			}
		}
		return BigIntegerNode.valueOf( new BigInteger( text ) );
	}

	/** Construct a floating token whose JSON syntax has already been validated by the parser. */
	public static JsonNode fromFloatingToken(String text) {
		int exponent = Math.max( text.indexOf( 'e' ), text.indexOf( 'E' ) );
		if ( exponent >= 0 && new BigInteger( text.substring( exponent + 1 ) ).abs().compareTo( MAX_EXPONENT ) > 0 ) {
			throw new IllegalArgumentException( "Unsupported JSON decimal exponent" );
		}
		BigDecimal value = new BigDecimal( text );
		validateDecimal( value );
		return text.charAt( 0 ) == '-' && value.signum() == 0 ? DoubleNode.valueOf( -0.0d ) : DecimalNode.valueOf( value );
	}

	public static void validateDecimal(BigDecimal value) {
		long adjustedExponent = value.precision() - 1L - value.scale();
		if ( value.scale() == Integer.MIN_VALUE || Math.abs( adjustedExponent ) > MAX_DECIMAL_EXPONENT ) {
			throw new IllegalArgumentException( "Unsupported JSON decimal scale/exponent" );
		}
	}

	/**
	 * Snapshot a supported Java scalar number without widening exact values through double.
	 */
	public static JsonNode fromNumber(Number value) {
		java.util.Objects.requireNonNull( value );
		if ( value instanceof Byte || value instanceof Integer ) {
			return IntNode.valueOf( value.intValue() );
		}
		if ( value instanceof Short ) {
			return ShortNode.valueOf( value.shortValue() );
		}
		if ( value instanceof Long ) {
			return LongNode.valueOf( value.longValue() );
		}
		if ( value instanceof BigInteger integer ) {
			return BigIntegerNode.valueOf( integer );
		}
		if ( value instanceof BigDecimal decimal ) {
			validateDecimal( decimal );
			return DecimalNode.valueOf( decimal );
		}
		if ( value instanceof Float ) {
			if ( !Float.isFinite( value.floatValue() ) ) {
				throw new IllegalArgumentException( "Non-finite JSON number" );
			}
			return FloatNode.valueOf( value.floatValue() );
		}
		if ( value instanceof Double ) {
			if ( !Double.isFinite( value.doubleValue() ) ) {
				throw new IllegalArgumentException( "Non-finite JSON number" );
			}
			return DoubleNode.valueOf( value.doubleValue() );
		}
		// Mutable/custom Number implementations are snapshotted through their numeric text, never databinding.
		String text = value.toString();
		if ( !JSON_NUMBER.matcher( text ).matches() ) {
			throw new IllegalArgumentException( "Unsupported JSON number" );
		}
		return text.indexOf( '.' ) >= 0 || text.indexOf( 'e' ) >= 0 || text.indexOf( 'E' ) >= 0
				? fromFloatingToken( text )
				: fromIntegerToken( text );
	}

	/**
	 * Mathematical integral conversion; bound decimal expansion independently of storage acceptance.
	 */
	public static BigInteger toBigInteger(BigDecimal value) {
		validateDecimal( value );
		if ( value.signum() == 0 ) {
			return BigInteger.ZERO;
		}
		BigDecimal normalized = value.stripTrailingZeros();
		if ( normalized.scale() > 0 ) {
			throw new ArithmeticException( "JSON number is not integral" );
		}
		if ( normalized.scale() < -100_000 ) {
			throw new ArithmeticException( "JSON integer expansion exceeds supported scale" );
		}
		return normalized.toBigIntegerExact();
	}

	/**
	 * Truncating integral conversion for aggregation keys, with bounded decimal expansion.
	 */
	public static BigInteger truncateToBigInteger(BigDecimal value) {
		validateDecimal( value );
		if ( value.signum() == 0 ) {
			return BigInteger.ZERO;
		}
		BigDecimal normalized = value.stripTrailingZeros();
		if ( normalized.scale() < -100_000 ) {
			throw new ArithmeticException( "JSON integer expansion exceeds supported scale" );
		}
		if ( normalized.scale() >= normalized.precision() ) {
			return BigInteger.ZERO;
		}
		return normalized.toBigInteger();
	}

	/**
	 * Standard scalar output; no reflective serializer or approximate number conversion.
	 */
	public static String render(JsonNode node) {
		Class<?> type = node.getClass();
		if ( type == DecimalNode.class ) {
			validateDecimal( node.decimalValue() );
		}
		else if ( type == FloatNode.class || type == DoubleNode.class ) {
			if ( !Double.isFinite( node.doubleValue() ) ) {
				throw new IllegalArgumentException( "Non-finite JSON number" );
			}
		}
		else if ( type != ShortNode.class && type != IntNode.class && type != LongNode.class && type != BigIntegerNode.class ) {
			throw new IllegalArgumentException( "Unsupported JSON number " + type.getName() );
		}
		return node.asString();
	}
}
