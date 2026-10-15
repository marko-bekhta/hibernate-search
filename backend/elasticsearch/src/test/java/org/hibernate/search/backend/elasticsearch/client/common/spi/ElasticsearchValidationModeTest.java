package org.hibernate.search.backend.elasticsearch.client.common.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.hibernate.search.backend.elasticsearch.json.impl.JsonHandlerBridge;
import org.hibernate.search.backend.elasticsearch.json.jackson.JacksonJsonHandlers;
import org.hibernate.search.util.json.spi.JsonTreeValidation;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class ElasticsearchValidationModeTest {
	@Test
	void applicationAndCustomClientIngressRemainValidatedInProductionMode() {
		var cycle = new ObjectNode( JsonNodeFactory.instance );
		cycle.set( "cycle", cycle );
		assertThatThrownBy( () -> ElasticsearchRequest.post().bodyJson( cycle ) )
				.isInstanceOf( IllegalArgumentException.class );
		assertThatThrownBy( () -> ElasticsearchResponse.withJsonBody( "custom", 200, "ok", cycle ) )
				.isInstanceOf( IllegalArgumentException.class );
		assertThatThrownBy( () -> JsonHandlerBridge.importObject( JacksonJsonHandlers.object(), cycle, "user input" ) )
				.hasMessageContaining( "user input" );
	}

	@Test
	void internalRequestWrappingAndTrustedExportsDoNotRescanWhenDisabled() {
		var fields = new CountingMap();
		fields.put( "value", IntNode.valueOf( 42 ) );
		var tree = new ObjectNode( JsonNodeFactory.instance, fields );
		var request = ElasticsearchRequest.post().bodyJsonFromInternal( tree ).build();
		assertThat( fields.visits ).isEqualTo( JsonTreeValidation.isInternalValidationEnabled() ? 1 : 0 );
		fields.visits = 0;
		assertThat( request.bodyParts( JacksonJsonHandlers.object() ).get( 0 ) ).isSameAs( tree );
		assertThat( fields.visits ).isEqualTo( JsonTreeValidation.isInternalValidationEnabled() ? 1 : 0 );
	}

	@Test
	void internallyParsedResponseWrappingAndExportsDoNotRescanWhenDisabled() {
		var fields = new CountingMap();
		fields.put( "value", IntNode.valueOf( 42 ) );
		var tree = new ObjectNode( JsonNodeFactory.instance, fields );
		var response = ElasticsearchResponse.withInternalJsonBody( "builtin", 200, "ok", tree );
		assertThat( fields.visits ).isEqualTo( JsonTreeValidation.isInternalValidationEnabled() ? 1 : 0 );
		fields.visits = 0;
		assertThat( response.body( JacksonJsonHandlers.object() ) ).isSameAs( tree );
		assertThat( fields.visits ).isEqualTo( JsonTreeValidation.isInternalValidationEnabled() ? 1 : 0 );
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
