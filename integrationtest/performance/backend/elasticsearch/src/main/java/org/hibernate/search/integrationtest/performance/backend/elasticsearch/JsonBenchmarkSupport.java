package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.ElasticsearchJsonBody;
import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.client.common.gson.entity.spi.GsonHttpEntityContentProvider;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.impl.JacksonJsonBodyWriter;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

import org.openjdk.jmh.infra.Blackhole;

final class JsonBenchmarkSupport {
	private JsonBenchmarkSupport() {
	}

	interface Writer {
		void write(OutputStream output) throws Exception;
	}

	static List<JsonElement> convert(JsonBenchmarkFixtures fixture) {
		JsonParsing parser = new JsonParsing();
		List<JsonElement> parts = new ArrayList<>();
		for ( var part : fixture.parts ) {
			parts.add( parser.parse( fixture.gson.toJson( part ) ) );
		}
		return parts;
	}

	static void jackson(List<JsonElement> parts, OutputStream output) throws IOException {
		for ( JsonElement part : parts ) {
			JacksonJsonBodyWriter.write( part, output );
			output.write( '\n' );
		}
	}

	static void incremental(JsonBody body, OutputStream output, ByteBuffer buffer) throws IOException {
		try ( JsonWriteCursor cursor = body.openCursor() ) {
			while ( !cursor.isComplete() ) {
				buffer.clear();
				cursor.write( buffer );
				output.write( buffer.array(), 0, buffer.position() );
			}
		}
	}

	static byte[] bytes(Writer writer) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		writer.write( output );
		return output.toByteArray();
	}

	static void verify(JsonBenchmarkFixtures fixture, List<JsonElement> parts, JsonBoundedWorker worker) throws Exception {
		byte[] expected = bytes( output -> new GsonHttpEntityContentProvider( fixture.gson, fixture.parts ).writeTo( output ) );
		JsonBody body = ElasticsearchJsonBody.ndjson( parts );
		List<byte[]> results = List.of( bytes( output -> jackson( parts, output ) ), bytes( body::writeTo ),
				bytes( output -> incremental( body, output, ByteBuffer.allocate( 8192 ) ) ),
				bytes( output -> worker.write( destination -> jackson( parts, destination ), output ) ) );
		for ( byte[] result : results ) {
			if ( !Arrays.equals( expected, result ) ) {
				throw new IllegalStateException( "Full byte mismatch: " + fixture.fixture );
			}
		}
		if ( expected.length != fixture.encodedBytes ) {
			throw new IllegalStateException( "Incomplete consumption" );
		}
	}

	static void verifyAffinity() {
		String expected = System.getProperty( "json.benchmark.cores" );
		if ( expected == null ) {
			return;
		}
		try {
			String status = Files.readString( Path.of( "/proc/thread-self/status" ) );
			String actual = status.lines().filter( line -> line.startsWith( "Cpus_allowed_list:" ) )
					.findFirst().orElseThrow().split( ":" )[1].trim();
			if ( !expected.equals( actual ) ) {
				throw new IllegalStateException( "Affinity mismatch: expected " + expected + ", actual " + actual );
			}
			System.err.println( "Verified fork/thread affinity " + actual + " thread=" + Thread.currentThread().getName() );
		}
		catch (IOException e) {
			throw new UncheckedIOException( e );
		}
	}

	static class Sink extends OutputStream {
		long count;
		long sample;

		@Override
		public void write(int value) {
			count++;
			sample += value;
		}

		@Override
		public void write(byte[] bytes, int offset, int length) {
			count += length;
			if ( length > 0 ) {
				sample += bytes[offset] + bytes[offset + length - 1];
			}
		}

		long result() {
			return count ^ sample;
		}
	}

	/** Make array contents escape through JMH without a per-byte checksum. */
	static final class OpaqueSink extends Sink {
		private final Blackhole blackhole;

		OpaqueSink(Blackhole blackhole) {
			this.blackhole = blackhole;
		}

		@Override
		public void write(byte[] bytes, int offset, int length) {
			super.write( bytes, offset, length );
			blackhole.consume( bytes );
		}
	}
}
