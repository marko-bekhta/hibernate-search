package org.hibernate.search.util.json.spi;

import java.util.List;
import java.util.Objects;

import org.hibernate.search.util.common.impl.Contracts;
import org.hibernate.search.util.json.impl.JsonBodyOutput;
import org.hibernate.search.util.json.impl.NdjsonBody;
import org.hibernate.search.util.json.impl.Utf8JsonWriteCursor;

import tools.jackson.databind.JsonNode;

/**
 * Jackson-native output factories. Request output uses cursor-backed writing by default.
 */
public final class JsonBodies {

	public static final int DEFAULT_BUFFER_SIZE = 8192;

	private JsonBodies() {
	}

	/**
	 * Create a replayable source and probe at most 8 KiB for a small body's length.
	 * Strings/names escape JSON controls, HTML-sensitive characters and U+2028/U+2029 using
	 * lowercase hex. Valid surrogate pairs use UTF-8; isolated surrogates use Unicode escapes.
	 * Exact numeric values and object insertion order are preserved. No newline or HTTP framing is added.
	 */
	public static JsonBody of(JsonNode root) {
		Objects.requireNonNull( root );
		return withContentLengthProbe( () -> openCursor( root ), DEFAULT_BUFFER_SIZE );
	}

	/**
	 * Every retained part ends with one newline; probe the complete framed body once.
	 */
	public static JsonBody ndjson(List<? extends JsonNode> parts) {
		Contracts.assertNotNull( parts, "parts" );
		for ( JsonNode part : parts ) {
			Contracts.assertNotNull( part, "NDJSON part" );
		}
		return withContentLengthProbe( new NdjsonBody( parts ), DEFAULT_BUFFER_SIZE );
	}

	/**
	 * Open an independent traversal without any preliminary length probe.
	 */
	public static JsonWriteCursor openCursor(JsonNode root) {
		return new Utf8JsonWriteCursor( root );
	}

	/**
	 * Probe at most {@code maximumBytes}, once, without retaining the probe's encoded bytes.
	 * An incomplete probe records {@code -1} permanently; subsequent traversal cannot change it.
	 * Use this on independently framed sources as well as individual JSON values.
	 */
	public static JsonBody withContentLengthProbe(JsonBody source, int maximumBytes) {
		Objects.requireNonNull( source );
		if ( maximumBytes < 0 ) {
			throw new IllegalArgumentException( "Negative probe limit" );
		}
		long length = JsonBodyOutput.probeLength( source, maximumBytes );
		return new JsonBody() {

			@Override
			public JsonWriteCursor openCursor() {
				return source.openCursor();
			}

			@Override
			public long contentLength() {
				return length;
			}
		};
	}
}
