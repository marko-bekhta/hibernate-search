package org.hibernate.search.backend.elasticsearch.client.impl;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonGzipDecoder;
import org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.logging.spi.ElasticsearchClientLog;
import org.hibernate.search.util.json.JsonObject;

/** Parses on the HTTP client's callback thread, requesting one consumed batch at a time. */
final class ClientJdkJsonBodySubscriber implements HttpResponse.BodySubscriber<JsonObject> {
	private final CompletableFuture<JsonObject> result = new CompletableFuture<>();
	private final JsonResponseBody body;
	private JsonGzipDecoder gzip;
	private final JsonCodecService json;
	private final int statusCode;
	private Flow.Subscription subscription;
	private boolean terminated;

	ClientJdkJsonBodySubscriber(JsonCodecService json, Charset charset, int statusCode) {
		this( json, charset, statusCode, false );
	}

	ClientJdkJsonBodySubscriber(JsonCodecService json, Charset charset, int statusCode, boolean compressed) {
		this.json = json;
		this.statusCode = statusCode;
		body = new JsonResponseBody( charset, 0 );
		if ( compressed ) {
			gzip = new JsonGzipDecoder( body );
		}
		result.whenComplete( (value, failure) -> {
			if ( result.isCancelled() ) {
				cancel();
			}
		} );
	}

	@Override
	public CompletionStage<JsonObject> getBody() {
		return result;
	}

	@Override
	public synchronized void onSubscribe(Flow.Subscription incoming) {
		if ( subscription != null || terminated ) {
			incoming.cancel();
			return;
		}
		subscription = incoming;
		incoming.request( 1 );
	}

	@Override
	public synchronized void onNext(List<ByteBuffer> buffers) {
		if ( terminated ) {
			return;
		}
		try {
			for ( ByteBuffer buffer : buffers ) {
				if ( gzip == null ) {
					body.feed( buffer );
				}
				else {
					gzip.feed( buffer );
				}
				body.throwIfFailed();
			}
			subscription.request( 1 );
		}
		catch (RuntimeException e) {
			cancel();
			result.completeExceptionally( ElasticsearchClientLog.INSTANCE.failedToParseElasticsearchResponse(
					statusCode, null, e.getMessage(), e ) );
		}
	}

	@Override
	public synchronized void onError(Throwable failure) {
		if ( terminated ) {
			return;
		}
		cancel();
		result.completeExceptionally( failure );
	}

	@Override
	public synchronized void onComplete() {
		if ( terminated ) {
			return;
		}
		try {
			if ( gzip != null ) {
				gzip.endOfInput();
			}
			body.complete();
			JsonObject tree = json.decode( body.tree(), JsonObject.class );
			terminated = true;
			close();
			result.complete( tree );
		}
		catch (RuntimeException e) {
			cancel();
			result.completeExceptionally( ElasticsearchClientLog.INSTANCE.failedToParseElasticsearchResponse(
					statusCode, null, e.getMessage(), e ) );
		}
	}

	private void close() {
		body.close();
		if ( gzip != null ) {
			gzip.close();
			gzip = null;
		}
	}

	synchronized void cancel() {
		terminated = true;
		close();
		if ( subscription != null ) {
			subscription.cancel();
		}
	}
}
