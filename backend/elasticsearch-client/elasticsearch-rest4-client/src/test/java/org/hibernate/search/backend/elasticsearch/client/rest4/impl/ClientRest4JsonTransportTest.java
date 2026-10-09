package org.hibernate.search.backend.elasticsearch.client.rest4.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;
import org.hibernate.search.util.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.http.ContentTooLongException;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.BasicHttpContext;

class ClientRest4JsonTransportTest {
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 8192 })
	void responseFragmentationCharsetHeadersGzipAndReplay(int capacity) throws Exception {
		String text = "{\"é😀\":\"" + "é😀".repeat( 5000 ) + "\"}";
		byte[] bytes = text.getBytes( StandardCharsets.UTF_16LE );
		for ( boolean gzip : List.of( false, true ) ) {
			byte[] input = bytes;
			if ( gzip ) {
				ByteArrayOutputStream compressed = new ByteArrayOutputStream();
				try ( GZIPOutputStream stream = new GZIPOutputStream( compressed ) ) {
					stream.write( bytes );
				}
				input = compressed.toByteArray();
			}
			var consumer = new ClientRest4JsonResponseConsumer( input.length );
			BasicHttpResponse response = response( 200, "application/json; charset=UTF-16LE" );
			response.addHeader( "Warning", "299 test \"warning\"" );
			if ( gzip ) {
				( (BasicHttpEntity) response.getEntity() ).setContentEncoding( "gzip" );
				response.addHeader( "Content-Encoding", "gzip" );
			}
			consumer.responseReceived( response );
			consumer.consumeContent( new Decoder( ByteBuffer.allocate( 0 ) ), StubIOControl.INSTANCE );
			for ( int offset = 0; offset < input.length; offset += capacity ) {
				consumer.consumeContent(
						new Decoder( ByteBuffer.wrap( input, offset, Math.min( capacity, input.length - offset ) ) ),
						StubIOControl.INSTANCE );
			}
			assertThat( consumer.getResult() ).isNull();
			consumer.responseCompleted( new BasicHttpContext() );
			assertThat( consumer.getException() ).isNull();
			var completed = consumer.getResult();
			assertThat( completed.getFirstHeader( "Warning" ).getValue() ).contains( "warning" );
			assertThat( completed.getStatusLine().getReasonPhrase() ).isEqualTo( "fine" );
			if ( gzip ) {
				assertThat( completed.getFirstHeader( "Content-Encoding" ) ).isNull();
			}
			var entity = (ClientRest4JsonResponseConsumer.Entity) completed.getEntity();
			assertThat( entity.body().tree().getAsJsonObject().get( "é😀" ).getAsString() ).isEqualTo( "é😀".repeat( 5000 ) );
			for ( int attempt = 0; attempt < 2; ++attempt ) {
				try ( var stream = entity.getContent() ) {
					assertThat( new String( stream.readAllBytes(), StandardCharsets.UTF_8 ) ).isEqualTo( text );
				}
			}
		}
	}

	@Test
	void completionCanReleaseTheRequestProducerImmediately() throws Exception {
		var entity = new ClientRest4JsonHttpEntity( JsonProviderHelper.create( true ).getCodec(),
				List.of( new JsonObject() ) );
		var output = new ByteArrayOutputStream();
		entity.produceContent( new org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.ContentEncoder() {
			@Override
			public int write(ByteBuffer source) {
				int count = source.remaining();
				while ( source.hasRemaining() ) {
					output.write( source.get() );
				}
				return count;
			}

			@Override
			public void complete() {
				entity.close();
			}

			@Override
			public boolean isCompleted() {
				return false;
			}
		} );
		assertThat( output.toString( StandardCharsets.UTF_8 ) ).isEqualTo( "{}\n" );
		try ( var stream = entity.getContent() ) {
			assertThat( stream.readAllBytes() ).isEqualTo( "{}\n".getBytes( StandardCharsets.UTF_8 ) );
		}
	}

	@Test
	void cancellationClosesIndependentClientOwnedRequestStreams() throws Exception {
		JsonObject document = new JsonObject();
		document.addProperty( "value", "é😀".repeat( 10_000 ) );
		var entity = new ClientRest4JsonHttpEntity( JsonProviderHelper.create( true ).getCodec(), List.of( document ) );
		try ( var first = entity.getContent(); var second = entity.getContent() ) {
			assertThat( first.read() ).isEqualTo( '{' );
			assertThat( second.read() ).isEqualTo( '{' );
			entity.cancel();
			entity.cancel();
			assertThat( entity.isRepeatable() ).isFalse();
			assertThatThrownBy( first::read ).isInstanceOf( java.io.IOException.class );
			assertThatThrownBy( second::read ).isInstanceOf( java.io.IOException.class );
			assertThatThrownBy( entity::getContent ).hasMessageContaining( "cancelled" );
			assertThatThrownBy( () -> entity.produceContent( new Sink(), new Sink() ) )
					.hasMessageContaining( "cancelled" );
			var field =
					org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.JsonHttpEntityContentProvider.class
							.getDeclaredField( "cursor" );
			field.setAccessible( true );
			assertThat( field.get( entity ) ).isNull();
		}
	}

	@Test
	void requestCancellationEagerlyReleasesPerAttemptState() throws Exception {
		var scope = new org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseConsumerScope();
		var consumer = new ClientRest4JsonResponseConsumer( 100, scope );
		consumer.responseReceived( response( 200, "application/json" ) );
		consumer.consumeContent( new Decoder( ByteBuffer.wrap( "{".getBytes( StandardCharsets.UTF_8 ) ) ),
				StubIOControl.INSTANCE );
		var field = ClientRest4JsonResponseConsumer.class.getDeclaredField( "body" );
		field.setAccessible( true );
		var active =
				(org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody) field.get( consumer );
		scope.cancel();
		scope.cancel();
		assertThat( field.get( consumer ) ).isNull();
		assertThatThrownBy( active::throwIfFailed ).hasMessageContaining( "cancelled" );
	}

	@Test
	void localHttpParsesBeforeResponseCompletion() throws Exception {
		var parsed = new java.util.concurrent.CountDownLatch( 1 );
		var finish = new java.util.concurrent.CountDownLatch( 1 );
		var observed = new java.util.concurrent.atomic.AtomicReference<
				org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody>();
		var bodyField = ClientRest4JsonResponseConsumer.class.getDeclaredField( "body" );
		bodyField.setAccessible( true );
		var consumer = org.mockito.Mockito.spy( new ClientRest4JsonResponseConsumer( 1000 ) );
		org.mockito.Mockito.doAnswer( invocation -> {
			invocation.callRealMethod();
			observed.set( (org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody) bodyField
					.get( consumer ) );
			parsed.countDown();
			return null;
		} ).when( consumer ).consumeContent( org.mockito.ArgumentMatchers.any( ContentDecoder.class ),
				org.mockito.ArgumentMatchers.any( IOControl.class ) );
		try ( var server = new org.hibernate.search.util.impl.test.http.StreamingHttpServer( exchange -> {
			exchange.headers( 200, "application/json", "" );
			exchange.chunk( "<proxy>".getBytes( StandardCharsets.UTF_8 ) );
			assertThat( finish.await( 10, java.util.concurrent.TimeUnit.SECONDS ) ).isTrue();
			exchange.end();
		} ); var client = org.apache.http.impl.nio.client.HttpAsyncClients.createDefault() ) {
			client.start();
			var future = client.execute( org.apache.http.nio.client.methods.HttpAsyncMethods.createGet( server.uri() ),
					consumer, null );
			try {
				assertThat( parsed.await( 10, java.util.concurrent.TimeUnit.SECONDS ) ).isTrue();
				assertThat( future.isDone() ).isFalse();
				assertThatThrownBy( () -> observed.get().throwIfFailed() )
						.isInstanceOf( org.hibernate.search.util.json.spi.JsonParseException.class );
			}
			finally {
				finish.countDown();
			}
			assertThat( future.get( 10, java.util.concurrent.TimeUnit.SECONDS ).getStatusLine().getStatusCode() )
					.isEqualTo( 200 );
		}
	}

	@Test
	void requestEncodedStorageStaysBoundedAcrossScalarGrowth() throws Exception {
		var storage =
				org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.JsonHttpEntityContentProvider.class
						.getDeclaredField( "pending" );
		storage.setAccessible( true );
		var cursor =
				org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.JsonHttpEntityContentProvider.class
						.getDeclaredField( "cursor" );
		cursor.setAccessible( true );
		for ( int size : new int[] { 100, 100_000, 1_000_000 } ) {
			JsonObject document = new JsonObject();
			document.addProperty( "value", "x".repeat( size ) );
			var entity = new ClientRest4JsonHttpEntity( JsonProviderHelper.create( true ).getCodec(), List.of( document ) );
			Sink sink = new Sink();
			for ( int step = 0; step < 3 && !sink.ended; ++step ) {
				sink.remaining = 1;
				entity.produceContent( sink, sink );
				assertThat( ( (ByteBuffer) storage.get( entity ) ).capacity() ).isEqualTo( 8192 );
				assertThat( sink.output.size() ).isEqualTo( step + 1 );
			}
			entity.close();
			assertThat( cursor.get( entity ) ).isNull();
			assertThat( storage.get( entity ) ).isNull();
		}
	}

	@Test
	void malformedResponsesDeferFailureButEnforceLimits() throws Exception {
		for ( String text : List.of( "<proxy>unavailable", "{", "{}{}" ) ) {
			var consumer = new ClientRest4JsonResponseConsumer( 100 );
			consumer.responseReceived( response( 503, "application/json" ) );
			consumer.consumeContent( new Decoder( ByteBuffer.wrap( text.getBytes( StandardCharsets.UTF_8 ) ) ),
					StubIOControl.INSTANCE );
			consumer.responseCompleted( new BasicHttpContext() );
			assertThat( consumer.getException() ).isNull();
			assertThat( consumer.getResult().getStatusLine().getStatusCode() ).isEqualTo( 503 );
			var entity = (ClientRest4JsonResponseConsumer.Entity) consumer.getResult().getEntity();
			assertThatThrownBy( () -> entity.body().tree() ).isInstanceOf( RuntimeException.class );
			try ( var stream = entity.getContent() ) {
				assertThat( new String( stream.readAllBytes(), StandardCharsets.UTF_8 ) ).isEqualTo( text );
			}
		}
		var consumer = new ClientRest4JsonResponseConsumer( 3 );
		consumer.responseReceived( response( 503, "application/json" ) );
		consumer.consumeContent( new Decoder( ByteBuffer.wrap( new byte[] { 60 } ) ), StubIOControl.INSTANCE );
		assertThatThrownBy(
				() -> consumer.consumeContent( new Decoder( ByteBuffer.wrap( new byte[3] ) ), StubIOControl.INSTANCE ) )
				.isInstanceOf( ContentTooLongException.class );
		consumer.cancel();
		consumer.close();
	}

	@Test
	void emptyAbsentNullAndCancellation() throws Exception {
		for ( String text : List.of( "", " \n\t", "null" ) ) {
			var consumer = new ClientRest4JsonResponseConsumer( 100 );
			consumer.responseReceived( response( 200, "application/json" ) );
			consumer.consumeContent( new Decoder( ByteBuffer.wrap( text.getBytes( StandardCharsets.UTF_8 ) ) ),
					StubIOControl.INSTANCE );
			consumer.responseCompleted( new BasicHttpContext() );
			var entity = (ClientRest4JsonResponseConsumer.Entity) consumer.getResult().getEntity();
			assertThat( JsonProviderHelper.create( true ).getCodec().decode( entity.body().tree(), JsonObject.class ) )
					.isNull();
		}
		var consumer = new ClientRest4JsonResponseConsumer( 100 );
		consumer.responseReceived( new BasicHttpResponse( HttpVersion.HTTP_1_1, 204, "fine" ) );
		consumer.responseCompleted( new BasicHttpContext() );
		assertThat( consumer.getResult().getEntity() ).isNull();
		consumer = new ClientRest4JsonResponseConsumer( 100 );
		consumer.responseReceived( response( 200, "application/json" ) );
		consumer.consumeContent( new Decoder( ByteBuffer.wrap( new byte[] { 123 } ) ), StubIOControl.INSTANCE );
		assertThat( consumer.cancel() ).isTrue();
		consumer.responseCompleted( new BasicHttpContext() );
		assertThat( consumer.getResult() ).isNull();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 8192 })
	void partialZeroWritesWakeupsAndRetry(int capacity) throws Exception {
		var json = JsonProviderHelper.create( true ).getCodec();
		JsonObject object = new JsonObject();
		object.addProperty( "value", "é😀".repeat( 5000 ) );
		var entity = new ClientRest4JsonHttpEntity( json, List.of( object ) );
		Sink sink = new Sink();
		entity.produceContent( sink, sink );
		assertThat( sink.output.size() ).isZero();
		assertThat( sink.wakeups ).isEqualTo( 1 );
		sink.remaining = capacity;
		entity.produceContent( sink, sink );
		entity.close();
		assertThat( sink.output.size() ).isEqualTo( capacity );
		Sink retry = new Sink();
		while ( !retry.ended ) {
			retry.remaining = capacity;
			entity.produceContent( retry, retry );
		}
		assertThat( retry.output.toString( StandardCharsets.UTF_8 ) ).isEqualTo( json.write( object ) + "\n" );
		assertThat( entity.getContentLength() ).isEqualTo( -1 );
		entity.close();
	}

	@Test
	void pinnedRequestCompressionBuffersAndPropagatesFailure() throws Exception {
		var json = JsonProviderHelper.create( true ).getCodec();
		JsonObject document = new JsonObject();
		document.addProperty( "value", "é😀".repeat( 10_000 ) );
		var entity = new ClientRest4JsonHttpEntity( json, List.of( document ) );
		var compressed = new org.elasticsearch.client.RestClient.ContentCompressingEntity( entity );
		for ( int attempt = 0; attempt < 2; ++attempt ) {
			try ( var input = compressed.getContent() ) {
				// Pinned client wrapper materializes the complete compressed payload before returning this stream.
				assertThat( input ).isInstanceOf( java.io.ByteArrayInputStream.class );
				try ( var decoded = new java.util.zip.GZIPInputStream( input ) ) {
					assertThat( new String( decoded.readAllBytes(), StandardCharsets.UTF_8 ) )
							.isEqualTo( json.write( document ) + "\n" );
				}
			}
		}
		assertThat( entity.getContentLength() ).isEqualTo( -1 );
		var failing = org.mockito.Mockito.mock( org.apache.http.HttpEntity.class );
		org.mockito.Mockito.doThrow( new java.io.IOException( "source failed" ) ).when( failing )
				.writeTo( org.mockito.ArgumentMatchers.any( java.io.OutputStream.class ) );
		var failingCompressed = new org.elasticsearch.client.RestClient.ContentCompressingEntity( failing );
		assertThatThrownBy( failingCompressed::getContent ).isInstanceOf( java.io.IOException.class )
				.hasMessageContaining( "source failed" );
	}

	private static BasicHttpResponse response(int code, String contentType) {
		BasicHttpResponse response = new BasicHttpResponse( HttpVersion.HTTP_1_1, code, "fine" );
		BasicHttpEntity entity = new BasicHttpEntity();
		entity.setContentType( contentType );
		entity.setContentLength( -1 );
		response.setEntity( entity );
		return response;
	}

	private static final class Decoder implements ContentDecoder {
		private final ByteBuffer input;

		Decoder(ByteBuffer input) {
			this.input = input;
		}

		@Override
		public int read(ByteBuffer destination) {
			int count = Math.min( destination.remaining(), input.remaining() );
			for ( int i = 0; i < count; ++i ) {
				destination.put( input.get() );
			}
			return count;
		}

		@Override
		public boolean isCompleted() {
			return !input.hasRemaining();
		}
	}

	private static final class Sink implements ContentEncoder, IOControl {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		int remaining;
		int wakeups;
		boolean ended;

		@Override
		public int write(ByteBuffer input) {
			int count = Math.min( remaining, input.remaining() );
			byte[] bytes = new byte[count];
			input.get( bytes );
			output.writeBytes( bytes );
			remaining -= count;
			return count;
		}

		@Override
		public void complete() {
			ended = true;
		}

		@Override
		public boolean isCompleted() {
			return ended;
		}

		@Override
		public void requestOutput() {
			++wakeups;
		}

		@Override
		public void requestInput() {
		}

		@Override
		public void suspendInput() {
		}

		@Override
		public void suspendOutput() {
		}

		@Override
		public void shutdown() {
		}
	}
}
