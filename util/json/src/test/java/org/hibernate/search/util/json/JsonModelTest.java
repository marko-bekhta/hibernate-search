package org.hibernate.search.util.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashSet;

import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;

class JsonModelTest {
	private final JsonParsing parsing = new JsonParsing();

	@Test
	void orderedMembersDuplicateReplacementNullAndIndependentCopies() {
		JsonObject root = parsing.parse( "{\"z\":1,\"a\":null,\"z\":2,\"nested\":{\"array\":[1]}}" ).getAsJsonObject();
		assertThat( root.keySet() ).containsExactly( "z", "a", "nested" );
		assertThat( root.get( "missing" ) ).isNull();
		assertThat( root.get( "a" ) ).isSameAs( JsonNull.INSTANCE );
		assertThat( root.get( "z" ).getAsInt() ).isEqualTo( 2 );
		JsonObject copy = root.deepCopy();
		copy.getAsJsonObject( "nested" ).getAsJsonArray( "array" ).add( 2 );
		assertThat( root.getAsJsonObject( "nested" ).getAsJsonArray( "array" ) ).hasSize( 1 );
		assertThat( copy.get( "z" ) ).isSameAs( root.get( "z" ) );
		root.add( "javaNull", null );
		assertThat( root.remove( "javaNull" ) ).isSameAs( JsonNull.INSTANCE );
	}

	@Test
	void integralFastPathMatchesDecimalNarrowing() {
		for ( String number : new String[] {
				"0",
				"-0",
				"42",
				"-42",
				"2147483647",
				"2147483648",
				"-2147483649",
				"9223372036854775807",
				"-9223372036854775808",
				"9223372036854775808",
				"-9223372036854775809",
				"18446744073709551617",
				"-18446744073709551617",
				"9999999999999999999999999999999999999",
				"1.9",
				"-1.9",
				"1e20",
				"1e-20",
				"1.23000e+03" } ) {
			BigDecimal reference = new BigDecimal( number );
			JsonPrimitive primitive = JsonPrimitive.number( number );
			assertThat( primitive.getAsLong() ).isEqualTo( reference.longValue() );
			assertThat( primitive.getAsInt() ).isEqualTo( reference.intValue() );
			assertThat( primitive.getAsShort() ).isEqualTo( reference.shortValue() );
			assertThat( primitive.getAsByte() ).isEqualTo( reference.byteValue() );
			assertThat( primitive.getAsString() ).isEqualTo( number );
		}
		for ( String text : new String[] { "+42", "00042", "+1.9", "+1e+3" } ) {
			Number actual = new JsonPrimitive( text ).getAsNumber();
			assertThat( actual.longValue() ).isEqualTo( new BigDecimal( text ).longValue() );
			assertThat( actual.intValue() ).isEqualTo( new BigDecimal( text ).intValue() );
		}
		for ( String text : new String[] { "", "-", "+", " 42", "NaN", "--42" } ) {
			Number actual = new JsonPrimitive( text ).getAsNumber();
			assertThatThrownBy( actual::longValue ).isInstanceOf( NumberFormatException.class );
		}
	}

	@Test
	void numberFactoriesRetainStrictGrammarAndSignedZero() {
		for ( String invalid : new String[] {
				"",
				"-",
				"+1",
				"01",
				"-01",
				".1",
				"-.1",
				"0.",
				"1e",
				"1e+",
				"1e-",
				"0E+",
				"1e+-1",
				"1x",
				"1 " } ) {
			assertThatThrownBy( () -> JsonPrimitive.number( invalid ) ).isInstanceOf( IllegalArgumentException.class );
		}
		for ( String valid : new String[] {
				"0",
				"-0",
				"0.1",
				"-0.0",
				"10",
				"1e+000",
				"-0E-000",
				"1E999999999999999999999999999" } ) {
			assertThat( JsonPrimitive.number( valid ).getAsString() ).isEqualTo( valid );
		}
		JsonPrimitive zero = JsonPrimitive.number( "-0" );
		assertThat( zero.getAsLong() ).isZero();
		assertThat( zero.getAsInt() ).isZero();
		assertThat( Double.doubleToRawLongBits( zero.getAsDouble() ) ).isEqualTo( Long.MIN_VALUE );
		assertThat( Float.floatToRawIntBits( zero.getAsFloat() ) ).isEqualTo( Integer.MIN_VALUE );
		assertThat( zero.getAsString() ).isEqualTo( "-0" );
	}

	@Test
	void numbersPreserveLexemesAndNarrowing() {
		for ( String number : new String[] { "-0", "1.2300e+04", "1.0E-04", "9007199254740993", "0.000", "-1e+0003" } ) {
			JsonElement value = parsing.parse( number );
			assertThat( value.getAsString() ).isEqualTo( number );
			assertThat( value.toString() ).isEqualTo( number );
		}
		assertThat( parsing.parse( "[0,-0,0 ]" ).toString() ).isEqualTo( "[0,-0,0]" );
		assertThat( parsing.parse( "9007199254740993" ).getAsLong() ).isEqualTo( 9007199254740993L );
		assertThat( parsing.parse( "1.9" ).getAsLong() ).isEqualTo( 1 );
		assertThat( parsing.parse( "4294967297" ).getAsInt() ).isEqualTo( 1 );
		assertThat( parsing.parse( "1.2300e+04" ).getAsBigDecimal() ).isEqualByComparingTo( new BigDecimal( "12300" ) );
	}

	@Test
	void exactNumericEqualityObeysHashContract() {
		JsonElement integer = new JsonPrimitive( 1L );
		JsonElement decimal = new JsonPrimitive( 1.0 );
		assertThat( integer ).isEqualTo( decimal ).hasSameHashCodeAs( decimal );
		assertThat( parsing.parse( "9007199254740993" ) ).isNotEqualTo( parsing.parse( "9007199254740992" ) );
		assertThat( parsing.parse( "-0.00" ) ).isEqualTo( parsing.parse( "0" ) ).hasSameHashCodeAs( parsing.parse( "0" ) );
		assertThat( parsing.parse( "1e999999999999999999999" ) )
				.isEqualTo( parsing.parse( "10e999999999999999999998" ) )
				.hasSameHashCodeAs( parsing.parse( "10e999999999999999999998" ) );
		HashSet<JsonElement> numbers = new HashSet<>();
		numbers.add( integer );
		numbers.add( decimal );
		assertThat( numbers ).hasSize( 1 );
		assertThat( integer ).isNotEqualTo( new JsonPrimitive( "1" ) );
		assertThat( parsing.parse( "{\"a\":1,\"b\":2}" ) ).isEqualTo( parsing.parse( "{\"b\":2,\"a\":1}" ) );
	}

	@Test
	void scalarConversionsDistinguishNumbersFromTextualRepresentations() {
		JsonPrimitive fractionalText = new JsonPrimitive( "1.9" );
		assertThat( fractionalText.getAsNumber().longValue() ).isEqualTo( 1 );
		assertThatThrownBy( fractionalText::getAsLong ).isInstanceOf( NumberFormatException.class );
		assertThatThrownBy( () -> new JsonPrimitive( "4294967297" ).getAsInt() ).isInstanceOf( NumberFormatException.class );
		assertThatThrownBy( () -> new JsonPrimitive( true ).getAsNumber() ).isInstanceOf( UnsupportedOperationException.class );
		assertThatThrownBy( () -> parsing.parse( "1.9" ).getAsBigInteger() ).isInstanceOf( NumberFormatException.class );
		assertThatThrownBy( () -> parsing.parse( "1e3" ).getAsBigInteger() ).isInstanceOf( NumberFormatException.class );
		JsonArray singleton = new JsonArray();
		singleton.add( fractionalText );
		assertThatThrownBy( singleton::getAsLong ).isInstanceOf( NumberFormatException.class );
		singleton.set( 0, parsing.parse( "1.9" ) );
		assertThat( singleton.getAsLong() ).isEqualTo( 1 );
	}

	@Test
	void snapshotMutableNumbersAndRejectNonJsonNumbers() {
		java.util.concurrent.atomic.AtomicInteger number = new java.util.concurrent.atomic.AtomicInteger( 1 );
		JsonPrimitive value = new JsonPrimitive( number );
		number.set( 2 );
		assertThat( value.getAsInt() ).isEqualTo( 1 );
		assertThatThrownBy( () -> new JsonPrimitive( Double.NaN ) ).isInstanceOf( IllegalArgumentException.class );
		assertThatThrownBy( () -> JsonPrimitive.number( "01" ) ).isInstanceOf( IllegalArgumentException.class );
	}

	@Test
	void diagnosticRenderingEscapesAndRetainsLiteralHtml() {
		JsonObject object = new JsonObject();
		object.addProperty( "\"\\\n", "<>&='é中😀\b\f\r\t\u0000" );
		assertThat( parsing.parse( object.toString() ) ).isEqualTo( object );
		JsonElement loneSurrogate = parsing.parse( "\"\\uD800\"" );
		assertThat( parsing.parse( loneSurrogate.toString() ) ).isEqualTo( loneSurrogate );
		assertThat( object.toString() ).contains( "<>&='", "\\n", "\\u0000" );
	}

	@Test
	void arraysNormalizeJavaNullAndUseSingletonScalarNavigation() {
		JsonArray array = new JsonArray();
		array.add( (JsonElement) null );
		assertThat( array.get( 0 ) ).isSameAs( JsonNull.INSTANCE );
		array.set( 0, new JsonPrimitive( 42 ) );
		assertThat( array.getAsInt() ).isEqualTo( 42 );
		array.add( 43 );
		assertThatThrownBy( array::getAsInt ).isInstanceOf( IllegalStateException.class );
		array.remove( 1 );
		assertThat( array.deepCopy() ).isEqualTo( array );
	}
}
