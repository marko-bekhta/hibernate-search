package org.hibernate.search.util.json.impl;

import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonPrimitive;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.SerializableString;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;

/** Diagnostic rendering only. Request streaming is implemented separately. */
public final class JsonDiagnosticRenderer {
	private static final CharacterEscapes SURROGATE_ESCAPES = new CharacterEscapes() {
		private final int[] ascii = standardAsciiEscapesForJSON();

		@Override
		public int[] getEscapeCodesForAscii() {
			return ascii;
		}

		@Override
		public SerializableString getEscapeSequence(int character) {
			return character >= 0xd800 && character <= 0xdfff
					? new SerializedString( "\\u" + Integer.toHexString( character ) )
					: null;
		}
	};

	private static final JsonFactory FACTORY = JsonFactory.builder()
			.streamWriteConstraints( StreamWriteConstraints.builder().maxNestingDepth( Integer.MAX_VALUE ).build() )
			.characterEscapes( SURROGATE_ESCAPES ).build();

	private JsonDiagnosticRenderer() {
	}

	public static String render(JsonElement root) {
		StringWriter writer = new StringWriter();
		try ( JsonGenerator generator = FACTORY.createGenerator( ObjectWriteContext.empty(), writer ) ) {
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
		catch (RuntimeException e) {
			throw new IllegalStateException( "Cannot render JSON", e );
		}
		return writer.toString();
	}
}
