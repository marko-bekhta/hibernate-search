package org.hibernate.search.backend.elasticsearch.client.common.json.spi;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.hibernate.search.backend.elasticsearch.json.spi.JsonCodecService;
import org.hibernate.search.backend.elasticsearch.json.spi.JsonProviderHelper;
import org.hibernate.search.util.json.JsonObject;

/**
 * Helpers allowing to log JSON.
 *
 */
public final class JsonLogHelper {


	public static JsonLogHelper get() {
		return JsonProviderHelper.create( true ).getLogHelper();
	}

	public static JsonLogHelper create(JsonCodecService codec, boolean prettyPrinting) {
		return new JsonLogHelper( codec, prettyPrinting );
	}

	private final JsonCodecService json;

	private final boolean prettyPrinting;

	private JsonLogHelper(JsonCodecService json, boolean prettyPrinting) {
		this.json = json;
		this.prettyPrinting = prettyPrinting;
	}

	public String toString(JsonObject object) {
		StringBuilder stringBuilder = new StringBuilder();
		append( stringBuilder, object );
		return stringBuilder.toString();
	}

	public void append(StringBuilder sb, JsonObject object) {
		beforeValue( sb );
		doAppend( sb, object );
		afterValue( sb );
	}

	public String toString(Iterable<JsonObject> objects) {
		StringBuilder sb = new StringBuilder( 180 );
		append( sb, objects );
		return sb.toString();
	}

	public void append(StringBuilder sb, Iterable<JsonObject> objects) {
		boolean first = true;
		beforeValue( sb );
		for ( JsonObject object : objects ) {
			if ( first ) {
				first = false;
			}
			else if ( prettyPrinting ) {
				sb.append( "\n" );
			}
			else {
				sb.append( "\\n" );
			}
			doAppend( sb, object );
		}
		afterValue( sb );
	}

	private void beforeValue(StringBuilder sb) {
		if ( prettyPrinting ) {
			sb.append( "\n" );
		}
	}

	private void doAppend(StringBuilder sb, JsonObject object) {
		try {
			sb.append( json.write( object ) );
		}
		catch (RuntimeException e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace( new PrintWriter( writer ) );
			sb.append( writer.toString() );
		}
	}

	private void afterValue(StringBuilder sb) {
		if ( prettyPrinting ) {
			sb.append( "\n" );
		}
	}
}
