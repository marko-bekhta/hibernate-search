package org.hibernate.search.util.json.impl;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.async.NonBlockingByteBufferJsonParser;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;

/**
 * Workaround for Core 2.22.1 returning NOT_AVAILABLE without updating the current token when a
 * field-name high-surrogate escape ends exactly at a buffer boundary. Keep this implementation
 * detail isolated; the escaped-name split-point regression determines when it can be removed.
 */
final class ResumableJsonFactory extends JsonFactory {
	ResumableJsonFactory(JsonFactory configured) {
		super( configured, null );
	}

	@Override
	public NonBlockingByteBufferJsonParser createNonBlockingByteBufferParser() {
		IOContext context = _createNonBlockingContext( null );
		ByteQuadsCanonicalizer symbols = _byteSymbolCanonicalizer.makeChildOrPlaceholder( _factoryFeatures );
		return new ResumableParser( context, _parserFeatures, symbols );
	}

	private static final class ResumableParser extends NonBlockingByteBufferJsonParser {
		private ResumableParser(IOContext context, int features, ByteQuadsCanonicalizer symbols) {
			super( context, features, symbols );
		}

		@Override
		public JsonToken nextToken() throws IOException {
			JsonToken token = super.nextToken();
			if ( token == JsonToken.NOT_AVAILABLE
					&& _pendingSurrogateInName != 0
					&& currentToken() != JsonToken.NOT_AVAILABLE ) {
				return _updateTokenToNA();
			}
			return token;
		}
	}
}
