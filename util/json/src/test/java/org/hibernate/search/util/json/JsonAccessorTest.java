package org.hibernate.search.util.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.json.spi.JsonAccessor;
import org.hibernate.search.util.json.spi.JsonElementTypes;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.UnexpectedJsonElementTypeException;

import org.junit.jupiter.api.Test;

class JsonAccessorTest {
	private static final JsonAccessor<Long> STRICT = JsonAccessor.root().property( "parent" ).property( "value" ).asLong();
	private static final JsonAccessor<Long> ADAPTED = JsonAccessor.root().property( "parent" ).unwrapSingletonArrays()
			.property( "value" ).unwrapSingletonArrays().asLong();

	private static JsonObject tree(String json) {
		return new JsonParsing().parse( json ).getAsJsonObject();
	}

	@Test
	void strictAtIntermediateAndTerminalLocations() {
		assertThat( STRICT.get( tree( "{\"parent\":{\"value\":42}}" ) ) ).contains( 42L );
		assertThatThrownBy( () -> STRICT.get( tree( "{\"parent\":[{\"value\":42}]}" ) ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class ).hasMessageContaining( ".parent(JsonObject)" );
		assertThatThrownBy( () -> STRICT.get( tree( "{\"parent\":{\"value\":[42]}}" ) ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class )
				.hasMessageContaining( ".parent(JsonObject).value(JsonPrimitive(Long))" );
		assertThatThrownBy( () -> STRICT.get( tree( "{\"parent\":{\"value\":\"42\"}}" ) ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class );
	}

	@Test
	void explicitAdaptationAtBothLocations() {
		assertThat( ADAPTED.get( tree( "{\"parent\":[[{\"value\":[[42]]}]]}" ) ) ).contains( 42L );
		for ( String json : new String[] { "{\"parent\":[]}", "{\"parent\":[null]}", "{\"parent\":{\"value\":[[]]}}" } ) {
			assertThat( ADAPTED.get( tree( json ) ) ).isEmpty();
		}
		assertThatThrownBy( () -> ADAPTED.get( tree( "{\"parent\":[{},{}]}" ) ) )
				.isInstanceOf( IllegalStateException.class ).hasMessageContaining( ".parent" );
		assertThatThrownBy( () -> ADAPTED.get( tree( "{\"parent\":{\"value\":[1,2]}}" ) ) )
				.isInstanceOf( IllegalStateException.class ).hasMessageContaining( ".value" );
	}

	@Test
	void writesTraverseWrappersAndReplaceTheSelectedLocation() {
		JsonObject root = tree( "{\"parent\":[[{\"value\":[[42]]}]]}" );
		ADAPTED.set( root, 7L );
		assertThat( root ).isEqualTo( tree( "{\"parent\":[[{\"value\":7}]]}" ) );
		ADAPTED.set( root, null );
		assertThat( root ).isEqualTo( tree( "{\"parent\":[[{\"value\":null}]]}" ) );
	}

	@Test
	void getOrCreatePreservesRawPresenceAndAdaptedAbsence() {
		AtomicInteger calls = new AtomicInteger();
		for ( String json : new String[] { "{}", "{\"parent\":null}", "{\"parent\":{\"value\":null}}" } ) {
			JsonObject root = tree( json );
			assertThat( ADAPTED.getOrCreate( root, () -> {
				calls.incrementAndGet();
				return 9L;
			} ) ).isEqualTo( 9L );
			assertThat( root ).isEqualTo( tree( "{\"parent\":{\"value\":9}}" ) );
		}
		assertThat( calls ).hasValue( 3 );
		for ( String json : new String[] { "{\"parent\":{\"value\":[]}}", "{\"parent\":{\"value\":[[null]]}}" } ) {
			JsonObject root = tree( json );
			assertThat( ADAPTED.getOrCreate( root,
					() -> { throw new AssertionFailure( "Raw present value cannot call supplier" ); } ) ).isNull();
			assertThat( root ).isEqualTo( tree( json ) );
		}
		JsonObject existing = tree( "{\"parent\":[[{\"value\":[[42]]}]]}" );
		assertThat( ADAPTED.getOrCreate( existing, () -> { throw new AssertionFailure( "Existing value" ); } ) )
				.isEqualTo( 42 );
		assertThat( existing ).isEqualTo( tree( "{\"parent\":[[{\"value\":[[42]]}]]}" ) );
	}

	@Test
	void absentAdaptedIntermediateWritesFailDescriptivelyWithoutReplacingWrappers() {
		for ( String json : new String[] { "{\"parent\":[]}", "{\"parent\":[null]}" } ) {
			JsonObject root = tree( json );
			assertThatThrownBy( () -> ADAPTED.set( root, 1L ) ).isInstanceOf( IllegalStateException.class )
					.hasMessageContaining( "Cannot traverse an absent adapted parent" ).hasMessageContaining( ".parent" );
			assertThatThrownBy( () -> ADAPTED.getOrCreate( root, () -> 1L ) ).isInstanceOf( IllegalStateException.class );
			assertThat( root ).isEqualTo( tree( json ) );
		}
	}

	@Test
	void nullMissingArrayIdentityIndexCreationAndReuse() {
		JsonObject root = tree( "{\"value\":null,\"array\":[[1]]}" );
		assertThat( JsonAccessor.root().property( "missing" ).get( root ) ).isEmpty();
		assertThat( JsonAccessor.root().property( "value" ).get( root ) ).contains( JsonNull.INSTANCE );
		assertThat( JsonAccessor.root().property( "array" ).asArray().get( root ) )
				.containsSame( root.getAsJsonArray( "array" ) );
		JsonAccessor<String> indexed = JsonAccessor.root().property( "created" ).element( 2 ).property( "name" ).asString();
		indexed.set( root, "first" );
		assertThat( root.get( "created" ) ).isEqualTo( new JsonParsing().parse( "[null,null,{\"name\":\"first\"}]" ) );
		JsonObject second = new JsonObject();
		indexed.set( second, "second" );
		assertThat( indexed.get( root ) ).contains( "first" );
		assertThat( indexed.get( second ) ).contains( "second" );
		assertThat( JsonAccessor.root().property( "array" ).element( 5 ).get( root ) ).isEmpty();
		assertThatThrownBy( () -> JsonAccessor.root().property( "array" ).element( -1 ).get( root ) )
				.isInstanceOf( IndexOutOfBoundsException.class );
	}

	@Test
	void scalarTypesAndNumericNarrowingAreIndependentOfArrayAdaptation() {
		JsonObject root = tree( "{\"parent\":{\"value\":1.9},\"integer\":4294967297,\"text\":\"123\",\"boolean\":true}" );
		assertThat( STRICT.get( root ) ).contains( 1L );
		assertThat( JsonAccessor.root().property( "integer" ).asInteger().get( root ) ).contains( 1 );
		assertThat( JsonElementTypes.BIG_INTEGER.fromElement( root.get( "text" ) ) )
				.isEqualTo( new java.math.BigInteger( "123" ) );
		assertThat( JsonAccessor.root().property( "boolean" ).asBoolean().get( root ) ).contains( true );
		assertThatThrownBy( () -> JsonAccessor.root().property( "boolean" ).asString().get( root ) )
				.isInstanceOf( UnexpectedJsonElementTypeException.class );
		assertThat( JsonAccessor.root().property( "integer" ).asFloat().get( root ) ).contains( 4294967297F );
		assertThat( JsonAccessor.root().property( "integer" ).asDouble().get( root ) ).contains( 4294967297D );
	}

	@Test
	void rootAndArrayMutationContracts() {
		assertThatThrownBy( () -> JsonAccessor.root().get( null ) ).isInstanceOf( AssertionFailure.class );
		assertThatThrownBy( () -> JsonAccessor.root().set( new JsonObject(), new JsonObject() ) )
				.isInstanceOf( UnsupportedOperationException.class );
		JsonObject root = new JsonObject();
		var array = JsonAccessor.root().property( "array" ).asArray();
		array.addElementIfAbsent( root, new JsonPrimitive( 1 ) );
		array.addElementIfAbsent( root, new JsonPrimitive( 1.0 ) );
		assertThat( root.getAsJsonArray( "array" ) ).hasSize( 1 );
		JsonObject absent = tree( "{\"array\":[]}" );
		assertThatThrownBy( () -> JsonAccessor.root().property( "array" ).unwrapSingletonArrays().asArray()
				.addElementIfAbsent( absent, new JsonPrimitive( 1 ) ) ).isInstanceOf( IllegalStateException.class )
				.hasMessageContaining( "Cannot modify an absent adapted array" );
		assertThat( absent ).isEqualTo( tree( "{\"array\":[]}" ) );
		JsonAccessor.root().property( "" ).asString().set( root, "empty name" );
		assertThat( root.get( "" ).getAsString() ).isEqualTo( "empty name" );
	}
}
