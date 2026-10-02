package org.hibernate.search.util.json.impl;

import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonPrimitive;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.SerializableString;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonWriteFeature;

/** Internal synchronous comparison implementation; production output drains the Search cursor. */
public final class JacksonJsonBodyWriter {
	private static final CharacterEscapes REQUEST_ESCAPES = new CharacterEscapes() {
		private final int[] ascii = createAscii();

		private int[] createAscii() {
			int[] codes = standardAsciiEscapesForJSON();
			for ( char character : new char[] { '<', '>', '&', '=', '\'' } ) {
				codes[character] = ESCAPE_STANDARD;
			}
			return codes;
		}

		@Override
		public int[] getEscapeCodesForAscii() {
			return ascii;
		}

		@Override
		public SerializableString getEscapeSequence(int character) {
			return character == 0x2028 ? new SerializedString( "\\u2028" )
					: character == 0x2029 ? new SerializedString( "\\u2029" )
					: null;
		}
	};
	private static final JsonFactory FACTORY = JsonFactory.builder()
			.streamWriteConstraints( StreamWriteConstraints.builder().maxNestingDepth( Integer.MAX_VALUE ).build() )
			.disable( StreamWriteFeature.AUTO_CLOSE_TARGET, StreamWriteFeature.FLUSH_PASSED_TO_STREAM )
			.disable( JsonWriteFeature.WRITE_HEX_UPPER_CASE )
			.characterEscapes( REQUEST_ESCAPES ).build();

	private JacksonJsonBodyWriter() {
	}

	/** Write the Search tree directly without reflection, diagnostic rendering, or a materialized body. */
	public static void write(JsonElement root, OutputStream destination) {
		try ( JsonGenerator generator = FACTORY.createGenerator( ObjectWriteContext.empty(), destination ) ) {
			ArrayDeque<Iterator<?>> frames = new ArrayDeque<>();
			JsonElement value = root;
			while ( true ) {
				if ( value.isJsonObject() ) {
					generator.writeStartObject();
					frames.push( value.getAsJsonObject().entrySet().iterator() );
				}
				else if ( value.isJsonArray() ) {
					generator.writeStartArray();
					frames.push( value.getAsJsonArray().iterator() );
				}
				else if ( value.isJsonNull() ) {
					generator.writeNull();
				}
				else {
					JsonPrimitive primitive = value.getAsJsonPrimitive();
					if ( primitive.isString() ) {
						generator.writeString( primitive.getAsString() );
					}
					else if ( primitive.isBoolean() ) {
						generator.writeBoolean( primitive.getAsBoolean() );
					}
					else {
						generator.writeNumber( primitive.getAsString() );
					}
				}
				while ( !frames.isEmpty() && !frames.peek().hasNext() ) {
					frames.pop();
					if ( generator.streamWriteContext().inArray() ) {
						generator.writeEndArray();
					}
					else {
						generator.writeEndObject();
					}
				}
				if ( frames.isEmpty() ) {
					break;
				}
				Object next = frames.peek().next();
				if ( next instanceof Map.Entry ) {
					Map.Entry<?, ?> member = (Map.Entry<?, ?>) next;
					generator.writeName( (String) member.getKey() );
					value = (JsonElement) member.getValue();
				}
				else {
					value = (JsonElement) next;
				}
			}
		}
	}
}
