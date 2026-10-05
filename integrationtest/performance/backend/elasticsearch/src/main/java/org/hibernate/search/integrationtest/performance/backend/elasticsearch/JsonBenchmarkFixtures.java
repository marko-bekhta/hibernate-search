package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.gson.spi.GsonProviderHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Shared server-independent JSON workloads; setup is never timed. */
public class JsonBenchmarkFixtures {
	public String fixture;
	public Gson gson;
	public List<JsonObject> parts;
	public String json;
	public long encodedBytes;

	public void setup() {
		gson = GsonProviderHelper.create( GsonBuilder::new, false ).getGson();
		parts = new ArrayList<>();
		JsonObject body = new JsonObject();
		switch ( fixture ) {
			case "small":
				body = gson.fromJson( "{\"query\":{\"term\":{\"name\":\"test\"}},\"size\":10}", JsonObject.class );
				break;
			case "mapping":
				body = gson.fromJson( "{\"dynamic\":\"strict\",\"_routing\":{\"required\":true},"
						+ "\"properties\":{\"name\":{\"type\":\"keyword\"},\"time\":{\"type\":\"date\","
						+ "\"format\":\"strict_date_optional_time||epoch_millis\"}}}", JsonObject.class );
				break;
			case "unicode":
				body.addProperty( "text", "é漢😀\n\"\\".repeat( 256 ) );
				break;
			case "nested":
				JsonObject current = body;
				for ( int i = 0; i < 32; i++ ) {
					JsonObject child = new JsonObject();
					current.add( "child", child );
					current = child;
				}
				current.addProperty( "value", 9007199254740993L );
				break;
			case "scalar-1m":
				body.addProperty( "text", "x".repeat( 1024 * 1024 ) );
				break;
			case "scalar-8m":
				body.addProperty( "text", "x".repeat( 8 * 1024 * 1024 ) );
				break;
			case "numbers-10000":
				JsonArray numbers = new JsonArray();
				for ( int i = 0; i < 10_000; i++ ) {
					numbers.add( i );
				}
				body.add( "values", numbers );
				break;
			case "numbers-mixed-10000":
				String[] mixed = { "-0", "-42", "0.125", "1.00", "6.022e23", "-7E-9", "9223372036854775808", "1e2147483648" };
				StringBuilder numericDocument = new StringBuilder( "{\"values\":[" );
				for ( int i = 0; i < 10_000; i++ ) {
					if ( i != 0 ) {
						numericDocument.append( ',' );
					}
					numericDocument.append( mixed[i % mixed.length] );
				}
				body = gson.fromJson( numericDocument.append( "]}" ).toString(), JsonObject.class );
				break;
			case "bulk-10000":
				for ( int i = 0; i < 10_000; i++ ) {
					JsonObject metadata = new JsonObject();
					JsonObject index = new JsonObject();
					index.addProperty( "_index", "benchmark" );
					index.addProperty( "_id", i );
					metadata.add( "index", index );
					parts.add( metadata );
					JsonObject document = new JsonObject();
					document.addProperty( "text", "x".repeat( 256 ) );
					document.addProperty( "number", i );
					parts.add( document );
				}
				break;
			default:
				throw new IllegalArgumentException( fixture );
		}
		if ( parts.isEmpty() ) {
			parts.add( body );
		}
		json = gson.toJson( parts.get( 0 ) );
		encodedBytes = 0;
		for ( JsonObject part : parts ) {
			encodedBytes += gson.toJson( part ).getBytes( StandardCharsets.UTF_8 ).length + 1;
		}
	}
}
