package org.hibernate.search.util.json.impl;

import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.async.NonBlockingByteBufferJsonParser;
import tools.jackson.core.sym.ByteQuadsCanonicalizer;

/**
 * Workaround for Core 3.2.2 pausing within an escaped property-name surrogate pair before
 * consuming all available input, or without updating its current token. Keep this implementation
 * detail isolated; the escaped-name split-point regression determines when it can be removed.
 */
final class ResumableJsonFactory extends JsonFactory {
	ResumableJsonFactory(JsonFactory configured) {
		super( configured );
	}

	NonBlockingByteBufferJsonParser createResumableByteBufferParser(ObjectReadContext readContext) {
		IOContext context = _createNonBlockingContext( null );
		ByteQuadsCanonicalizer symbols = _byteSymbolCanonicalizer.makeChildOrPlaceholder( _factoryFeatures );
		return new ResumableParser( readContext, context, readContext.getStreamReadFeatures( _streamReadFeatures ),
				readContext.getFormatReadFeatures( _formatReadFeatures ), symbols );
	}

	private static final class ResumableParser extends NonBlockingByteBufferJsonParser {
		private ResumableParser(ObjectReadContext readContext, IOContext context, int features, int formatFeatures,
				ByteQuadsCanonicalizer symbols) {
			super( readContext, context, features, formatFeatures, symbols );
		}

		@Override
		public JsonToken nextToken() {
			while ( true ) {
				JsonToken token = super.nextToken();
				if ( token == JsonToken.NOT_AVAILABLE && _pendingSurrogateInName != 0 ) {
					if ( currentToken() != JsonToken.NOT_AVAILABLE ) {
						_updateTokenToNA();
					}
					// Core 3 can pause before consuming available low-surrogate escape bytes.
					if ( !needMoreInput() ) {
						continue;
					}
				}
				return token;
			}
		}
	}
}
