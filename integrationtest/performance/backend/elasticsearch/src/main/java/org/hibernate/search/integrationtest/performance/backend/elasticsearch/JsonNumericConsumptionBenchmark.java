package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.util.concurrent.TimeUnit;

import org.hibernate.search.util.json.JsonElement;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Complete numeric-tree construction, with no reads, selected reads, or ten reads of every value. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonNumericConsumptionBenchmark {
	@State(Scope.Thread)
	public static class Numbers extends JsonComparisonBenchmark.Bodies {
		@Param({ "none", "selected", "repeated" })
		public String reads;

		// The inherited @Setup invokes this override; annotating it too schedules it twice.
		@Override
		public void setup() {
			super.setup();
			if ( !"numbers-10000".equals( fixture ) ) {
				throw new IllegalArgumentException( "Numeric consumption requires numbers-10000" );
			}
			long expected = switch ( reads ) {
				case "none" -> 0;
				case "selected" -> 495_000;
				case "repeated" -> 499_950_000;
				default -> throw new IllegalArgumentException( reads );
			};
			if ( readSearch( parsing.parse( json ), reads ) != expected
					|| readGson( gson.fromJson( json, com.google.gson.JsonObject.class ), reads ) != expected ) {
				throw new IllegalStateException( "Numeric consumption mismatch" );
			}
		}
	}

	@Benchmark
	public long searchParser(Numbers state, Blackhole blackhole) {
		var tree = state.parsing.parse( state.json );
		blackhole.consume( tree );
		return readSearch( tree, state.reads );
	}

	@Benchmark
	public long gsonParser(Numbers state, Blackhole blackhole) {
		var tree = state.gson.fromJson( state.json, com.google.gson.JsonObject.class );
		blackhole.consume( tree );
		return readGson( tree, state.reads );
	}

	private static long readSearch(JsonElement tree, String reads) {
		if ( "none".equals( reads ) ) {
			return 0;
		}
		var values = tree.getAsJsonObject().getAsJsonArray( "values" );
		int step = "selected".equals( reads ) ? 100 : 1;
		int repetitions = "selected".equals( reads ) ? 1 : 10;
		long sum = 0;
		for ( int pass = 0; pass < repetitions; pass++ ) {
			for ( int index = 0; index < values.size(); index += step ) {
				sum += values.get( index ).getAsLong();
			}
		}
		return sum;
	}

	private static long readGson(com.google.gson.JsonObject tree, String reads) {
		if ( "none".equals( reads ) ) {
			return 0;
		}
		var values = tree.getAsJsonArray( "values" );
		int step = "selected".equals( reads ) ? 100 : 1;
		int repetitions = "selected".equals( reads ) ? 1 : 10;
		long sum = 0;
		for ( int pass = 0; pass < repetitions; pass++ ) {
			for ( int index = 0; index < values.size(); index += step ) {
				sum += values.get( index ).getAsLong();
			}
		}
		return sum;
	}
}
