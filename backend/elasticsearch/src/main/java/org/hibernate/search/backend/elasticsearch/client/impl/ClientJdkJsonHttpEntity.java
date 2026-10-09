package org.hibernate.search.backend.elasticsearch.client.impl;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

import org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.JsonHttpEntityContentProvider;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.util.json.JsonObject;

public class ClientJdkJsonHttpEntity extends JsonHttpEntityContentProvider implements HttpRequest.BodyPublisher {

	public static HttpRequest.BodyPublisher toEntity(JsonCodecService json, ElasticsearchRequest request) throws IOException {
		final List<JsonObject> bodyParts = request.bodyParts();
		if ( bodyParts.isEmpty() ) {
			return HttpRequest.BodyPublishers.noBody();
		}
		return new ClientJdkJsonHttpEntity( json, bodyParts );
	}

	private final HttpRequest.BodyPublisher delegate;

	public ClientJdkJsonHttpEntity(JsonCodecService json, List<JsonObject> bodyParts) throws IOException {
		super( json, bodyParts );
		delegate = HttpRequest.BodyPublishers.ofInputStream( this::getContent );
	}

	@Override
	public long contentLength() {
		return getContentLength();
	}

	@Override
	public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
		delegate.subscribe( subscriber );
	}

	static boolean isNoBodyPublisher(HttpRequest.BodyPublisher bodyPublisher) {
		return bodyPublisher.contentLength() == 0;
	}
}
