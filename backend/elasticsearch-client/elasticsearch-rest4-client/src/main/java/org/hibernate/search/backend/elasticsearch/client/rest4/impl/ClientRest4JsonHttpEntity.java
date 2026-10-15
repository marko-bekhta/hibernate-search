package org.hibernate.search.backend.elasticsearch.client.rest4.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.ContentEncoder;
import org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi.JsonHttpEntityContentProvider;
import org.hibernate.search.backend.elasticsearch.client.common.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.HttpAsyncContentProducer;
import org.apache.http.protocol.HTTP;
import tools.jackson.databind.node.ObjectNode;

/**
 * Replayable JSON entity using one bounded pending cursor buffer per asynchronous attempt.
 * Partial/zero transport acceptance retains the unread suffix until output readiness resumes.
 * A bounded independent probe reports small content lengths; large bodies remain chunked.
 * Signing and synchronous reads each open independent cursors. Close/release resets attempt state.
 */
final class ClientRest4JsonHttpEntity extends JsonHttpEntityContentProvider implements HttpEntity, HttpAsyncContentProducer {

	private static final BasicHeader CONTENT_TYPE =
			new BasicHeader( HTTP.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString() );


	public static HttpEntity toEntity(JsonCodecService json, ElasticsearchRequest request) throws IOException {
		final List<ObjectNode> bodyParts = request.bodyPartsJson();
		if ( bodyParts.isEmpty() ) {
			return null;
		}
		return new ClientRest4JsonHttpEntity( json, bodyParts );
	}

	public ClientRest4JsonHttpEntity(JsonCodecService json, List<ObjectNode> bodyParts) throws IOException {
		super( json, bodyParts );
	}

	@Override
	public boolean isRepeatable() {
		return !isCancelled();
	}

	@Override
	public boolean isChunked() {
		return false;
	}

	@Override
	public Header getContentType() {
		return CONTENT_TYPE;
	}

	@Override
	public Header getContentEncoding() {
		//Apparently this is the correct value:
		return null;
	}

	@Override
	public boolean isStreaming() {
		return false;
	}

	@Override
	@SuppressWarnings("deprecation") // javac warns about this method being deprecated, but we have to implement it
	public void consumeContent() {
		//not used (and deprecated)
	}

	@Override
	public void produceContent(org.apache.http.nio.ContentEncoder encoder, IOControl ioctrl) throws IOException {
		try {
			produceContent( toJsonContentEncoder( encoder ) );
			if ( availableBytes() > 0 ) {
				ioctrl.requestOutput();
			}
		}
		catch (IOException | RuntimeException e) {
			close();
			throw e;
		}
	}

	private ContentEncoder toJsonContentEncoder(org.apache.http.nio.ContentEncoder encoder) {
		if ( encoder instanceof ContentEncoder gce ) {
			return gce;
		}
		else {
			return new ClientRest4ContentEncoder( encoder );
		}
	}

	private static class ClientRest4ContentEncoder implements ContentEncoder {

		private final org.apache.http.nio.ContentEncoder encoder;

		public ClientRest4ContentEncoder(org.apache.http.nio.ContentEncoder encoder) {
			this.encoder = encoder;
		}

		@Override
		public int write(ByteBuffer src) throws IOException {
			return encoder.write( src );
		}

		@Override
		public void complete() throws IOException {
			encoder.complete();
		}

		@Override
		public boolean isCompleted() {
			return encoder.isCompleted();
		}
	}
}
