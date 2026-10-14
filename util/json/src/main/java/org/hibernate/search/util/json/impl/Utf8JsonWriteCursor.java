package org.hibernate.search.util.json.impl;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.hibernate.search.util.json.spi.JsonWriteCursor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.StringNode;

/**
 * Iterative traversal with six encoded pending bytes. Primitive numeric fallback uses a separate
 * fixed twenty-character workspace; BigInteger/decimal renderings can retain text proportional to digits.
 */
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

	private Frame top;

	private int depth;

	private JsonNode nextValue;

	private JsonWriteWorkspace workspace;

	private int integralOffset;

	private int integralLength;

	// -1 length queues a primitive value; positive length resumes the fixed-character fallback.
	private long integralValue;

	private String scalar;

	private boolean generatedNumericScalar;

	private int scalarOffset;

	// Raw scalars start at stage 1; quoted scalars have opening/content/closing stages 0/1/2.
	private boolean quoted;

	private int scalarStage;

	private byte[] pending = new byte[6];

	private int pendingOffset;

	private int pendingLength;

	private boolean complete;

	private boolean closed;

	public Utf8JsonWriteCursor(JsonNode root) {
		this( root, null );
	}

	Utf8JsonWriteCursor(JsonNode root, JsonWriteWorkspace workspace) {
		nextValue = Objects.requireNonNull( root );
		this.workspace = workspace;
	}

	@Override
	public int write(ByteBuffer destination) {
		if ( closed ) {
			throw new IllegalStateException( "JSON write cursor is closed" );
		}
		if ( destination.isReadOnly() ) {
			throw new ReadOnlyBufferException();
		}
		try {
			int start = destination.position();
			while ( destination.hasRemaining() && !complete ) {
				if ( pendingOffset == pendingLength ) {
					pendingOffset = pendingLength = 0;
					if ( writeIntegralRun( destination ) || writeAsciiRun( destination ) || writeUnicodeRun( destination ) ) {
						if ( !hasScalar() && nextValue == null && top == null ) {
							releaseCompleted();
						}
						continue;
					}
					prepare();
					if ( pendingLength == 0 ) {
						continue;
					}
				}
				int count = Math.min( destination.remaining(), pendingLength - pendingOffset );
				destination.put( pending, pendingOffset, count );
				pendingOffset += count;
				if ( pendingOffset == pendingLength && !hasScalar() && nextValue == null && top == null ) {
					releaseCompleted();
				}
			}
			return destination.position() - start;
		}
		catch (RuntimeException failure) {
			close();
			throw failure;
		}
	}

	private boolean hasScalar() {
		return scalar != null || integralLength != 0;
	}

	private void releaseCompleted() {
		complete = true;
		pending = null;
		top = null;
		depth = 0;
		workspace = null;
	}

	/**
	 * Render complete primitives into caller storage; tiny/direct destinations resume bounded characters.
	 */
	private boolean writeIntegralRun(ByteBuffer destination) {
		if ( integralLength == 0 ) {
			return false;
		}
		if ( integralLength == -1 ) {
			long value = integralValue;
			boolean fitsInt = value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
			// These conservative bounds avoid a digit-count pass; common small values use tighter bounds.
			int required = fitsInt ? 11 : 20;
			if ( value >= 0 && value < 100 ) {
				required = value < 10 ? 1 : 2;
			}
			else if ( value < 0 && value > -100 ) {
				required = value > -10 ? 2 : 3;
			}
			if ( destination.hasArray() && destination.remaining() >= required ) {
				int start = destination.arrayOffset() + destination.position();
				int end = fitsInt
						? tools.jackson.core.io.NumberOutput.outputInt( (int) value, destination.array(), start )
						: tools.jackson.core.io.NumberOutput.outputLong( value, destination.array(), start );
				destination.position( destination.position() + end - start );
				integralValue = 0;
				integralLength = 0;
				return true;
			}
			char[] characters = workspace().integral();
			integralLength = fitsInt
					? tools.jackson.core.io.NumberOutput.outputInt( (int) value, characters, 0 )
					: tools.jackson.core.io.NumberOutput.outputLong( value, characters, 0 );
			integralValue = 0;
			integralOffset = 0;
		}
		int count = Math.min( destination.remaining(), integralLength - integralOffset );
		char[] characters = workspace.integral;
		if ( destination.hasArray() ) {
			byte[] bytes = destination.array();
			int start = destination.arrayOffset() + destination.position();
			for ( int index = 0; index < count; index++ ) {
				bytes[start + index] = (byte) characters[integralOffset + index];
			}
			destination.position( destination.position() + count );
		}
		else {
			for ( int index = 0; index < count; index++ ) {
				destination.put( (byte) characters[integralOffset + index] );
			}
		}
		integralOffset += count;
		if ( integralOffset == integralLength ) {
			integralOffset = integralLength = 0;
		}
		return count != 0;
	}

	private JsonWriteWorkspace workspace() {
		if ( workspace == null ) {
			workspace = new JsonWriteWorkspace();
		}
		return workspace;
	}

	private void integral(JsonNode value) {
		integralValue = value.longValue();
		integralLength = -1;
	}

	/**
	 * Copy only the caller's available region; no encoded token or body is materialized.
	 */
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

	/**
	 * Encode complete units directly into caller storage; the last partial unit uses pending.
	 */
	private boolean writeUnicodeRun(ByteBuffer destination) {
		if ( scalar == null || !quoted || scalarStage != 1 || !destination.hasArray() || destination.remaining() < 6 ) {
			return false;
		}
		byte[] array = destination.array();
		int base = destination.arrayOffset();
		int position = base + destination.position();
		int end = base + destination.limit() - 6;
		int offset = scalarOffset;
		String text = scalar;
		while ( position <= end && offset < text.length() ) {
			char character = text.charAt( offset++ );
			if ( character < 128 && SAFE_ASCII[character] ) {
				array[position++] = (byte) character;
				continue;
			}
			char escape = switch ( character ) {
				case '"', '\\' ->
						character;
				case '\b' ->
						'b';
				case '\f' ->
						'f';
				case '\n' ->
						'n';
				case '\r' ->
						'r';
				case '\t' ->
						't';
				default ->
						0;
			};
			if ( escape != 0 ) {
				array[position++] = '\\';
				array[position++] = (byte) escape;
			}
			else if ( character < 128
					|| character == 0x2028 || character == 0x2029
					|| Character.isSurrogate( character )
							&& !( Character.isHighSurrogate( character )
									&& offset < text.length() && Character.isLowSurrogate( text.charAt( offset ) ) ) ) {
				array[position++] = '\\';
				array[position++] = 'u';
				array[position++] = (byte) HEX.charAt( character >> 12 & 0xf );
				array[position++] = (byte) HEX.charAt( character >> 8 & 0xf );
				array[position++] = (byte) HEX.charAt( character >> 4 & 0xf );
				array[position++] = (byte) HEX.charAt( character & 0xf );
			}
			else if ( character < 0x800 ) {
				array[position++] = (byte) ( 0xc0 | character >> 6 );
				array[position++] = (byte) ( 0x80 | character & 0x3f );
			}
			else if ( Character.isHighSurrogate( character ) ) {
				int codePoint = Character.toCodePoint( character, text.charAt( offset++ ) );
				array[position++] = (byte) ( 0xf0 | codePoint >> 18 );
				array[position++] = (byte) ( 0x80 | codePoint >> 12 & 0x3f );
				array[position++] = (byte) ( 0x80 | codePoint >> 6 & 0x3f );
				array[position++] = (byte) ( 0x80 | codePoint & 0x3f );
			}
			else {
				array[position++] = (byte) ( 0xe0 | character >> 12 );
				array[position++] = (byte) ( 0x80 | character >> 6 & 0x3f );
				array[position++] = (byte) ( 0x80 | character & 0x3f );
			}
		}
		destination.position( position - base );
		scalarOffset = offset;
		if ( offset == text.length() ) {
			scalarStage = 2;
		}
		return true;
	}

	private void prepare() {
		while ( pendingLength == 0 ) {
			if ( scalar != null ) {
				prepareScalar();
			}
			else if ( nextValue != null ) {
				JsonNode value = nextValue;
				nextValue = null;
				if ( value.getClass() == tools.jackson.databind.node.ObjectNode.class ) {
					enterContainer( value );
					top = new Frame( value, value.properties().iterator(), true, top );
					depth++;
					ascii( '{' );
				}
				else if ( value.getClass() == tools.jackson.databind.node.ArrayNode.class ) {
					enterContainer( value );
					top = new Frame( value, value.iterator(), false, top );
					depth++;
					ascii( '[' );
				}
				else if ( value.getClass() == tools.jackson.databind.node.NullNode.class ) {
					scalar( "null", false );
				}
				else {
					if ( value.getClass() == tools.jackson.databind.node.StringNode.class ) {
						scalar( value.asString(), true );
					}
					else if ( value.getClass() == tools.jackson.databind.node.BooleanNode.class ) {
						scalar( value.asBoolean() ? "true" : "false", false );
					}
					else if ( value.getClass() == tools.jackson.databind.node.IntNode.class
							|| value.getClass() == tools.jackson.databind.node.ShortNode.class
							|| value.getClass() == tools.jackson.databind.node.LongNode.class ) {
						integral( value );
						return;
					}
					else if ( value.isNumber() ) {
						scalar( JsonNumbers.render( value ), false );
						generatedNumericScalar = true;
					}
					else {
						throw new IllegalArgumentException( "Unsupported JSON node: " + value.getClass().getName() );
					}
				}
			}
			else {
				prepareFrame();
			}
		}
	}

	private void enterContainer(JsonNode value) {
		// Bound the allocation-free path walk; deep trees switch to identity hashing.
		if ( workspace != null && workspace.active != null ) {
			if ( workspace.active.put( value, Boolean.TRUE ) != null ) {
				failCycle();
			}
			return;
		}
		if ( depth < 16 ) {
			for ( Frame ancestor = top; ancestor != null; ancestor = ancestor.parent ) {
				if ( ancestor.container == value ) {
					failCycle();
				}
			}
			return;
		}
		JsonWriteWorkspace state = workspace();
		state.active = new java.util.IdentityHashMap<>( depth + 1 );
		for ( Frame ancestor = top; ancestor != null; ancestor = ancestor.parent ) {
			state.active.put( ancestor.container, Boolean.TRUE );
		}
		if ( state.active.put( value, Boolean.TRUE ) != null ) {
			failCycle();
		}
	}

	private void failCycle() {
		close();
		throw new IllegalArgumentException( "Cyclic JSON tree" );
	}

	private void prepareFrame() {
		Frame frame = top;
		switch ( frame.stage ) {
			case 0:
				if ( !frame.iterator.hasNext() ) {
					top = frame.parent;
					depth--;
					if ( workspace != null && workspace.active != null ) {
						workspace.active.remove( frame.container );
					}
					ascii( frame.object ? '}' : ']' );
					return;
				}
				Object next = frame.iterator.next();
				if ( frame.object ) {
					Map.Entry<?, ?> entry = (Map.Entry<?, ?>) next;
					if ( !( entry.getKey() instanceof String name ) ) {
						throw new IllegalArgumentException( "JSON object member name must be a String" );
					}
					frame.name = name;
					next = entry.getValue();
				}
				if ( !( next instanceof JsonNode value ) ) {
					throw new IllegalArgumentException( "JSON container value must be a non-null JsonNode" );
				}
				frame.value = value;
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
				throw new IllegalStateException( "Unexpected output frame stage: " + frame.stage );
		}
	}

	private void selectValue(Frame frame) {
		nextValue = frame.value;
		frame.value = null;
		frame.stage = 0;
	}

	private void scalar(String value, boolean quoted) {
		scalar = value;
		generatedNumericScalar = false;
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
				// Numeric content comes from approved standard finite Jackson nodes.
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
				|| character == '<' || character == '>' || character == '&' || character == '=' || character == '\''
				|| character == 0x2028 || character == 0x2029 ) {
			unicodeEscape( character );
		}
		else if ( Character.isHighSurrogate( character )
				&& scalarOffset < scalar.length() && Character.isLowSurrogate( scalar.charAt( scalarOffset ) ) ) {
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
		top = null;
		depth = 0;
		if ( workspace != null ) {
			workspace.clearReferences();
		}
		workspace = null;
		integralOffset = integralLength = 0;
		integralValue = 0;
		nextValue = null;
		scalar = null;
		pending = null;
		pendingOffset = pendingLength = 0;
	}

	// Instrumentation separates encoded retention from references into the caller's tree.
	public int retainedEncodedCapacity() {
		return pending == null ? 0 : pending.length;
	}

	public int retainedTraversalDepth() {
		return depth;
	}

	/**
	 * Unencoded numeric text retained here; a numeric source value may also cache its rendering.
	 */
	public int retainedGeneratedNumericCharacters() {
		return scalar != null && generatedNumericScalar ? scalar.length() : 0;
	}

	public int retainedIntegralCharacterCapacity() {
		return workspace == null || workspace.integral == null ? 0 : workspace.integral.length;
	}

	public int retainedCycleEntries() {
		return workspace == null || workspace.active == null ? 0 : workspace.active.size();
	}

	public boolean retainsScalarOrNextValue() {
		return hasScalar() || nextValue != null;
	}

	private static final class Frame {

		private final JsonNode container;

		private final Frame parent;

		private final Iterator<?> iterator;

		private final boolean object;

		private boolean first = true;

		private int stage;

		private String name;

		private JsonNode value;

		private Frame(JsonNode container, Iterator<?> iterator, boolean object, Frame parent) {
			this.parent = parent;
			this.container = container;
			this.iterator = iterator;
			this.object = object;
		}
	}
}
