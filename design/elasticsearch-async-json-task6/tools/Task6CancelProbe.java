import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.search.util.impl.test.http.StreamingHttpServer;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.ResponseListener;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;

/** Diagnostic only: observes the pinned stock client's cancellation/header-delivery race. */
public class Task6CancelProbe {
	public static void main(String[] args) throws Exception {
		for ( boolean automaticRetries : new boolean[] { true, false } ) {
			var started = new CountDownLatch( 1 );
			var disconnected = new CountDownLatch( 1 );
			var listenerDone = new CountDownLatch( 1 );
			var failure = new AtomicReference<Exception>();
			try ( var server = new StreamingHttpServer( exchange -> {
				exchange.headers( 200, "application/json", "" );
				exchange.chunk( "{}".getBytes( StandardCharsets.UTF_8 ) );
				started.countDown();
				if ( exchange.awaitDisconnect() ) {
					disconnected.countDown();
				}
				exchange.closeConnection();
			} ); var client = Rest5Client.builder( URI.create( server.uri() ) )
					.setHttpClientConfigCallback( builder -> {
						if ( !automaticRetries ) {
							builder.disableAutomaticRetries();
						}
					} )
					.build() ) {
				var cancellation = client.performRequestAsync( new Request( "GET", "/" ), new ResponseListener() {
					@Override
					public void onSuccess(Response response) {
						listenerDone.countDown();
					}

					@Override
					public void onFailure(Exception exception) {
						failure.set( exception );
						listenerDone.countDown();
					}
				} );
				if ( !started.await( 10, TimeUnit.SECONDS ) ) {
					throw new IllegalStateException( "No response prefix received" );
				}
				boolean cancelled = cancellation.cancel();
				boolean notified = listenerDone.await( 10, TimeUnit.SECONDS );
				boolean socketEof = disconnected.await( 3, TimeUnit.SECONDS );
				System.out.println( "automaticRetries=" + automaticRetries + ", cancelled=" + cancelled
						+ ", listenerNotified=" + notified + ", listenerFailure="
						+ ( failure.get() == null ? null : failure.get().getClass().getSimpleName() )
						+ ", socketEofWithin3Seconds=" + socketEof );
			}
		}
	}
}
