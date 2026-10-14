package org.hibernate.search.backend.elasticsearch.client.rest4.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseBody;
import org.hibernate.search.backend.elasticsearch.client.common.json.impl.JsonResponseConsumerScope;
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

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.sniff.Sniffer;
import tools.jackson.databind.node.ObjectNode;

public class ClientRest4ElasticsearchClient implements ElasticsearchClientImplementor {

	private final BeanHolder<? extends RestClient> restClientHolder;

	private final Sniffer sniffer;

	private final SimpleScheduledExecutor timeoutExecutorService;

	private final Optional<Integer> requestTimeoutMs;
	private final int connectionTimeoutMs;

	private final JsonCodecService json;
	private final JsonLogHelper jsonLogHelper;

	ClientRest4ElasticsearchClient(BeanHolder<? extends RestClient> restClientHolder, Sniffer sniffer,
			SimpleScheduledExecutor timeoutExecutorService,
			Optional<Integer> requestTimeoutMs, int connectionTimeoutMs,
			JsonCodecService json, JsonLogHelper jsonLogHelper) {
		this.restClientHolder = restClientHolder;
		this.sniffer = sniffer;
		this.timeoutExecutorService = timeoutExecutorService;
		this.requestTimeoutMs = requestTimeoutMs;
		this.connectionTimeoutMs = connectionTimeoutMs;
		this.json = json;
		this.jsonLogHelper = jsonLogHelper;
	}

	@Override
	public CompletableFuture<ElasticsearchResponse> submit(ElasticsearchRequest request) {
		CompletableFuture<Response> sent;
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
		if ( RestClient.class.isAssignableFrom( clientClass ) ) {
			return (T) restClientHolder.get();
		}
		throw ElasticsearchClientLog.INSTANCE.clientUnwrappingWithUnknownType( clientClass, RestClient.class );
	}

	private CompletableFuture<Response> send(ElasticsearchRequest elasticsearchRequest) {
		CompletableFuture<Response> completableFuture = new CompletableFuture<>();
		JsonResponseConsumerScope responseScope = new JsonResponseConsumerScope();

		HttpEntity entity;
		try {
			entity = ClientRest4JsonHttpEntity.toEntity( json, elasticsearchRequest );
		}
		catch (IOException | RuntimeException e) {
			completableFuture.completeExceptionally( e );
			return completableFuture;
		}

		var cancellation = restClientHolder.get().performRequestAsync(
				toRequest( elasticsearchRequest, entity, responseScope ),
				new ResponseListener() {
					@Override
					public void onSuccess(Response response) {
						completableFuture.complete( response );
					}

					@Override
					public void onFailure(Exception exception) {
						if ( exception instanceof ResponseException ) {
							/*
							 * The client tries to guess what's an error and what's not, but it's too naive.
							 * A 404 on DELETE is not always important to us, for instance.
							 * Thus we ignore the exception and do our own checks afterwards.
							 */
							completableFuture.complete( ( (ResponseException) exception ).getResponse() );
						}
						else {
							completableFuture.completeExceptionally( exception );
						}
					}
				}
		);

		completableFuture.whenComplete( (response, failure) -> {
			if ( failure != null ) {
				if ( entity instanceof org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.JsonHttpEntityContentProvider provider ) {
					provider.cancel();
				}
				responseScope.cancel();
				cancellation.cancel();
			}
		} );

		Deadline deadline = elasticsearchRequest.deadline();
		if ( deadline == null && !requestTimeoutMs.isPresent() ) {
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

	private Request toRequest(ElasticsearchRequest elasticsearchRequest, HttpEntity entity,
			JsonResponseConsumerScope responseScope) {
		Request request = new Request( elasticsearchRequest.method(), elasticsearchRequest.path() );
		setPerRequestSocketTimeout( elasticsearchRequest, request );
		RequestOptions.Builder options = request.getOptions().toBuilder();
		options.setHttpAsyncResponseConsumerFactory(
				() -> new ClientRest4JsonResponseConsumer( JsonResponseBody.DEFAULT_LIMIT, responseScope ) );
		request.setOptions( options );

		for ( Entry<String, String> parameter : elasticsearchRequest.parameters().entrySet() ) {
			request.addParameter( parameter.getKey(), parameter.getValue() );
		}

		request.setEntity( entity );

		return request;
	}

	private void setPerRequestSocketTimeout(ElasticsearchRequest elasticsearchRequest, Request request) {
		Deadline deadline = elasticsearchRequest.deadline();
		if ( deadline == null ) {
			return;
		}

		long timeToHardTimeout = deadline.checkRemainingTimeMillis();

		// set a per-request socket timeout
		int socketTimeoutMs = ( timeToHardTimeout <= Integer.MAX_VALUE ) ? Math.toIntExact( timeToHardTimeout ) : -1;
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout( 0 ) //Disable lease handling for the connection pool! See also HSEARCH-2681
				.setSocketTimeout( socketTimeoutMs )
				.setConnectTimeout( connectionTimeoutMs )
				.build();

		RequestOptions.Builder requestOptions = RequestOptions.DEFAULT.toBuilder()
				.setRequestConfig( requestConfig );

		request.setOptions( requestOptions );
	}

	private ElasticsearchResponse convertResponse(Response response) {
		try {
			ObjectNode body = parseBody( response );
			return ElasticsearchResponse.withJsonBody(
					response.getHost().toHostString(),
					response.getStatusLine().getStatusCode(),
					response.getStatusLine().getReasonPhrase(),
					body );
		}
		catch (IOException | RuntimeException e) {
			throw ElasticsearchClientLog.INSTANCE.failedToParseElasticsearchResponse( response.getStatusLine().getStatusCode(),
					response.getStatusLine().getReasonPhrase(), e.getMessage(), e );
		}
	}

	private ObjectNode parseBody(Response response) throws IOException {
		HttpEntity entity = response.getEntity();
		if ( entity == null ) {
			return null;
		}

		return json.decode( ( (ClientRest4JsonResponseConsumer.Entity) entity ).body().tree(), ObjectNode.class );
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
			closer.push( Sniffer::close, this.sniffer );
			// The BeanHolder is responsible for calling close() on the client if necessary.
			closer.push( BeanHolder::close, this.restClientHolder );
		}
		catch (RuntimeException | IOException e) {
			throw ElasticsearchClientLog.INSTANCE.unableToShutdownClient( e.getMessage(), e );
		}
	}

}
