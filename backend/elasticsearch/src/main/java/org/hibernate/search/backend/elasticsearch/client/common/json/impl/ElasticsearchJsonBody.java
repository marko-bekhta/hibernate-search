package org.hibernate.search.backend.elasticsearch.client.common.json.impl;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.List;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

/**
 * Elasticsearch body-part framing shared by the synchronous client entity adapters. Every part has one final newline; an empty list emits no bytes.
 */
public final class ElasticsearchJsonBody {
	private ElasticsearchJsonBody() {
	}

	/** Retain the submitted list and trees by reference; they must remain unchanged across retries. */
	public static JsonBody ndjson(List<? extends JsonElement> parts) {
		return JsonBodies.withContentLengthProbe( () -> new Cursor( parts ), JsonBodies.DEFAULT_BUFFER_SIZE );
	}

	private static final class Cursor implements JsonWriteCursor {
		private List<? extends JsonElement> parts;
		private int index;
		private JsonWriteCursor part;
		private boolean newline;
		private boolean complete;
		private boolean closed;

		private Cursor(List<? extends JsonElement> parts) {
			this.parts = parts;
			complete = parts.isEmpty();
			if ( complete ) {
				this.parts = null;
			}
		}

		@Override
		public int write(ByteBuffer destination) {
			if ( closed ) {
				throw new IllegalStateException( "Elasticsearch JSON body cursor is closed" );
			}
			if ( destination.isReadOnly() ) {
				throw new ReadOnlyBufferException();
			}
			int start = destination.position();
			while ( destination.hasRemaining() && !complete ) {
				if ( newline ) {
					destination.put( (byte) '\n' );
					newline = false;
					if ( ++index == parts.size() ) {
						complete = true;
						parts = null;
					}
				}
				else {
					if ( part == null ) {
						part = JsonBodies.openCursor( parts.get( index ) );
					}
					part.write( destination );
					if ( part.isComplete() ) {
						part.close();
						part = null;
						newline = true;
					}
				}
			}
			return destination.position() - start;
		}

		@Override
		public boolean isComplete() {
			return complete;
		}

		@Override
		public void close() {
			closed = true;
			parts = null;
			if ( part != null ) {
				part.close();
				part = null;
			}
		}
	}
}
