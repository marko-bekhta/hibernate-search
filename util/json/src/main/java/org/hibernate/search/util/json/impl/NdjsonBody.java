package org.hibernate.search.util.json.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.List;
import java.util.Objects;

import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonWriteCursor;
import org.hibernate.search.util.json.spi.JsonWriteStrategy;

import tools.jackson.databind.JsonNode;

/**
 * Internal framing; every traversal has its own workspace and shares it only between sequential parts.
 */
public final class NdjsonBody implements JsonBody {

	private final List<? extends JsonNode> parts;

	public NdjsonBody(List<? extends JsonNode> parts) {
		this.parts = parts;
	}

	@Override
	public JsonWriteCursor openCursor() {
		return new Cursor( parts );
	}

	@Override
	public void writeTo(OutputStream destination, JsonWriteStrategy strategy) throws IOException {
		Objects.requireNonNull( destination );
		if ( strategy == JsonWriteStrategy.NATIVE ) {
			for ( JsonNode part : parts ) {
				JacksonJsonBodyWriter.writeInternal( part, destination );
				destination.write( '\n' );
			}
		}
		else {
			JsonBody.super.writeTo( destination, strategy );
		}
	}

	private static final class Cursor implements JsonWriteCursor {

		private List<? extends JsonNode> parts;

		private int index;

		private JsonWriteCursor part;

		private JsonWriteWorkspace workspace;

		private boolean newline;

		private boolean complete;

		private boolean closed;

		private Cursor(List<? extends JsonNode> parts) {
			this.parts = parts;
			workspace = parts.isEmpty() ? null : new JsonWriteWorkspace();
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
			try {
				int start = destination.position();
				while ( destination.hasRemaining() && !complete ) {
					if ( newline ) {
						destination.put( (byte) '\n' );
						newline = false;
						if ( ++index == parts.size() ) {
							complete = true;
							parts = null;
							workspace = null;
						}
					}
					else {
						if ( part == null ) {
							part = new Utf8JsonWriteCursor( parts.get( index ), workspace );
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
			catch (RuntimeException failure) {
				close();
				throw failure;
			}
		}

		@Override
		public boolean isComplete() {
			return complete;
		}

		@Override
		public void close() {
			closed = true;
			parts = null;
			if ( workspace != null ) {
				workspace.clearReferences();
			}
			workspace = null;
			if ( part != null ) {
				part.close();
				part = null;
			}
		}
	}
}
