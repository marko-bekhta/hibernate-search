package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.util.concurrent.TimeUnit;

import org.hibernate.search.util.json.spi.JsonBody;

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

/** Sensitivity comparison: identical complete writes with sampled or opaque array observation. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class JsonSinkSensitivityBenchmark {
	@State(Scope.Thread)
	public static class Bodies extends JsonComparisonBenchmark.Bodies {
		@Param({ "sampled", "opaque" })
		public String observation;
	}

	@Benchmark
	public long jackson(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = "opaque".equals( state.observation )
				? new JsonBenchmarkSupport.OpaqueSink( blackhole )
				: new JsonBenchmarkSupport.Sink();
		JsonBenchmarkSupport.jackson( state.searchParts, sink );
		return sink.result() ^ body.contentLength();
	}

	@Benchmark
	public long synchronousCursor(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = "opaque".equals( state.observation )
				? new JsonBenchmarkSupport.OpaqueSink( blackhole )
				: new JsonBenchmarkSupport.Sink();
		body.writeTo( sink );
		return sink.result() ^ body.contentLength();
	}

	@Benchmark
	public long incrementalCursor(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = "opaque".equals( state.observation )
				? new JsonBenchmarkSupport.OpaqueSink( blackhole )
				: new JsonBenchmarkSupport.Sink();
		JsonBenchmarkSupport.incremental( body, sink, state.buffer );
		return sink.result() ^ body.contentLength();
	}

	@Benchmark
	public long boundedWorker(Bodies state, Blackhole blackhole) throws Exception {
		JsonBody body = state.selectedBody();
		var sink = "opaque".equals( state.observation )
				? new JsonBenchmarkSupport.OpaqueSink( blackhole )
				: new JsonBenchmarkSupport.Sink();
		state.worker.write( output -> JsonBenchmarkSupport.jackson( state.searchParts, output ), sink );
		return sink.result() ^ body.contentLength();
	}

}
