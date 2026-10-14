package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Untimed fixture fingerprints and integrated production setup checks; no performance scores. */
public final class JsonHarnessVerification {
	private JsonHarnessVerification() {
	}

	public static void main(String[] args) throws Exception {
		Map<String, String> matched = Map.of(
				"small", "49866608d2c8047d316fa54432d62897a7fd0de5e55a678f48c6e5644decd354",
				"unicode", "b0605cb095540bfa6043dea41f8dec9c54baa88ba7537d93e3bc7847c121bb09",
				"scalar-1m", "b194f811dc06551e54f1b63a540a93799bc7c70efbbeb467fc65b8102a67be64",
				"numbers-10000", "17679a76c6b8e310e007d9a8693d6197cab5bfbce645cb8264d6a274e403ce24",
				"bulk-10000", "0b3710f9e90901013c227256e9af65283b02b996345efe6c33fea823f1e08946" );
		System.out.println( "fixture,input_bytes,input_sha256,ndjson_bytes,ndjson_sha256" );
		for ( String name : new String[] {
				"small",
				"mapping",
				"unicode",
				"nested",
				"scalar-1m",
				"scalar-8m",
				"numbers-10000",
				"bulk-10000",
				"numbers-mixed-supported-10000" } ) {
			var state = new JsonComparisonBenchmark.Bodies();
			state.fixture = name;
			state.lifecycle = "reused";
			try {
				state.setup();
				String hash = sha256( state.expectedBytes );
				if ( matched.containsKey( name ) && !matched.get( name ).equals( hash ) ) {
					throw new IllegalStateException( "Matched baseline fixture changed: " + name );
				}
				byte[] input = state.json.getBytes( StandardCharsets.UTF_8 );
				System.out.printf( java.util.Locale.ROOT, "%s,%d,%s,%d,%s%n", name, input.length, sha256( input ),
						state.expectedBytes.length, hash );
			}
			finally {
				if ( state.worker != null ) {
					state.close();
				}
			}
		}
		for ( String reads : new String[] { "none", "selected", "repeated" } ) {
			var state = new JsonNumericConsumptionBenchmark.Numbers();
			state.fixture = "numbers-10000";
			state.lifecycle = "reused";
			state.reads = reads;
			try {
				state.setup();
			}
			finally {
				if ( state.worker != null ) {
					state.close();
				}
			}
		}
		new JsonComparisonBenchmark.Accessors().verify();
		// Keep the original historical bytes as an explicit unsupported fixture.
		var unsupported = new JsonBenchmarkFixtures();
		unsupported.fixture = "numbers-mixed-10000";
		try {
			unsupported.setup();
			throw new IllegalStateException( "Unsupported historical numeric fixture was accepted" );
		}
		catch (org.hibernate.search.util.json.spi.JsonParseException expected) {
			System.err.println( "Historical numbers-mixed-10000 rejected by supported exponent policy" );
		}
		System.err.println( "Integrated fixture, complete output, numeric read and accessor setup checks passed" );
	}

	private static String sha256(byte[] input) throws Exception {
		return HexFormat.of().formatHex( MessageDigest.getInstance( "SHA-256" ).digest( input ) );
	}
}
