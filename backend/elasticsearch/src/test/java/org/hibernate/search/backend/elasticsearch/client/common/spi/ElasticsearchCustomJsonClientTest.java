package org.hibernate.search.backend.elasticsearch.client.common.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.ElasticsearchJsonBody;
import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonProvider;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;
import org.hibernate.search.util.json.spi.JsonParsing;

import org.junit.jupiter.api.Test;

class ElasticsearchCustomJsonClientTest {
	@Test
	@SuppressWarnings("deprecation")
	void customFactoryUsesProviderAndNativeStreamingCarriers() {
		// Existing factory lifecycle inputs remain unchanged; only its JSON infrastructure parameter changes.
		ElasticsearchClientFactory factory =
				(resolver, properties, threads, prefix, timeout, provider) -> new Client( provider );
		try ( var client = factory.create( null, null, null, "custom", null, JsonProviderHelper.create( false ) ) ) {
			var request = ElasticsearchRequest.post().bodyJson( new JsonParsing()
					.parse( "{\"value\":\"é😀\",\"number\":9007199254740993,\"null\":null}" ).asObject() ).build();
			var response = client.submit( request ).join();
			assertThat( response.bodyJson().get( "value" ).asString() ).isEqualTo( "é😀" );
			assertThat( response.bodyJson().get( "number" ).longValue() ).isEqualTo( 9007199254740993L );
			assertThat( response.bodyJson().get( "null" ).isNull() ).isTrue();
			assertThat( response.body().get( "number" ).getAsLong() ).isEqualTo( 9007199254740993L );
		}
	}

	private static final class Client implements ElasticsearchClientImplementor {
		private final JsonProvider provider;

		private Client(JsonProvider provider) {
			this.provider = provider;
		}

		public CompletableFuture<ElasticsearchResponse> submit(ElasticsearchRequest request) {
			try {
				ByteArrayOutputStream wire = new ByteArrayOutputStream();
				try ( var cursor = ElasticsearchJsonBody.ndjson( request.bodyPartsJson() ).openCursor() ) {
					ByteBuffer buffer = ByteBuffer.allocate( 5 );
					while ( !cursor.isComplete() ) {
						cursor.write( buffer );
						wire.write( buffer.array(), 0, buffer.position() );
						buffer.clear();
					}
				}
				// Simulated response bytes arrive in another supported charset and fragmented buffers.
				byte[] responseBytes = wire.toString( StandardCharsets.UTF_8 ).getBytes( StandardCharsets.UTF_16LE );
				try ( var parser = new JsonParsing().createParser( StandardCharsets.UTF_16LE ) ) {
					for ( int offset = 0; offset < responseBytes.length; offset++ ) {
						ByteBuffer fragment = ByteBuffer.wrap( responseBytes, offset, 1 );
						while ( fragment.hasRemaining() ) {
							parser.feed( fragment );
						}
					}
					var tree = provider.getCodec().decode( parser.endOfInput(), tools.jackson.databind.node.ObjectNode.class );
					return CompletableFuture.completedFuture( ElasticsearchResponse.withJsonBody( "custom", 200, "ok", tree ) );
				}
			}
			catch (RuntimeException e) {
				return CompletableFuture.failedFuture( e );
			}
		}

		public <T> T unwrap(Class<T> type) {
			return type.cast( this );
		}

		public void close() {
		}
	}
}
