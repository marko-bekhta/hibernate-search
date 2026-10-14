package org.hibernate.search.backend.elasticsearch.client.rest5.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentTooLongException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class ClientRest5JsonTransportTest {
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 8192 })
	void responseFragmentationCharsetHeadersAndReplay(int size) throws Exception {
		String text = "{\"é😀\":\"" + "é😀".repeat( 5000 ) + "\"}";
		byte[] bytes = text.getBytes( StandardCharsets.UTF_16LE );
		var consumer = new ClientRest5JsonResponseConsumer( bytes.length );
		BasicHttpResponse response = new BasicHttpResponse( 200, "fine" );
		response.addHeader( "Warning", "299 test \"warning\"" );
		Result result = new Result();
		consumer.consumeResponse( response,
				new BasicEntityDetails( -1, ContentType.create( "application/json", StandardCharsets.UTF_16LE ) ),
				HttpCoreContext.create(), result );
		consumer.consume( ByteBuffer.allocate( 0 ) );
		int[] capacity = { 0 };
		consumer.updateCapacity( count -> capacity[0] += count );
		assertThat( capacity[0] ).isEqualTo( 8192 );
		for ( int offset = 0; offset < bytes.length; offset += size ) {
			ByteBuffer fragment = ByteBuffer.allocateDirect( Math.min( size, bytes.length - offset ) );
			fragment.put( bytes, offset, fragment.capacity() ).flip();
			ByteBuffer readOnly = fragment.asReadOnlyBuffer();
			consumer.consume( readOnly );
			assertThat( readOnly.hasRemaining() ).isFalse();
			fragment.clear();
			while ( fragment.hasRemaining() ) {
				fragment.put( (byte) 0 );
			}
		}
		assertThat( result.response ).isNull();
		consumer.streamEnd( List.of() );
		assertThat( result.response.getCode() ).isEqualTo( 200 );
		assertThat( result.response.getReasonPhrase() ).isEqualTo( "fine" );
		assertThat( result.response.getFirstHeader( "Warning" ).getValue() ).contains( "warning" );
		var entity = (ClientRest5JsonResponseConsumer.Entity) result.response.getEntity();
		assertThat( entity.body().tree().asObject().get( "é😀" ).asString() ).isEqualTo( "é😀".repeat( 5000 ) );
		for ( int attempt = 0; attempt < 2; ++attempt ) {
			try ( var input = entity.getContent() ) {
				assertThat( new String( input.readAllBytes(), StandardCharsets.UTF_8 ) ).isEqualTo( text );
			}
		}
		consumer.releaseResources();
		consumer.streamEnd( List.of() );
		assertThat( result.completions ).isEqualTo( 1 );
	}

	@Test
	void explicitGzipLayerWhenClientDecompressionIsDisabled() throws Exception {
		String text = "{\"value\":\"" + "é😀".repeat( 5000 ) + "\"}";
		var compressed = new ByteArrayOutputStream();
		try ( var gzip = new java.util.zip.GZIPOutputStream( compressed ) ) {
			gzip.write( text.getBytes( StandardCharsets.UTF_16LE ) );
		}
		byte[] bytes = compressed.toByteArray();
		var consumer = new ClientRest5JsonResponseConsumer( bytes.length );
		Result result = new Result();
		try ( var details = new org.apache.hc.core5.http.io.entity.ByteArrayEntity( bytes,
				ContentType.create( "application/json", StandardCharsets.UTF_16LE ), "gzip" ) ) {
			consumer.consumeResponse( new BasicHttpResponse( 200 ), details, HttpCoreContext.create(), result );
		}
		for ( byte value : bytes ) {
			consumer.consume( ByteBuffer.wrap( new byte[] { value } ) );
		}
		consumer.streamEnd( List.of() );
		var entity = (ClientRest5JsonResponseConsumer.Entity) result.response.getEntity();
		assertThat( entity.body().tree().asObject().get( "value" ).asString() )
				.isEqualTo( "é😀".repeat( 5000 ) );
		assertThat( entity.getContentEncoding() ).isNull();
	}

	@Test
	void requestCancellationEagerlyReleasesPerAttemptState() throws Exception {
		var scope = new org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseConsumerScope();
		var consumer = new ClientRest5JsonResponseConsumer( 100, scope );
		consumer.consumeResponse( new BasicHttpResponse( 200 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
				HttpCoreContext.create(), new Result() );
		consumer.consume( ByteBuffer.wrap( "{".getBytes( StandardCharsets.UTF_8 ) ) );
		var field = ClientRest5JsonResponseConsumer.class.getDeclaredField( "body" );
		field.setAccessible( true );
		var active =
				(org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody) field.get( consumer );
		scope.cancel();
		scope.cancel();
		assertThat( field.get( consumer ) ).isNull();
		assertThatThrownBy( active::throwIfFailed ).hasMessageContaining( "cancelled" );
	}

	@Test
	void hc5InternalRetryStartsFreshSessionOnNewResponseHead() throws Exception {
		var consumer = new ClientRest5JsonResponseConsumer( 10 );
		Result first = new Result();
		consumer.consumeResponse( new BasicHttpResponse( 200 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
				HttpCoreContext.create(), first );
		consumer.consume( ByteBuffer.wrap( "{\"value\":".getBytes( StandardCharsets.UTF_8 ) ) );
		consumer.releaseResources();
		Result second = new Result();
		consumer.consumeResponse( new BasicHttpResponse( 200 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
				HttpCoreContext.create(), second );
		consumer.consume( ByteBuffer.wrap( "{\"x\":1}".getBytes( StandardCharsets.UTF_8 ) ) );
		consumer.streamEnd( List.of() );
		assertThat( first.completions ).isZero();
		assertThat( ( (ClientRest5JsonResponseConsumer.Entity) second.response.getEntity() ).body().tree().asObject()
				.get( "x" ).intValue() )
				.isEqualTo( 1 );
	}

	@Test
	void localHttpParsesBeforeResponseCompletion() throws Exception {
		var parsed = new java.util.concurrent.CountDownLatch( 1 );
		var finish = new java.util.concurrent.CountDownLatch( 1 );
		var observed = new java.util.concurrent.atomic.AtomicReference<
				org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody>();
		var bodyField = ClientRest5JsonResponseConsumer.class.getDeclaredField( "body" );
		bodyField.setAccessible( true );
		var consumer = org.mockito.Mockito.spy( new ClientRest5JsonResponseConsumer( 1000 ) );
		org.mockito.Mockito.doAnswer( invocation -> {
			invocation.callRealMethod();
			observed.set( (org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody) bodyField
					.get( consumer ) );
			parsed.countDown();
			return null;
		} ).when( consumer ).consume( org.mockito.ArgumentMatchers.any( ByteBuffer.class ) );
		try ( var server = new org.hibernate.search.util.impl.test.http.StreamingHttpServer( exchange -> {
			exchange.headers( 200, "application/json", "" );
			exchange.chunk( "<proxy>".getBytes( StandardCharsets.UTF_8 ) );
			assertThat( finish.await( 10, java.util.concurrent.TimeUnit.SECONDS ) ).isTrue();
			exchange.end();
		} ); var client = org.apache.hc.client5.http.impl.async.HttpAsyncClients.custom().disableAutomaticRetries().build() ) {
			client.start();
			var future = client.execute( org.apache.hc.client5.http.async.methods.SimpleRequestProducer.create(
					org.apache.hc.client5.http.async.methods.SimpleHttpRequest.create( "GET", server.uri() ) ), consumer,
					null );
			try {
				assertThat( parsed.await( 10, java.util.concurrent.TimeUnit.SECONDS ) ).isTrue();
				assertThat( future.isDone() ).isFalse();
				assertThatThrownBy( () -> observed.get().throwIfFailed() )
						.isInstanceOf( org.hibernate.search.util.json.spi.JsonParseException.class );
			}
			finally {
				finish.countDown();
			}
			assertThat( future.get( 10, java.util.concurrent.TimeUnit.SECONDS ).getCode() ).isEqualTo( 200 );
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
			ObjectNode document = JsonNodeFactory.instance.objectNode();
			document.put( "value", "x".repeat( size ) );
			var entity = new ClientRest5JsonHttpEntity( JsonProviderHelper.create( true ).getCodec(), List.of( document ) );
			Sink sink = new Sink();
			for ( int step = 0; step < 3 && !sink.ended; ++step ) {
				sink.remaining = 1;
				entity.produce( sink );
				assertThat( ( (ByteBuffer) storage.get( entity ) ).capacity() ).isEqualTo( 8192 );
				assertThat( sink.output.size() ).isEqualTo( step + 1 );
			}
			entity.close();
			assertThat( cursor.get( entity ) ).isNull();
			assertThat( storage.get( entity ) ).isNull();
		}
	}

	@Test
	void oversizedTransportBatchReplenishesCapacity() throws Exception {
		var consumer = new ClientRest5JsonResponseConsumer( 100_000 );
		consumer.consumeResponse( new BasicHttpResponse( 200 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
				HttpCoreContext.create(), new Result() );
		byte[] input = new byte[65_536];
		java.util.Arrays.fill( input, (byte) 32 );
		consumer.consume( ByteBuffer.wrap( input ) );
		int[] capacity = { 0 };
		consumer.updateCapacity( count -> capacity[0] += count );
		assertThat( capacity[0] ).isEqualTo( input.length );
		consumer.streamEnd( List.of() );
	}

	@Test
	void malformedResponsesDeferredUntilStatusDecisionAndLimitsStillApply() throws Exception {
		for ( String text : List.of( "<proxy>unavailable", "{", "{}{}" ) ) {
			var consumer = new ClientRest5JsonResponseConsumer( 100 );
			Result result = new Result();
			consumer.consumeResponse( new BasicHttpResponse( 503 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
					HttpCoreContext.create(), result );
			ByteBuffer bytes = ByteBuffer.wrap( text.getBytes( StandardCharsets.UTF_8 ) );
			consumer.consume( bytes );
			assertThat( bytes.hasRemaining() ).isFalse();
			consumer.streamEnd( List.of() );
			assertThat( result.response.getCode() ).isEqualTo( 503 );
			var entity = (ClientRest5JsonResponseConsumer.Entity) result.response.getEntity();
			assertThatThrownBy( () -> entity.body().tree() ).isInstanceOf( RuntimeException.class );
			try ( var stream = entity.getContent() ) {
				assertThat( new String( stream.readAllBytes(), StandardCharsets.UTF_8 ) ).isEqualTo( text );
			}
		}
		var consumer = new ClientRest5JsonResponseConsumer( 3 );
		consumer.consumeResponse( new BasicHttpResponse( 503 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
				HttpCoreContext.create(), new Result() );
		consumer.consume( ByteBuffer.wrap( new byte[] { 60 } ) );
		assertThatThrownBy( () -> consumer.consume( ByteBuffer.wrap( new byte[3] ) ) )
				.isInstanceOf( ContentTooLongException.class );
		consumer.failed( new java.io.IOException() );
		consumer.releaseResources();
	}

	@Test
	void absentEmptyJsonNullAndCancellation() throws Exception {
		for ( String text : List.of( "", " \n\t", "null" ) ) {
			var consumer = new ClientRest5JsonResponseConsumer( 100 );
			Result result = new Result();
			consumer.consumeResponse( new BasicHttpResponse( 200 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
					HttpCoreContext.create(), result );
			consumer.consume( ByteBuffer.wrap( text.getBytes( StandardCharsets.UTF_8 ) ) );
			consumer.streamEnd( List.of() );
			var entity = (ClientRest5JsonResponseConsumer.Entity) result.response.getEntity();
			assertThat( JsonProviderHelper.create( true ).getCodec().decode( entity.body().tree(), ObjectNode.class ) )
					.isNull();
		}
		var consumer = new ClientRest5JsonResponseConsumer( 100 );
		Result result = new Result();
		consumer.consumeResponse( new BasicHttpResponse( 204 ), null, HttpCoreContext.create(), result );
		assertThat( result.response.getEntity() ).isNull();
		consumer.releaseResources();
		consumer = new ClientRest5JsonResponseConsumer( 100 );
		Result cancelled = new Result();
		consumer.consumeResponse( new BasicHttpResponse( 200 ), new BasicEntityDetails( -1, ContentType.APPLICATION_JSON ),
				HttpCoreContext.create(), cancelled );
		consumer.consume( ByteBuffer.wrap( new byte[] { 123 } ) );
		consumer.releaseResources();
		consumer.streamEnd( List.of() );
		assertThat( cancelled.completions ).isZero();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 8192 })
	void partialZeroWritesWakeupsAndRetry(int capacity) throws Exception {
		var json = JsonProviderHelper.create( true ).getCodec();
		ObjectNode object = JsonNodeFactory.instance.objectNode();
		object.put( "value", "é😀".repeat( 5000 ) );
		var entity = new ClientRest5JsonHttpEntity( json, List.of( object ) );
		assertThat( entity.getContentLength() ).isEqualTo( -1 );
		Sink sink = new Sink();
		entity.produce( sink );
		assertThat( sink.output.size() ).isZero();
		assertThat( sink.wakeups ).isEqualTo( 1 );
		assertThat( entity.available() ).isPositive();
		sink.remaining = capacity;
		entity.produce( sink );
		entity.releaseResources();
		assertThat( sink.output.size() ).isEqualTo( capacity );
		Sink retry = new Sink();
		while ( !retry.ended ) {
			retry.remaining = capacity;
			entity.produce( retry );
		}
		assertThat( entity.available() ).isZero();
		assertThat( retry.output.toString( StandardCharsets.UTF_8 ) ).isEqualTo( json.write( object ) + "\n" );
		assertThat( entity.getContentLength() ).isEqualTo( -1 );
		entity.releaseResources();
		entity.failed( new java.io.IOException() );
	}

	@Test
	@SuppressWarnings("deprecation")
	void pinnedRequestCompressionBuffersAndPropagatesFailure() throws Exception {
		var json = JsonProviderHelper.create( true ).getCodec();
		ObjectNode document = JsonNodeFactory.instance.objectNode();
		document.put( "value", "é😀".repeat( 10_000 ) );
		var entity = new ClientRest5JsonHttpEntity( json, List.of( document ) );
		var compressed = new co.elastic.clients.transport.rest5_client.low_level.Rest5Client.ContentCompressingEntity( entity );
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
		var failing = org.mockito.Mockito.mock( org.apache.hc.core5.http.HttpEntity.class );
		org.mockito.Mockito.doThrow( new java.io.IOException( "source failed" ) ).when( failing )
				.writeTo( org.mockito.ArgumentMatchers.any( java.io.OutputStream.class ) );
		var failingCompressed =
				new co.elastic.clients.transport.rest5_client.low_level.Rest5Client.ContentCompressingEntity( failing );
		assertThatThrownBy( failingCompressed::getContent ).isInstanceOf( java.io.IOException.class )
				.hasMessageContaining( "source failed" );
	}

	private static final class Result implements FutureCallback<ClassicHttpResponse> {
		ClassicHttpResponse response;
		int completions;

		@Override
		public void completed(ClassicHttpResponse response) {
			this.response = response;
			++completions;
		}

		@Override
		public void failed(Exception exception) {
			throw new org.hibernate.search.util.common.AssertionFailure( "Unexpected failure", exception );
		}

		@Override
		public void cancelled() {
			throw new org.hibernate.search.util.common.AssertionFailure( "Unexpected cancellation" );
		}
	}

	private static final class Sink implements DataStreamChannel {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		int remaining;
		int wakeups;
		boolean ended;

		@Override
		public void requestOutput() {
			++wakeups;
		}

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
		public void endStream(List<? extends Header> trailers) {
			ended = true;
		}

		@Override
		public void endStream() {
			ended = true;
		}
	}
}
