package org.hibernate.search.util.json.spi;

import org.hibernate.search.util.common.annotation.Incubating;

/** Central parser constraints. Zero byte limit means unlimited, appropriate for the existing JDK client.
 * REST adapters must opt into their existing 100 MiB limit. Limits count incoming bytes before transcoding.
 * Token limits constrain parser/tree work separately from transport body limits.
 */
@Incubating
public final class JsonParsingOptions {
	/** Default maximum bytes accepted by one parser feed. */
	public static final int DEFAULT_MAX_INPUT_CHUNK_SIZE = 8192;

	public static final JsonParsingOptions DEFAULT =
			new JsonParsingOptions( 1000, 20_000_000, 50_000, 1000, DEFAULT_MAX_INPUT_CHUNK_SIZE, 0 );
	private final int maxDepth;
	private final int maxStringLength;
	private final int maxNameLength;
	private final int maxNumberLength;
	private final int maxInputChunkSize;
	private final long maxInputBytes;

	public JsonParsingOptions(int maxDepth, int maxStringLength, int maxNameLength, int maxNumberLength,
			int maxInputChunkSize, long maxInputBytes) {
		if ( maxDepth <= 0
				|| maxStringLength <= 0 || maxNameLength <= 0 || maxNumberLength <= 0
				|| maxInputChunkSize <= 0 || maxInputBytes < 0 ) {
			throw new IllegalArgumentException( "Invalid JSON parsing constraints" );
		}
		this.maxDepth = maxDepth;
		this.maxStringLength = maxStringLength;
		this.maxNameLength = maxNameLength;
		this.maxNumberLength = maxNumberLength;
		this.maxInputChunkSize = maxInputChunkSize;
		this.maxInputBytes = maxInputBytes;
	}

	public JsonParsingOptions withMaxInputBytes(long limit) {
		return new JsonParsingOptions( maxDepth, maxStringLength, maxNameLength, maxNumberLength, maxInputChunkSize, limit );
	}

	public int maxDepth() {
		return maxDepth;
	}

	public int maxStringLength() {
		return maxStringLength;
	}

	public int maxNameLength() {
		return maxNameLength;
	}

	public int maxNumberLength() {
		return maxNumberLength;
	}

	public int maxInputChunkSize() {
		return maxInputChunkSize;
	}

	public long maxInputBytes() {
		return maxInputBytes;
	}
}
