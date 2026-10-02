package org.hibernate.search.backend.elasticsearch.gson.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.search.util.common.AssertionFailure;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

class JsonAccessorCharacterizationTest {

	private static final JsonAccessor<Long> VALUE = JsonAccessor.root().property( "parent" ).property( "value" ).asLong();

	@Test
	void intermediateAndTerminalSingletonArrays() {
		assertThat( VALUE.get( tree( "{\"parent\":[[{\"value\":[[42]]}]]}" ) ) ).contains( 42L );
		assertThat( VALUE.get( tree( "{\"parent\":[]}" ) ) ).isEmpty();
		assertThat( VALUE.get( tree( "{\"parent\":[null]}" ) ) ).isEmpty();
		assertThat( VALUE.get( tree( "{\"parent\":{\"value\":[[]]}}" ) ) ).isEmpty();
		assertThatThrownBy( () -> VALUE.get( tree( "{\"parent\":[{},{}]}" ) ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class )
				.hasMessageContaining( ".parent(JsonObject)" );
		assertThatThrownBy( () -> VALUE.get( tree( "{\"parent\":{\"value\":[1,2]}}" ) ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class )
				.hasMessageContaining( ".parent(JsonObject).value(JsonPrimitive(Long))" );
	}

	@Test
	void writesTraverseSingletonObjectsButReplaceSelectedScalarLocation() {
		JsonObject root = tree( "{\"parent\":[[{\"value\":[[42]]}]]}" );
		VALUE.set( root, 7L );
		assertThat( root ).isEqualTo( tree( "{\"parent\":[[{\"value\":7}]]}" ) );
		VALUE.set( root, null );
		assertThat( root ).isEqualTo( tree( "{\"parent\":[[{\"value\":null}]]}" ) );
	}

	@Test
	void creationDistinguishesRawNullFromAdaptedAbsence() {
		AtomicInteger calls = new AtomicInteger();
		for ( String json : new String[] { "{}", "{\"parent\":null}", "{\"parent\":{\"value\":null}}" } ) {
			JsonObject root = tree( json );
			assertThat( VALUE.get( root ) ).isEmpty();
			assertThat( VALUE.getOrCreate( root, () -> {
				calls.incrementAndGet();
				return 9L;
			} ) ).isEqualTo( 9L );
			assertThat( root ).isEqualTo( tree( "{\"parent\":{\"value\":9}}" ) );
		}
		assertThat( calls.get() ).isEqualTo( 3 );
		JsonObject emptyTerminal = tree( "{\"parent\":{\"value\":[]}}" );
		assertThat( VALUE.getOrCreate( emptyTerminal, () -> {
			calls.incrementAndGet();
			return 10L;
		} ) ).isNull();
		assertThat( calls.get() ).isEqualTo( 3 );
		assertThat( emptyTerminal ).isEqualTo( tree( "{\"parent\":{\"value\":[]}}" ) );
		// An existing empty/null singleton array is not replaced to create an intermediate object.
		for ( String json : new String[] { "{\"parent\":[]}", "{\"parent\":[null]}" } ) {
			assertThatThrownBy( () -> VALUE.set( tree( json ), 1L ) ).isInstanceOf( NullPointerException.class );
			assertThatThrownBy( () -> VALUE.getOrCreate( tree( json ), () -> 1L ) )
					.isInstanceOf( NullPointerException.class );
		}
	}

	@Test
	void getOrCreatePreservesExistingAdaptedContainer() {
		JsonObject root = tree( "{\"parent\":[[{\"value\":[[42]]}]]}" );
		assertThat( VALUE.getOrCreate( root, () -> {
			throw new AssertionFailure( "Existing value must not call supplier" );
		} ) ).isEqualTo( 42L );
		assertThat( root ).isEqualTo( tree( "{\"parent\":[[{\"value\":[[42]]}]]}" ) );
		JsonObject nullSingleton = tree( "{\"parent\":{\"value\":[[null]]}}" );
		assertThat( VALUE.getOrCreate( nullSingleton, () -> {
			throw new AssertionFailure( "Existing null singleton must not call supplier" );
		} ) ).isNull();
		assertThat( nullSingleton ).isEqualTo( tree( "{\"parent\":{\"value\":[[null]]}}" ) );
	}

	@Test
	void rawNullArrayIdentityAndIndexedCreation() {
		JsonObject root = tree( "{\"value\":null,\"array\":[[1]]}" );
		assertThat( JsonAccessor.root().property( "missing" ).get( root ) ).isEmpty();
		assertThat( JsonAccessor.root().property( "value" ).get( root ) ).containsSame( JsonNull.INSTANCE );
		JsonArray array = root.getAsJsonArray( "array" );
		assertThat( JsonAccessor.root().property( "array" ).asArray().get( root ) ).containsSame( array );
		JsonAccessor.root().property( "created" ).element( 2 ).property( "name" ).asString().set( root, "x" );
		assertThat( root.get( "created" ) ).isEqualTo( JsonParser.parseString( "[null,null,{\"name\":\"x\"}]" ) );
		assertThat( JsonAccessor.root().property( "array" ).element( 10 ).get( root ) ).isEmpty();
		assertThatThrownBy( () -> JsonAccessor.root().property( "array" ).element( -1 ).get( root ) )
				.isInstanceOf( IndexOutOfBoundsException.class );
	}

	@Test
	void numericNarrowingAndTypeChecks() {
		JsonObject root = tree( "{\"parent\":{\"value\":1.9},\"big\":4294967297,\"string\":\"42\",\"boolean\":true}" );
		assertThat( VALUE.get( root ) ).contains( 1L );
		assertThat( JsonAccessor.root().property( "big" ).asInteger().get( root ) ).contains( 1 );
		assertThatThrownBy( () -> JsonAccessor.root().property( "string" ).asLong().get( root ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class );
		assertThatThrownBy( () -> JsonAccessor.root().property( "boolean" ).asString().get( root ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class );
		assertThat( JsonElementTypes.BIG_INTEGER.fromElement( new JsonPrimitive( "42" ) ) ).isEqualTo( 42 );
	}

	@Test
	void definitionsAreReusableAcrossTrees() {
		JsonObject first = new JsonObject();
		JsonObject second = new JsonObject();
		VALUE.set( first, 1L );
		VALUE.set( second, 2L );
		assertThat( VALUE.get( first ) ).contains( 1L );
		assertThat( VALUE.get( second ) ).contains( 2L );
	}

	@Test
	void rootAndWriteErrors() {
		assertThatThrownBy( () -> JsonAccessor.root().get( null ) )
				.isInstanceOf( AssertionFailure.class ).hasMessageContaining( "null root" );
		assertThatThrownBy( () -> JsonAccessor.root().set( new JsonObject(), new JsonObject() ) )
				.isInstanceOf( UnsupportedOperationException.class );
		assertThatThrownBy( () -> VALUE.set( tree( "{\"parent\":42}" ), 1L ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class ).hasMessageContaining( ".parent(JsonObject)" );
	}

	private static JsonObject tree(String json) {
		return JsonParser.parseString( json ).getAsJsonObject();
	}
}
