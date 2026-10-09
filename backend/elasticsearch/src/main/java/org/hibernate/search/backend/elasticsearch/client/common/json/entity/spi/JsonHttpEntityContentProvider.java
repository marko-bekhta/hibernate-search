package org.hibernate.search.backend.elasticsearch.client.common.json.entity.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.ElasticsearchJsonBody;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

/** Bounded cursor adapter preserving the existing synchronous entity lifecycle. */
public class JsonHttpEntityContentProvider implements ContentProducer {
	private final JsonBody body;
	private JsonWriteCursor cursor;
	private final ByteBuffer pending = ByteBuffer.allocate( 8192 );

	public JsonHttpEntityContentProvider(JsonCodecService codec, List<JsonObject> bodyParts) throws IOException {
		java.util.Objects.requireNonNull( codec );
		body = ElasticsearchJsonBody.ndjson( bodyParts );
		pending.limit( 0 );
	}

	public long getContentLength() {
		return body.contentLength();
	}

	public InputStream getContent() {
		return body.openStream();
	}

	public void writeTo(OutputStream out) throws IOException {
		body.writeTo( out );
	}

	public void close() {
		if ( cursor != null ) {
			cursor.close();
			cursor = null;
		}
		pending.clear();
		pending.limit( 0 );
	}

	public void produceContent(ContentEncoder encoder) throws IOException {
		if ( cursor == null ) {
			cursor = body.openCursor();
		}
		while ( true ) {
			if ( pending.hasRemaining() ) {
				int accepted = encoder.write( pending.slice() );
				if ( accepted < 0 || accepted > pending.remaining() ) {
					throw new IOException( "Invalid encoded byte count: " + accepted );
				}
				pending.position( pending.position() + accepted );
				if ( pending.hasRemaining() ) {
					return;
				}
			}
			if ( cursor.isComplete() ) {
				encoder.complete();
				return;
			}
			pending.clear();
			cursor.write( pending );
			pending.flip();
		}
	}
}
