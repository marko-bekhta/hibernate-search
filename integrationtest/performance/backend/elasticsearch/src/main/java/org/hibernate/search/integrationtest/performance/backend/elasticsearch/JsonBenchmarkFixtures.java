package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParsing;

/** Shared server-independent JSON workloads; setup is never timed. */
public class JsonBenchmarkFixtures {
	public String fixture;
	public List<JsonObject> parts;
	public String json;
	public byte[] expectedBytes;
	public long encodedBytes;

	public void setup() {
		List<String> documents = new ArrayList<>();
		switch ( fixture ) {
			case "small":
				documents.add( "{\"query\":{\"term\":{\"name\":\"test\"}},\"size\":10}" );
				break;
			case "mapping":
				documents.add( "{\"dynamic\":\"strict\",\"_routing\":{\"required\":true},"
						+ "\"properties\":{\"name\":{\"type\":\"keyword\"},\"time\":{\"type\":\"date\","
						+ "\"format\":\"strict_date_optional_time||epoch_millis\"}}}" );
				break;
			case "unicode":
				documents.add( "{\"text\":\"" + "é漢😀\\n\\\"\\\\".repeat( 256 ) + "\"}" );
				break;
			case "nested":
				documents.add( "{\"child\":".repeat( 32 ) + "{\"value\":9007199254740993}" + "}".repeat( 32 ) );
				break;
			case "scalar-1m":
				documents.add( "{\"text\":\"" + "x".repeat( 1024 * 1024 ) + "\"}" );
				break;
			case "scalar-8m":
				documents.add( "{\"text\":\"" + "x".repeat( 8 * 1024 * 1024 ) + "\"}" );
				break;
			case "numbers-10000":
			case "numbers-mixed-10000":
				String[] mixed = { "-0", "-42", "0.125", "1.00", "6.022e23", "-7E-9", "9223372036854775808", "1e2147483648" };
				StringBuilder numericDocument = new StringBuilder( "{\"values\":[" );
				for ( int i = 0; i < 10_000; i++ ) {
					if ( i != 0 ) {
						numericDocument.append( ',' );
					}
					numericDocument
							.append( "numbers-10000".equals( fixture ) ? Integer.toString( i ) : mixed[i % mixed.length] );
				}
				documents.add( numericDocument.append( "]}" ).toString() );
				break;
			case "bulk-10000":
				for ( int i = 0; i < 10_000; i++ ) {
					documents.add( "{\"index\":{\"_index\":\"benchmark\",\"_id\":" + i + "}}" );
					documents.add( "{\"text\":\"" + "x".repeat( 256 ) + "\",\"number\":" + i + "}" );
				}
				break;
			default:
				throw new IllegalArgumentException( fixture );
		}
		// Expected NDJSON is built from fixture text, independently of the measured writers.
		json = documents.get( 0 );
		expectedBytes = ( String.join( "\n", documents ) + "\n" ).getBytes( StandardCharsets.UTF_8 );
		encodedBytes = expectedBytes.length;
		JsonParsing parsing = new JsonParsing();
		parts = new ArrayList<>();
		for ( String document : documents ) {
			parts.add( parsing.parse( document ).getAsJsonObject() );
		}
	}
}
