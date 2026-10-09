package org.hibernate.search.backend.elasticsearch.json.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.JsonPrimitive;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecRegistry;
import org.hibernate.search.util.json.spi.JsonCodecs;
import org.hibernate.search.util.json.spi.JsonEmptyInputException;
import org.hibernate.search.util.json.spi.JsonParseException;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;

/** Library-neutral backend JSON services, using explicit known-type codecs and bounded byte input. */
public final class JsonCodecService {
	private final JsonCodecRegistry registry;
	private final boolean serializeNulls;
	private final boolean pretty;
	private final JsonParsing parsing = new JsonParsing();

	public JsonCodecService(JsonCodecRegistry registry, boolean serializeNulls, boolean pretty) {
		this.registry = registry;
		this.serializeNulls = serializeNulls;
		this.pretty = pretty;
	}

	public <T> JsonCodec<T> getCodec(Class<T> type) {
		return registry.get( type );
	}

	public <T> T decode(String value, Class<T> type) {
		return value == null ? null : decode( parsing.parse( value ), type );
	}

	public <T> T decode(InputStream input, Class<T> type) throws IOException {
		return decode( input, StandardCharsets.UTF_8, type );
	}

	public <T> T decode(InputStream input, Charset charset, Class<T> type) throws IOException {
		try ( JsonParserSession parser = parsing.createParser( charset ) ) {
			byte[] buffer = new byte[8192];
			int count;
			boolean present = false;
			while ( ( count = input.read( buffer ) ) != -1 ) {
				if ( count == 0 ) {
					continue;
				}
				present = true;
				ByteBuffer bytes = ByteBuffer.wrap( buffer, 0, count );
				while ( bytes.hasRemaining() ) {
					parser.feed( bytes );
				}
			}
			if ( !present ) {
				return null;
			}
			try {
				return decode( parser.endOfInput(), type );
			}
			catch (JsonEmptyInputException e) {
				return null;
			}
		}
	}

	public <T> T decode(JsonElement value, Class<T> type) {
		if ( value == null ) {
			return null;
		}
		if ( type == JsonElement.class ) {
			return type.cast( value );
		}
		if ( value.isJsonNull() ) {
			return null;
		}
		JsonCodec<T> codec = type == JsonObject.class || type == JsonArray.class ? null : registry.get( type );
		try {
			if ( type == JsonObject.class ) {
				return type.cast( value.getAsJsonObject() );
			}
			if ( type == JsonArray.class ) {
				return type.cast( value.getAsJsonArray() );
			}
			return codec.decode( value );
		}
		catch (JsonParseException e) {
			throw e;
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			throw new JsonParseException( "Cannot decode JSON as " + type.getName() + ": " + e.getMessage(), e );
		}
	}

	@SuppressWarnings("unchecked")
	public JsonElement encode(Object value) {
		if ( value == null ) {
			return JsonNull.INSTANCE;
		}
		if ( value instanceof JsonElement element ) {
			return serializeNulls ? element : JsonCodecs.copy( element, false );
		}
		if ( value instanceof String string ) {
			return new JsonPrimitive( string );
		}
		if ( value instanceof Character character ) {
			return new JsonPrimitive( character );
		}
		if ( value instanceof Boolean bool ) {
			return new JsonPrimitive( bool );
		}
		if ( value instanceof Number number ) {
			return new JsonPrimitive( number );
		}
		return ( (JsonCodec<Object>) registry.get( value.getClass() ) ).encode( value, serializeNulls );
	}

	/** Explicit diagnostic/raw-projection String output. */
	public String write(Object value) {
		StringWriter writer = new StringWriter();
		try {
			write( value, writer );
		}
		catch (IOException e) {
			throw new IllegalStateException( e );
		}
		return writer.toString();
	}

	/** Streaming formatted output used by schema exports. */
	public void write(Object value, Writer writer) throws IOException {
		JsonElement tree = encode( value );
		if ( pretty ) {
			render( tree, writer, 0 );
		}
		else {
			compact( tree, writer );
		}
	}

	/** Compact streaming UTF-8 output, preserving schema export's file format. */
	public void writeCompact(Object value, OutputStream output) throws IOException {
		org.hibernate.search.util.json.spi.JsonBodies.of( encode( value ) ).writeTo( output );
	}

	private void render(JsonElement value, Writer writer, int depth) throws IOException {
		if ( value.isJsonObject() ) {
			writer.write( '{' );
			int i = 0;
			for ( var e : value.getAsJsonObject().entrySet() ) {
				if ( i++ > 0 ) {
					writer.write( ',' );
				}
				indent( writer, depth + 1 );
				compact( new JsonPrimitive( e.getKey() ), writer );
				writer.write( pretty ? ": " : ":" );
				render( e.getValue(), writer, depth + 1 );
			}
			if ( i > 0 ) {
				indent( writer, depth );
			}
			writer.write( '}' );
		}
		else if ( value.isJsonArray() ) {
			writer.write( '[' );
			int i = 0;
			for ( var e : value.getAsJsonArray() ) {
				if ( i++ > 0 ) {
					writer.write( ',' );
				}
				indent( writer, depth + 1 );
				render( e, writer, depth + 1 );
			}
			if ( i > 0 ) {
				indent( writer, depth );
			}
			writer.write( ']' );
		}
		else {
			compact( value, writer );
		}
	}

	private void compact(JsonElement value, Writer writer) throws IOException {
		try ( var input = org.hibernate.search.util.json.spi.JsonBodies.of( value ).openStream();
				var reader = new InputStreamReader( input, StandardCharsets.UTF_8 ) ) {
			char[] buffer = new char[8192];
			int count;
			while ( ( count = reader.read( buffer ) ) != -1 ) {
				writer.write( buffer, 0, count );
			}
		}
	}

	private void indent(Writer writer, int depth) throws IOException {
		if ( pretty ) {
			writer.write( '\n' );
			for ( int i = 0; i < depth; i++ ) {
				writer.write( "  " );
			}
		}
	}
}
