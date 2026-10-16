package org.hibernate.search.backend.elasticsearch.client.common.json.impl;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.hibernate.search.util.json.spi.JsonParseException;

/** Incremental gzip framing for clients whose response consumer precedes decompression. */
public final class JsonGzipDecoder implements AutoCloseable {
	private static final int OUTPUT_BUFFER_SIZE = 8192;

	private final JsonResponseBody body;
	private final Inflater inflater = new Inflater( true );
	private final CRC32 crc = new CRC32();
	private final CRC32 headerCrc = new CRC32();
	private byte[] output = new byte[OUTPUT_BUFFER_SIZE];
	private int state;
	private int offset;
	private int flags;
	private int extraLength;
	private long trailer;
	private boolean memberCompleted;

	public JsonGzipDecoder(JsonResponseBody body) {
		this.body = body;
	}

	public void feed(ByteBuffer input) {
		try {
			while ( input.hasRemaining() ) {
				if ( state == 6 ) {
					inflater.setInput( input );
					while ( !inflater.needsInput() ) {
						int count = inflater.inflate( output );
						if ( count > 0 ) {
							crc.update( output, 0, count );
							body.feed( ByteBuffer.wrap( output, 0, count ) );
						}
						if ( inflater.finished() ) {
							state = 7;
							offset = 0;
							trailer = 0;
							break;
						}
						if ( count == 0 && !inflater.needsInput() ) {
							throw new JsonParseException( "Invalid gzip deflate stream" );
						}
					}
					continue;
				}
				int value = Byte.toUnsignedInt( input.get() );
				if ( state < 5 ) {
					headerCrc.update( value );
				}
				switch ( state ) {
					case 0:
						if ( ( offset == 0 && value != 31 )
								|| ( offset == 1 && value != 139 )
								|| ( offset == 2 && value != 8 ) || ( offset == 3 && ( value & 224 ) != 0 ) ) {
							throw new JsonParseException( "Invalid gzip header" );
						}
						if ( offset == 3 ) {
							flags = value;
						}
						if ( ++offset == 10 ) {
							nextHeaderState();
						}
						break;
					case 1:
						extraLength |= value << ( 8 * offset++ );
						if ( offset == 2 ) {
							state = 2;
							if ( extraLength == 0 ) {
								nextHeaderState();
							}
						}
						break;
					case 2:
						if ( --extraLength == 0 ) {
							nextHeaderState();
						}
						break;
					case 3:
					case 4:
						if ( value == 0 ) {
							nextHeaderState();
						}
						break;
					case 5:
						trailer |= (long) value << ( 8 * offset++ );
						if ( offset == 2 ) {
							if ( trailer != ( headerCrc.getValue() & 65535 ) ) {
								throw new JsonParseException( "Invalid gzip header checksum" );
							}
							state = 6;
						}
						break;
					case 7:
						trailer |= (long) value << ( 8 * ( offset++ % 4 ) );
						if ( offset == 4 ) {
							if ( trailer != crc.getValue() ) {
								throw new JsonParseException( "Invalid gzip checksum" );
							}
							trailer = 0;
						}
						if ( offset == 8 ) {
							if ( trailer != ( inflater.getBytesWritten() & 0xffffffffL ) ) {
								throw new JsonParseException( "Invalid gzip size" );
							}
							memberCompleted = true;
							state = 0;
							offset = 0;
							inflater.reset();
							crc.reset();
							headerCrc.reset();
						}
						break;
					default:
						throw new IllegalStateException();
				}
			}
		}
		catch (DataFormatException e) {
			throw new JsonParseException( "Invalid gzip content", e );
		}
	}

	private void nextHeaderState() {
		offset = 0;
		trailer = 0;
		if ( ( flags & 4 ) != 0 ) {
			flags &= ~4;
			extraLength = 0;
			state = 1;
		}
		else if ( ( flags & 8 ) != 0 ) {
			flags &= ~8;
			state = 3;
		}
		else if ( ( flags & 16 ) != 0 ) {
			flags &= ~16;
			state = 4;
		}
		else {
			state = ( flags & 2 ) != 0 ? 5 : 6;
		}
	}

	public void endOfInput() {
		if ( !memberCompleted || state != 0 || offset != 0 ) {
			throw new JsonParseException( "Truncated gzip response" );
		}
	}

	@Override
	public void close() {
		inflater.end();
		output = null;
	}
}
