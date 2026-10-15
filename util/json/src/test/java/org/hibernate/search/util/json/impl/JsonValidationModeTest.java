package org.hibernate.search.util.json.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.JsonTreeValidation;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class JsonValidationModeTest {
	@Test
	void arbitraryExternalTreesAreAlwaysCheckedAndValidationIsNeverCached() {
		var tree = new ObjectNode( JsonNodeFactory.instance );
		JsonBodies.of( tree );
		tree.set( "cycle", tree );
		assertThatThrownBy( () -> JsonBodies.of( tree ) ).isInstanceOf( IllegalArgumentException.class );
		assertThatThrownBy( () -> JsonBodies.openCursor( tree ) ).isInstanceOf( IllegalArgumentException.class );
		assertThatThrownBy( () -> JsonBodies.ndjson( List.of( tree ) ) ).isInstanceOf( IllegalArgumentException.class );
		assertThatThrownBy( () -> JsonBodies.of( DoubleNode.valueOf( Double.NaN ) ) )
				.isInstanceOf( IllegalArgumentException.class );
	}

	@Test
	void disabledInternalCheckDoesNotVisitOrAllocateTraversalForItsInput() {
		var backing = new CountingMap();
		backing.put( "value", new JsonParsing().parse( "42" ) );
		var tree = new ObjectNode( JsonNodeFactory.instance, backing );
		JsonTreeValidation.validateInternal( tree );
		assertThat( backing.visits ).isEqualTo( JsonTreeValidation.isInternalValidationEnabled() ? 1 : 0 );
	}

	@Test
	void deepWriterUsesNoCycleMapWhenDisabledAndReleasesBothModes() {
		ObjectNode tree = new ObjectNode( JsonNodeFactory.instance );
		ObjectNode current = tree;
		for ( int depth = 0; depth < 48; depth++ ) {
			var child = new ObjectNode( JsonNodeFactory.instance );
			current.set( "child", child );
			current = child;
		}
		current.put( "value", "x".repeat( 10000 ) );
		try ( var cursor = new Utf8JsonWriteCursor( tree ) ) {
			cursor.write( ByteBuffer.allocate( 1024 ) );
			assertThat( cursor.retainedCycleEntries() > 0 ).isEqualTo( JsonTreeValidation.isInternalValidationEnabled() );
			if ( !JsonTreeValidation.isInternalValidationEnabled() ) {
				assertThat( field( cursor, "workspace" ) ).isNull();
			}
		}
	}

	@Test
	void debugCycleTrackingIsOptInAndExternalCycleChecksAreIndependent() {
		if ( JsonTreeValidation.isInternalValidationEnabled() ) {
			var cycle = new ArrayNode( JsonNodeFactory.instance );
			cycle.add( cycle );
			try ( var cursor = new Utf8JsonWriteCursor( cycle ) ) {
				assertThatThrownBy( () -> cursor.write( ByteBuffer.allocate( 32 ) ) )
						.isInstanceOf( IllegalArgumentException.class );
				assertThat( cursor.retainedCycleEntries() ).isZero();
			}
		}
	}

	@Test
	void internalValidOutputParseTinyBuffersAndReplayWorkInEitherMode() throws Exception {
		var parsing = new JsonParsing();
		var source = parsing.parse( "{\"unicode\":\"é😀\\n\",\"integer\":9007199254740993,\"zero\":-0.0}" );
		var body = JsonBodies.ndjsonInternal( List.of( source ) );
		for ( int replay = 0; replay < 2; replay++ ) {
			var out = new ByteArrayOutputStream();
			try ( var cursor = body.openCursor() ) {
				while ( !cursor.isComplete() ) {
					var bytes = ByteBuffer.allocate( 1 );
					cursor.write( bytes );
					out.write( bytes.array(), 0, bytes.position() );
				}
			}
			assertThat( parsing.parse( out.toString( StandardCharsets.UTF_8 ) ) ).isEqualTo( source );
		}
	}

	private static Object field(Object value, String name) {
		try {
			var field = value.getClass().getDeclaredField( name );
			field.setAccessible( true );
			return field.get( value );
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionFailure( "Cannot inspect writer state", e );
		}
	}

	private static final class CountingMap extends LinkedHashMap<String, JsonNode> {
		private int visits;

		@Override
		public Set<Map.Entry<String, JsonNode>> entrySet() {
			visits++;
			return super.entrySet();
		}
	}
}
