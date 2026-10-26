package org.hibernate.search.util.json.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import org.hibernate.search.util.common.annotation.Incubating;
import org.hibernate.search.util.json.impl.JsonBodyOutput;

/**
 * Replayable UTF-8 JSON output source. The underlying tree must remain unchanged from source
 * creation through consumption, including retries and signing reads. Trees must be acyclic;
 * trees and encoded payloads are not copied.
 * Sources may be shared; their cursors and streams are single-consumer state machines.
 */
@Incubating
public interface JsonBody {
	JsonWriteCursor openCursor();

	/**
	 * Stable length for this source/body attempt, or {@code -1} when unknown.
	 * Completing a cursor or signing read must not change this value.
	 */
	default long contentLength() {
		return -1;
	}

	/** Drain an independent cursor using an 8 KiB buffer; neither flush nor close the caller's stream. */
	default void writeTo(OutputStream destination) throws IOException {
		writeTo( destination, JsonWriteStrategy.CURSOR );
	}

	/**
	 * Write with the selected strategy; neither flush nor close the destination.
	 * Both strategies produce the same bytes. Custom bodies default to cursor-backed output
	 * for either strategy unless they override this method with a native writer.
	 */
	default void writeTo(OutputStream destination, JsonWriteStrategy strategy) throws IOException {
		Objects.requireNonNull( strategy );
		JsonBodyOutput.writeTo( this, destination );
	}

	/**
	 * Open an independent cursor-backed stream, suitable for synchronous signing and replay.
	 * Reads use bounded 8 KiB storage. Closing the stream cancels its cursor.
	 */
	default InputStream openStream() {
		return JsonBodyOutput.openStream( this );
	}
}
