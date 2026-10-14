package org.hibernate.search.backend.elasticsearch.client.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.hibernate.search.backend.elasticsearch.client.common.json.spi.JsonLogHelper;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchClientImplementor;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchResponse;
import org.hibernate.search.backend.elasticsearch.client.common.util.spi.ElasticsearchClientUtils;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.logging.spi.ElasticsearchClientLog;
import org.hibernate.search.backend.elasticsearch.logging.spi.ElasticsearchRequestLog;
import org.hibernate.search.engine.common.execution.spi.SimpleScheduledExecutor;
import org.hibernate.search.engine.common.timing.Deadline;
import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.util.common.impl.Closer;

import tools.jackson.databind.node.ObjectNode;

public class ClientJdkElasticsearchClient implements ElasticsearchClientImplementor {

	private static final Pattern CHARSET_PATTERN =
			Pattern.compile( "(?:;|^)\\s*charset\\s*=\\s*\"?([^\"\\s;]+)\"?", Pattern.CASE_INSENSITIVE );

	private final BeanHolder<? extends RestJdkClient> restClientHolder;

	private final SimpleScheduledExecutor timeoutExecutorService;

	private final Optional<Integer> requestTimeoutMs;

	private final JsonCodecService json;
	private final JsonLogHelper jsonLogHelper;

	private final List<HttpRequestInterceptor> requestInterceptors;

	ClientJdkElasticsearchClient(BeanHolder<? extends RestJdkClient> restClientHolder,
			SimpleScheduledExecutor timeoutExecutorService,
			Optional<Integer> requestTimeoutMs,
			JsonCodecService json, JsonLogHelper jsonLogHelper, List<HttpRequestInterceptor> requestInterceptors
	) {
		this.restClientHolder = restClientHolder;
		this.timeoutExecutorService = timeoutExecutorService;
		this.requestTimeoutMs = requestTimeoutMs;
		this.json = json;
		this.jsonLogHelper = jsonLogHelper;
		this.requestInterceptors = requestInterceptors;
	}

	@Override
	public CompletableFuture<ElasticsearchResponse> submit(ElasticsearchRequest request) {
		CompletableFuture<HttpResponse<ObjectNode>> sent;
		try {
			sent = send( request );
		}
		catch (RuntimeException e) {
			return CompletableFuture.failedFuture( e );
		}
		CompletableFuture<ElasticsearchResponse> result = sent.thenApply( this::convertResponse );
		result.whenComplete( (response, failure) -> {
			if ( result.isCancelled() ) {
				sent.cancel( true );
			}
		} );
		if ( ElasticsearchRequestLog.INSTANCE.isDebugEnabled() ) {
			long startTime = System.nanoTime();
			result.thenAccept( response -> log( request, startTime, response ) );
		}
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T unwrap(Class<T> clientClass) {
		if ( RestJdkClient.class.isAssignableFrom( clientClass ) ) {
			return (T) restClientHolder.get();
		}
		throw ElasticsearchClientLog.INSTANCE.clientUnwrappingWithUnknownType( clientClass, HttpClient.class );
	}

	private ElasticsearchResponse convertResponse(HttpResponse<ObjectNode> response) {
		try {
			return ElasticsearchResponse.withJsonBody(
					response.request().uri().getHost(),
					response.statusCode(),
					null,
					response.body() );
		}
		catch (RuntimeException e) {
			throw ElasticsearchClientLog.INSTANCE.failedToParseElasticsearchResponse( response.statusCode(),
					null, e.getMessage(), e );
		}
	}

	private static Charset getCharset(HttpHeaders headers) {
		Optional<String> contentType = headers.firstValue( "Content-Type" );
		if ( contentType.isPresent() ) {
			var matcher = CHARSET_PATTERN.matcher( contentType.get() );
			if ( matcher.find() ) {
				return Charset.forName( matcher.group( 1 ) );
			}
		}
		return StandardCharsets.UTF_8;
	}

	private CompletableFuture<HttpResponse<ObjectNode>> send(ElasticsearchRequest elasticsearchRequest) {
		HttpRequest request;
		try {
			HttpRequest.BodyPublisher entity = ClientJdkJsonHttpEntity.toEntity( json, elasticsearchRequest );
			request = toRequest( elasticsearchRequest, entity );
		}
		catch (IOException | RuntimeException e) {
			CompletableFuture<HttpResponse<ObjectNode>> completableFuture = new CompletableFuture<>();
			completableFuture.completeExceptionally( e );
			return completableFuture;
		}

		ObjectNodeBodyHandler handler = new ObjectNodeBodyHandler();
		CompletableFuture<HttpResponse<ObjectNode>> transportFuture = restClientHolder.get().sendAsync(
				request,
				handler
		);
		CompletableFuture<HttpResponse<ObjectNode>> completableFuture = new CompletableFuture<>();
		transportFuture.whenComplete( (response, failure) -> {
			if ( failure == null ) {
				completableFuture.complete( response );
			}
			else {
				completableFuture.completeExceptionally( failure );
			}
		} );
		completableFuture.whenComplete( (response, failure) -> {
			if ( failure != null ) {
				handler.cancel();
				transportFuture.cancel( true );
			}
		} );

		Deadline deadline = elasticsearchRequest.deadline();
		if ( deadline == null && requestTimeoutMs.isEmpty() ) {
			// no need to schedule a client side timeout
			return completableFuture;
		}

		try {
			long currentTimeoutValue =
					deadline == null ? Long.valueOf( requestTimeoutMs.get() ) : deadline.checkRemainingTimeMillis();

			ScheduledFuture<?> timeout = timeoutExecutorService.schedule(
					() -> {
						if ( !completableFuture.isDone() ) {
							RuntimeException cause = ElasticsearchClientLog.INSTANCE.requestTimedOut(
									Duration.ofNanos( TimeUnit.MILLISECONDS.toNanos( currentTimeoutValue ) ),
									elasticsearchRequest );
							completableFuture.completeExceptionally(
									deadline != null ? deadline.forceTimeoutAndCreateException( cause ) : cause
							);
						}
					},
					currentTimeoutValue, TimeUnit.MILLISECONDS
			);
			completableFuture.whenComplete( (response, failure) -> timeout.cancel( false ) );
		}
		catch (RuntimeException e) {
			// A deadline or executor failure after submission must still cancel the active transport.
			completableFuture.completeExceptionally( e );
		}

		return completableFuture;
	}

	private HttpRequest toRequest(ElasticsearchRequest elasticsearchRequest, HttpRequest.BodyPublisher bodyPublisher)
			throws IOException {
		URI uri = toUri( elasticsearchRequest );
		HttpRequest.Builder request = HttpRequest.newBuilder( uri )
				.method( elasticsearchRequest.method(), bodyPublisher );
		setPerRequestSocketTimeout( elasticsearchRequest, request );
		if ( !ClientJdkJsonHttpEntity.isNoBodyPublisher( bodyPublisher ) ) {
			request.header( "Content-Type", "application/json" );
		}

		HttpRequestInterceptorContext context = new HttpRequestInterceptorContext( elasticsearchRequest.method() );
		for ( HttpRequestInterceptor requestInterceptor : requestInterceptors ) {
			requestInterceptor.process( request, bodyPublisher, context );
		}

		return request.build();
	}

	private URI toUri(ElasticsearchRequest elasticsearchRequest) {
		return restClientHolder.get().nextNode().createRequestURI( elasticsearchRequest.path(),
				elasticsearchRequest.parameters() );
	}

	private void setPerRequestSocketTimeout(ElasticsearchRequest elasticsearchRequest, HttpRequest.Builder request) {
		Deadline deadline = elasticsearchRequest.deadline();
		if ( deadline == null ) {
			return;
		}

		long timeToHardTimeout = deadline.checkRemainingTimeMillis();

		// set a per-request socket timeout
		int generalRequestTimeoutMs = ( timeToHardTimeout <= Integer.MAX_VALUE ) ? Math.toIntExact( timeToHardTimeout ) : -1;
		request.timeout( Duration.of( generalRequestTimeoutMs, ChronoUnit.MILLIS ) );
	}

	private void log(ElasticsearchRequest request, long start, ElasticsearchResponse response) {
		boolean successCode = ElasticsearchClientUtils.isSuccessCode( response.statusCode() );
		if ( !ElasticsearchRequestLog.INSTANCE.isTraceEnabled() && successCode ) {
			return;
		}
		long executionTimeNs = System.nanoTime() - start;
		long executionTimeMs = TimeUnit.NANOSECONDS.toMillis( executionTimeNs );
		if ( successCode ) {
			ElasticsearchRequestLog.INSTANCE.executedRequest( request.method(), response.hostAndPort(), request.path(),
					request.parameters(),
					request.bodyPartsJson().size(), executionTimeMs,
					response.statusCode(), response.statusMessage(),
					jsonLogHelper.toString( request.bodyPartsJson() ),
					jsonLogHelper.toString( response.bodyJson() ) );
		}
		else {
			ElasticsearchRequestLog.INSTANCE.executedRequestWithFailure( request.method(), response.hostAndPort(),
					request.path(),
					request.parameters(),
					request.bodyPartsJson().size(), executionTimeMs,
					response.statusCode(), response.statusMessage(),
					jsonLogHelper.toString( request.bodyPartsJson() ),
					jsonLogHelper.toString( response.bodyJson() ) );
		}
	}

	@Override
	public void close() {
		try ( Closer<IOException> closer = new Closer<>() ) {
			/*
			 * There's no point waiting for timeouts: we'll just expect the RestClient to cancel all
			 * currently running requests when closing.
			 */
			// The BeanHolder is responsible for calling close() on the client if necessary.
			closer.push( BeanHolder::close, this.restClientHolder );
		}
		catch (RuntimeException | IOException e) {
			throw ElasticsearchClientLog.INSTANCE.unableToShutdownClient( e.getMessage(), e );
		}
	}

	private class ObjectNodeBodyHandler implements HttpResponse.BodyHandler<ObjectNode> {
		private ClientJdkJsonBodySubscriber subscriber;
		private boolean cancelled;

		void cancel() {
			ClientJdkJsonBodySubscriber active;
			synchronized (this) {
				cancelled = true;
				active = subscriber;
			}
			if ( active != null ) {
				active.getBody().toCompletableFuture().cancel( true );
			}
		}

		@Override
		public synchronized HttpResponse.BodySubscriber<ObjectNode> apply(HttpResponse.ResponseInfo responseInfo) {
			try {
				subscriber =
						new ClientJdkJsonBodySubscriber( json, getCharset( responseInfo.headers() ), responseInfo.statusCode(),
								responseInfo.headers().firstValue( "Content-Encoding" ).map( "gzip"::equalsIgnoreCase )
										.orElse( false ) );
				if ( cancelled ) {
					subscriber.getBody().toCompletableFuture().cancel( true );
				}
				return subscriber;
			}
			catch (RuntimeException e) {
				throw ElasticsearchClientLog.INSTANCE.failedToParseElasticsearchResponse(
						responseInfo.statusCode(), null, e.getMessage(), e );
			}
		}
	}
}
