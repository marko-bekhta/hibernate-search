package org.hibernate.search.backend.elasticsearch.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;
import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.impl.test.http.StreamingHttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class ClientJdkJsonTransportTest {
	private final JsonCodecService json = JsonProviderHelper.create( true ).getCodec();

	@Test
	void localHttpParsesBeforeResponseCompletion() throws Exception {
		CountDownLatch sent = new CountDownLatch( 1 );
		CountDownLatch finish = new CountDownLatch( 1 );
		try ( var server = new StreamingHttpServer( exchange -> {
			exchange.headers( 200, "application/json", "" );
			exchange.chunk( "<proxy>".getBytes( StandardCharsets.UTF_8 ) );
			sent.countDown();
			assertThat( finish.await( 10, TimeUnit.SECONDS ) ).isTrue();
			exchange.end();
		} ) ) {
			HttpClient client = HttpClient.newHttpClient();
			var subscriber = new ClientJdkJsonBodySubscriber( json, StandardCharsets.UTF_8, 200 );
			var request = HttpRequest.newBuilder( URI.create( server.uri() ) ).build();
			var future = client.sendAsync( request, info -> subscriber );
			try {
				assertThat( sent.await( 10, TimeUnit.SECONDS ) ).isTrue();
				assertThatThrownBy( () -> future.get( 10, TimeUnit.SECONDS ) ).hasMessageContaining( "200" );
				assertThat( finish.getCount() ).isEqualTo( 1 );
			}
			finally {
				finish.countDown();
				if ( ( (Object) client ) instanceof AutoCloseable closeable ) {
					closeable.close();
				}
			}
		}
	}

	@Test
	void demandRetainedStorageAndReplay() throws Exception {
		ObjectNode tree = JsonNodeFactory.instance.objectNode();
		tree.put( "text", "é".repeat( 50_000 ) );
		var publisher = new ClientJdkJsonHttpEntity( json, List.of( tree, tree ) );
		assertThat( publisher.contentLength() ).isEqualTo( -1 );
		RecordingSubscriber cancelled = new RecordingSubscriber();
		publisher.subscribe( cancelled );
		cancelled.subscription.cancel();
		cancelled.subscription.request( 1 );
		assertThat( cancelled.buffers ).isEmpty();
		RecordingSubscriber first = new RecordingSubscriber();
		publisher.subscribe( first );
		assertThat( first.buffers ).isEmpty();
		first.subscription.request( 1 );
		assertThat( first.buffers ).hasSize( 1 );
		byte[] retained = bytes( first.buffers.get( 0 ) );
		first.subscription.request( 1 );
		assertThat( first.buffers ).hasSize( 2 );
		assertThat( bytes( first.buffers.get( 0 ) ) ).isEqualTo( retained );
		first.subscription.cancel();
		first.subscription.request( Long.MAX_VALUE );
		assertThat( first.buffers ).hasSize( 2 );
		assertThat( first.complete ).isFalse();
		RecordingSubscriber retry = new RecordingSubscriber();
		publisher.subscribe( retry );
		retry.subscription.request( Long.MAX_VALUE );
		assertThat( retry.complete ).isTrue();
		assertThat( bytes( retry.buffers.get( 0 ) ) ).isEqualTo( retained );
		assertThat( retry.buffers ).allSatisfy( buffer -> assertThat( buffer.remaining() ).isLessThanOrEqualTo( 8192 ) );
		String encoded = collect( retry.buffers );
		assertThat( encoded ).isEqualTo( ( json.write( tree ) + "\n" ).repeat( 2 ) );
		assertThat( publisher.contentLength() ).isEqualTo( -1 );
	}

	@Test
	void reentrantDemandAndInvalidDemand() throws Exception {
		ObjectNode tree = JsonNodeFactory.instance.objectNode();
		tree.put( "value", "x".repeat( 100_000 ) );
		var publisher = new ClientJdkJsonHttpEntity( json, List.of( tree ) );
		RecordingSubscriber subscriber = new RecordingSubscriber() {
			@Override
			public void onNext(ByteBuffer value) {
				super.onNext( value );
				subscription.request( 1 );
			}
		};
		publisher.subscribe( subscriber );
		subscriber.subscription.request( 1 );
		assertThat( subscriber.complete ).isTrue();
		assertThat( collect( subscriber.buffers ) ).isEqualTo( json.write( tree ) + "\n" );
		RecordingSubscriber invalid = new RecordingSubscriber() {
			@Override
			public void onNext(ByteBuffer value) {
				super.onNext( value );
				subscription.request( 0 );
			}
		};
		publisher.subscribe( invalid );
		invalid.subscription.request( Long.MAX_VALUE );
		assertThat( invalid.buffers ).hasSize( 1 );
		assertThat( invalid.failure ).isInstanceOf( IllegalArgumentException.class );
		assertThat( invalid.complete ).isFalse();
		RecordingSubscriber before = new RecordingSubscriber();
		publisher.subscribe( before );
		before.subscription.request( -1 );
		assertThat( before.failure ).isInstanceOf( IllegalArgumentException.class );
		assertThat( before.buffers ).isEmpty();
	}

	@Test
	void concurrentCancellationDuringCallback() throws Exception {
		ObjectNode tree = JsonNodeFactory.instance.objectNode();
		tree.put( "value", "x".repeat( 1_000_000 ) );
		var publisher = new ClientJdkJsonHttpEntity( json, List.of( tree ) );
		CountDownLatch entered = new CountDownLatch( 1 );
		CountDownLatch release = new CountDownLatch( 1 );
		RecordingSubscriber subscriber = new RecordingSubscriber() {
			@Override
			public void onNext(ByteBuffer value) {
				super.onNext( value );
				entered.countDown();
				try {
					assertThat( release.await( 10, TimeUnit.SECONDS ) ).isTrue();
				}
				catch (InterruptedException e) {
					throw new AssertionFailure( "Interrupted", e );
				}
			}
		};
		publisher.subscribe( subscriber );
		CompletableFuture<Void> drain = CompletableFuture.runAsync( () -> subscriber.subscription.request( Long.MAX_VALUE ) );
		try {
			assertThat( entered.await( 10, TimeUnit.SECONDS ) ).isTrue();
			subscriber.subscription.cancel();
		}
		finally {
			release.countDown();
		}
		drain.get( 10, TimeUnit.SECONDS );
		assertThat( subscriber.buffers ).hasSize( 1 );
		assertThat( subscriber.complete ).isFalse();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 8192 })
	void responseFragmentationAndOwnership(int capacity) {
		for ( int kind = 0; kind < 4; ++kind ) {
			var subscriber = new ClientJdkJsonBodySubscriber( json, StandardCharsets.UTF_8, 200 );
			Demand demand = new Demand();
			subscriber.onSubscribe( demand );
			byte[] input = ( "{\"é😀\":\"" + "😀é".repeat( 9000 ) + "\"}" ).getBytes( StandardCharsets.UTF_8 );
			subscriber.onNext( List.of( ByteBuffer.allocate( 0 ) ) );
			int batches = 1;
			for ( int offset = 0; offset < input.length; offset += capacity ) {
				int size = Math.min( capacity, input.length - offset );
				ByteBuffer buffer = kind == 1 ? ByteBuffer.allocateDirect( size + 4 ) : ByteBuffer.allocate( size + 4 );
				buffer.position( 2 );
				buffer.put( input, offset, size );
				buffer.flip().position( 2 );
				ByteBuffer supplied = kind == 2 ? buffer.slice() : kind == 3 ? buffer.asReadOnlyBuffer() : buffer;
				subscriber.onNext( List.of( supplied ) );
				assertThat( supplied.hasRemaining() ).isFalse();
				// The parser has consumed the caller's storage before response completion.
				buffer.clear();
				while ( buffer.hasRemaining() ) {
					buffer.put( (byte) 0 );
				}
				++batches;
			}
			assertThat( demand.requested ).isEqualTo( batches + 1 );
			assertThat( subscriber.getBody().toCompletableFuture() ).isNotDone();
			subscriber.onComplete();
			assertThat( subscriber.getBody().toCompletableFuture().join().get( "é😀" ).asString() )
					.isEqualTo( "😀é".repeat( 9000 ) );
		}
	}

	@Test
	void responseErrorsEmptyAndCancellation() {
		for ( String input : List.of( "", " \n\t", "null" ) ) {
			var subscriber = new ClientJdkJsonBodySubscriber( json, StandardCharsets.UTF_8, 204 );
			subscriber.onSubscribe( new Demand() );
			subscriber.onNext( List.of( ByteBuffer.wrap( input.getBytes( StandardCharsets.UTF_8 ) ) ) );
			subscriber.onComplete();
			assertThat( subscriber.getBody().toCompletableFuture().join() ).isNull();
		}
		for ( String input : List.of( "{", "{} {}", "<html>proxy</html>" ) ) {
			var subscriber = new ClientJdkJsonBodySubscriber( json, StandardCharsets.UTF_8, 503 );
			Demand demand = new Demand();
			subscriber.onSubscribe( demand );
			subscriber.onNext( List.of( ByteBuffer.wrap( input.getBytes( StandardCharsets.UTF_8 ) ) ) );
			subscriber.onComplete();
			assertThatThrownBy( () -> subscriber.getBody().toCompletableFuture().join() ).hasMessageContaining( "503" );
			assertThat( demand.cancelled ).isTrue();
		}
		var failed = new ClientJdkJsonBodySubscriber( json, StandardCharsets.UTF_8, 502 );
		Demand failedDemand = new Demand();
		failed.onSubscribe( failedDemand );
		var failure = new IOException( "Interrupted HTTP response" );
		failed.onError( failure );
		assertThatThrownBy( () -> failed.getBody().toCompletableFuture().join() ).hasCause( failure );
		assertThat( failedDemand.cancelled ).isTrue();
		failed.onComplete();
		var cancelledBeforeSubscribe = new ClientJdkJsonBodySubscriber( json, StandardCharsets.UTF_8, 200 );
		cancelledBeforeSubscribe.getBody().toCompletableFuture().cancel( true );
		Demand lateDemand = new Demand();
		cancelledBeforeSubscribe.onSubscribe( lateDemand );
		assertThat( lateDemand.cancelled ).isTrue();
		var subscriber = new ClientJdkJsonBodySubscriber( json, StandardCharsets.UTF_8, 200 );
		Demand demand = new Demand();
		subscriber.onSubscribe( demand );
		subscriber.getBody().toCompletableFuture().cancel( true );
		assertThat( demand.cancelled ).isTrue();
		subscriber.onComplete();
		subscriber.onNext( List.of( ByteBuffer.wrap( new byte[] { 123 } ) ) );
		assertThat( subscriber.getBody().toCompletableFuture() ).isCancelled();
	}

	private static byte[] bytes(ByteBuffer buffer) {
		byte[] copy = new byte[buffer.remaining()];
		buffer.duplicate().get( copy );
		return copy;
	}

	private static String collect(List<ByteBuffer> buffers) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		for ( ByteBuffer buffer : buffers ) {
			output.writeBytes( bytes( buffer ) );
		}
		return output.toString( StandardCharsets.UTF_8 );
	}

	private static class RecordingSubscriber implements Flow.Subscriber<ByteBuffer> {
		Flow.Subscription subscription;
		final List<ByteBuffer> buffers = new ArrayList<>();
		boolean complete;
		Throwable failure;

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
		}

		@Override
		public void onNext(ByteBuffer buffer) {
			buffers.add( buffer );
		}

		@Override
		public void onError(Throwable cause) {
			failure = cause;
		}

		@Override
		public void onComplete() {
			complete = true;
		}
	}

	private static class Demand implements Flow.Subscription {
		int requested;
		boolean cancelled;

		@Override
		public void request(long n) {
			assertThat( n ).isEqualTo( 1 );
			++requested;
		}

		@Override
		public void cancel() {
			cancelled = true;
		}
	}
}
