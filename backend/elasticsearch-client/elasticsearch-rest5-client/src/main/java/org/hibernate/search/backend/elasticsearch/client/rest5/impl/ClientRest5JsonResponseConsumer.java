package org.hibernate.search.backend.elasticsearch.client.rest5.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonGzipDecoder;
import org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody;
import org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseConsumerScope;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentTooLongException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

/** HC5 delivers decompressed bytes by default. Entity details identify any remaining gzip layer. */
final class ClientRest5JsonResponseConsumer implements AsyncResponseConsumer<ClassicHttpResponse> {
	private final long limit;
	private final JsonResponseConsumerScope scope;
	private BasicClassicHttpResponse response;
	private JsonResponseBody body;
	private JsonGzipDecoder gzip;
	private boolean gzipFailed;
	private boolean terminated;
	private long inputBytes;
	private int capacity = 8192;
	private FutureCallback<ClassicHttpResponse> callback;

	ClientRest5JsonResponseConsumer(long limit) {
		this( limit, new JsonResponseConsumerScope() );
	}

	ClientRest5JsonResponseConsumer(long limit, JsonResponseConsumerScope scope) {
		this.limit = limit;
		this.scope = scope;
		scope.register( this::releaseResources );
	}

	@Override
	public synchronized void consumeResponse(HttpResponse incoming, EntityDetails details, HttpContext context,
			FutureCallback<ClassicHttpResponse> callback)
			throws IOException {
		if ( scope.isCancelled() ) {
			releaseResources();
			callback.cancelled();
			return;
		}
		// REST5 may retry internally within HC5 without invoking the low-level consumer factory again.
		// Each new response head starts fresh parsing/capacity state, including after release.
		releaseResources();
		terminated = false;
		gzipFailed = false;
		inputBytes = 0;
		capacity = 8192;
		this.callback = callback;
		response = new BasicClassicHttpResponse( incoming.getCode(), incoming.getReasonPhrase() );
		response.setVersion( incoming.getVersion() );
		response.setHeaders( incoming.getHeaders() );
		if ( incoming.getLocale() != null ) {
			response.setLocale( incoming.getLocale() );
		}
		if ( details == null ) {
			terminated = true;
			callback.completed( response );
			return;
		}
		checkLimit( details.getContentLength() );
		ContentType contentType = ContentType.parse( details.getContentType() );
		Charset charset = contentType == null || contentType.getCharset() == null
				? StandardCharsets.UTF_8
				: contentType.getCharset();
		body = new JsonResponseBody( charset, 0 );
		if ( "gzip".equalsIgnoreCase( details.getContentEncoding() ) ) {
			gzip = new JsonGzipDecoder( body );
		}
		// The diagnostic entity always exposes decoded UTF-8. Original headers stay on the response.
		response.setEntity( new Entity( body ) );
	}

	@Override
	public void informationResponse(HttpResponse response, HttpContext context) {
	}

	@Override
	public synchronized void updateCapacity(CapacityChannel channel) throws IOException {
		channel.update( capacity );
	}

	@Override
	public synchronized void consume(ByteBuffer input) throws IOException {
		if ( terminated ) {
			input.position( input.limit() );
			return;
		}
		// HC5 can deliver more than the declared window to drain its socket buffer.
		// Repay that entire batch on the next capacity update, avoiding a negative-window stall.
		capacity = Math.max( 8192, input.remaining() );
		inputBytes += input.remaining();
		checkLimit( inputBytes );
		if ( gzip != null ) {
			try {
				gzip.feed( input );
			}
			catch (RuntimeException e) {
				body.failedParsing( e );
				gzip.close();
				gzip = null;
				gzipFailed = true;
			}
		}
		else if ( !gzipFailed ) {
			body.feed( input );
		}
		input.position( input.limit() );
	}

	private void checkLimit(long count) throws ContentTooLongException {
		if ( limit > 0 && count > limit ) {
			throw new ContentTooLongException( "Response input exceeds byte limit " + limit );
		}
	}

	@Override
	public synchronized void streamEnd(List<? extends Header> trailers) {
		if ( terminated ) {
			return;
		}
		terminated = true;
		if ( gzip != null ) {
			try {
				gzip.endOfInput();
			}
			catch (RuntimeException e) {
				body.failedParsing( e );
			}
		}
		body.complete();
		BasicClassicHttpResponse completed = response;
		FutureCallback<ClassicHttpResponse> completedCallback = callback;
		releaseResources();
		completedCallback.completed( completed );
	}

	@Override
	public synchronized void failed(Exception cause) {
		terminated = true;
		releaseResources();
	}

	@Override
	public synchronized void releaseResources() {
		terminated = true;
		if ( gzip != null ) {
			gzip.close();
			gzip = null;
		}
		if ( body != null ) {
			body.close();
			body = null;
		}
		response = null;
		callback = null;
	}

	static final class Entity extends AbstractHttpEntity {
		private final JsonResponseBody body;

		Entity(JsonResponseBody body) {
			super( "application/json; charset=UTF-8", null, false );
			this.body = body;
		}

		JsonResponseBody body() {
			return body;
		}

		@Override
		public boolean isRepeatable() {
			return true;
		}

		@Override
		public long getContentLength() {
			return -1;
		}

		@Override
		public InputStream getContent() {
			return body.openDiagnosticStream();
		}

		@Override
		public void writeTo(OutputStream output) throws IOException {
			try ( InputStream stream = getContent() ) {
				stream.transferTo( output );
			}
		}

		@Override
		public boolean isStreaming() {
			return false;
		}

		@Override
		public void close() {
		}
	}
}
