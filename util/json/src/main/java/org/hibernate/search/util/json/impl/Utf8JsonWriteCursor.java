package org.hibernate.search.util.json.impl;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.hibernate.search.util.common.AssertionFailure;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

/** Iterative traversal retaining only source references and at most one encoded character/escape. */
public final class Utf8JsonWriteCursor implements JsonWriteCursor {
	private static final String HEX = "0123456789abcdef";
	private static final boolean[] SAFE_ASCII = safeAscii();

	private static boolean[] safeAscii() {
		boolean[] result = new boolean[128];
		java.util.Arrays.fill( result, 0x20, 128, true );
		for ( char character : new char[] { '"', '\\', '<', '>', '&', '=', '\'' } ) {
			result[character] = false;
		}
		return result;
	}

	private ArrayDeque<Frame> frames = new ArrayDeque<>();
	private JsonElement nextValue;
	private String scalar;
	private int scalarOffset;
	// Raw scalars start at stage 1; quoted scalars have opening/content/closing stages 0/1/2.
	private boolean quoted;
	private int scalarStage;
	private byte[] pending = new byte[6];
	private int pendingOffset;
	private int pendingLength;
	private boolean complete;
	private boolean closed;

	public Utf8JsonWriteCursor(JsonElement root) {
		nextValue = Objects.requireNonNull( root );
	}

	@Override
	public int write(ByteBuffer destination) {
		if ( closed ) {
			throw new IllegalStateException( "JSON write cursor is closed" );
		}
		if ( destination.isReadOnly() ) {
			throw new ReadOnlyBufferException();
		}
		int start = destination.position();
		while ( destination.hasRemaining() && !complete ) {
			if ( pendingOffset == pendingLength ) {
				pendingOffset = pendingLength = 0;
				if ( writeAsciiRun( destination ) ) {
					if ( scalar == null && nextValue == null && frames.isEmpty() ) {
						complete = true;
						pending = null;
						frames = null;
					}
					continue;
				}
				prepare();
			}
			int count = Math.min( destination.remaining(), pendingLength - pendingOffset );
			destination.put( pending, pendingOffset, count );
			pendingOffset += count;
			if ( pendingOffset == pendingLength && scalar == null && nextValue == null && frames.isEmpty() ) {
				complete = true;
				pending = null;
				frames = null;
			}
		}
		return destination.position() - start;
	}

	/** Copy only the caller's available region; no encoded token or body is materialized. */
	private boolean writeAsciiRun(ByteBuffer destination) {
		if ( scalar == null || scalarStage != 1 || scalarOffset == scalar.length() ) {
			return false;
		}
		int end = scalarOffset + Math.min( destination.remaining(), scalar.length() - scalarOffset );
		int offset = scalarOffset;
		if ( destination.hasArray() ) {
			byte[] array = destination.array();
			int position = destination.arrayOffset() + destination.position();
			while ( offset < end ) {
				char character = scalar.charAt( offset );
				if ( quoted && ( character >= 128 || !SAFE_ASCII[character] ) ) {
					break;
				}
				array[position++] = (byte) character;
				offset++;
			}
			destination.position( destination.position() + offset - scalarOffset );
		}
		else {
			while ( offset < end ) {
				char character = scalar.charAt( offset );
				if ( quoted && ( character >= 128 || !SAFE_ASCII[character] ) ) {
					break;
				}
				destination.put( (byte) character );
				offset++;
			}
		}
		if ( offset == scalarOffset ) {
			return false;
		}
		scalarOffset = offset;
		if ( offset == scalar.length() ) {
			if ( quoted ) {
				scalarStage = 2;
			}
			else {
				scalar = null;
			}
		}
		return true;
	}

	private void prepare() {
		while ( pendingLength == 0 ) {
			if ( scalar != null ) {
				prepareScalar();
			}
			else if ( nextValue != null ) {
				JsonElement value = nextValue;
				nextValue = null;
				if ( value.isJsonObject() ) {
					frames.push( new Frame( value.getAsJsonObject().entrySet().iterator(), true ) );
					ascii( '{' );
				}
				else if ( value.isJsonArray() ) {
					frames.push( new Frame( value.getAsJsonArray().iterator(), false ) );
					ascii( '[' );
				}
				else if ( value.isJsonNull() ) {
					scalar( "null", false );
				}
				else {
					JsonPrimitive primitive = value.getAsJsonPrimitive();
					scalar( primitive.getAsString(), primitive.isString() );
				}
			}
			else {
				prepareFrame();
			}
		}
	}

	private void prepareFrame() {
		Frame frame = frames.peek();
		switch ( frame.stage ) {
			case 0:
				if ( !frame.iterator.hasNext() ) {
					frames.pop();
					ascii( frame.object ? '}' : ']' );
					return;
				}
				Object next = frame.iterator.next();
				if ( frame.object ) {
					Map.Entry<?, ?> entry = (Map.Entry<?, ?>) next;
					frame.name = (String) entry.getKey();
					frame.value = (JsonElement) entry.getValue();
				}
				else {
					frame.value = (JsonElement) next;
				}
				frame.stage = 1;
				if ( frame.first ) {
					frame.first = false;
				}
				else {
					ascii( ',' );
				}
				break;
			case 1:
				if ( frame.object ) {
					scalar( frame.name, true );
					frame.name = null;
					frame.stage = 2;
					break;
				}
				selectValue( frame );
				break;
			case 2:
				ascii( ':' );
				frame.stage = 3;
				break;
			case 3:
				selectValue( frame );
				break;
			default:
				throw new AssertionFailure( "Unexpected output frame stage: " + frame.stage );
		}
	}

	private void selectValue(Frame frame) {
		nextValue = frame.value;
		frame.value = null;
		frame.stage = 0;
	}

	private void scalar(String value, boolean quoted) {
		scalar = value;
		scalarOffset = 0;
		this.quoted = quoted;
		scalarStage = quoted ? 0 : 1;
	}

	private void prepareScalar() {
		if ( scalarStage == 0 ) {
			ascii( '"' );
			scalarStage = 1;
		}
		else if ( scalarStage == 2 ) {
			ascii( '"' );
			scalar = null;
		}
		else if ( scalarOffset == scalar.length() ) {
			// Empty strings still have a closing quote.
			scalarStage = 2;
		}
		else {
			char character = scalar.charAt( scalarOffset++ );
			if ( !quoted ) {
				// Number lexemes are validated once by JsonPrimitive, never rescanned during output.
				ascii( character );
			}
			else {
				prepareCharacter( character );
			}
			if ( scalarOffset == scalar.length() ) {
				if ( quoted ) {
					scalarStage = 2;
				}
				else {
					scalar = null;
				}
			}
		}
	}

	private void prepareCharacter(char character) {
		switch ( character ) {
			case '"':
			case '\\':
				escape( character );
				return;
			case '\b':
				escape( 'b' );
				return;
			case '\f':
				escape( 'f' );
				return;
			case '\n':
				escape( 'n' );
				return;
			case '\r':
				escape( 'r' );
				return;
			case '\t':
				escape( 't' );
				return;
			default:
				break;
		}
		if ( character < 0x20
				|| character == '<' || character == '>' || character == '&'
				|| character == '=' || character == '\'' || character == 0x2028 || character == 0x2029 ) {
			unicodeEscape( character );
		}
		else if ( Character.isHighSurrogate( character )
				&& scalarOffset < scalar.length()
				&& Character.isLowSurrogate( scalar.charAt( scalarOffset ) ) ) {
			int codePoint = Character.toCodePoint( character, scalar.charAt( scalarOffset++ ) );
			pending[0] = (byte) ( 0xf0 | codePoint >> 18 );
			pending[1] = (byte) ( 0x80 | codePoint >> 12 & 0x3f );
			pending[2] = (byte) ( 0x80 | codePoint >> 6 & 0x3f );
			pending[3] = (byte) ( 0x80 | codePoint & 0x3f );
			pendingLength = 4;
		}
		else if ( Character.isSurrogate( character ) ) {
			unicodeEscape( character );
		}
		else if ( character < 0x80 ) {
			ascii( character );
		}
		else if ( character < 0x800 ) {
			pending[0] = (byte) ( 0xc0 | character >> 6 );
			pending[1] = (byte) ( 0x80 | character & 0x3f );
			pendingLength = 2;
		}
		else {
			pending[0] = (byte) ( 0xe0 | character >> 12 );
			pending[1] = (byte) ( 0x80 | character >> 6 & 0x3f );
			pending[2] = (byte) ( 0x80 | character & 0x3f );
			pendingLength = 3;
		}
	}

	private void ascii(char character) {
		pending[0] = (byte) character;
		pendingLength = 1;
	}

	private void escape(char character) {
		pending[0] = '\\';
		pending[1] = (byte) character;
		pendingLength = 2;
	}

	private void unicodeEscape(char character) {
		pending[0] = '\\';
		pending[1] = 'u';
		for ( int i = 0; i < 4; i++ ) {
			pending[i + 2] = (byte) HEX.charAt( character >> ( 12 - i * 4 ) & 0xf );
		}
		pendingLength = 6;
	}

	@Override
	public boolean isComplete() {
		return complete;
	}

	@Override
	public void close() {
		closed = true;
		frames = null;
		nextValue = null;
		scalar = null;
		pending = null;
		pendingOffset = pendingLength = 0;
	}

	// Instrumentation separates encoded retention from references into the caller's tree.
	int retainedEncodedCapacity() {
		return pending == null ? 0 : pending.length;
	}

	int retainedTraversalDepth() {
		return frames == null ? 0 : frames.size();
	}

	boolean retainsScalarOrNextValue() {
		return scalar != null || nextValue != null;
	}

	private static final class Frame {
		private final Iterator<?> iterator;
		private final boolean object;
		private boolean first = true;
		private int stage;
		private String name;
		private JsonElement value;

		private Frame(Iterator<?> iterator, boolean object) {
			this.iterator = iterator;
			this.object = object;
		}
	}
}
