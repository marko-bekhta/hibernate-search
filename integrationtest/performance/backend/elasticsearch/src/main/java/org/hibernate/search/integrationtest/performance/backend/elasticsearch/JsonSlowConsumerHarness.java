package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

import org.hibernate.search.backend.elasticsearch.client.common.json.impl.ElasticsearchJsonBody;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

/** Independent exact-byte/retention/cancellation assertions and untimed slow-consumer diagnostics. */
public final class JsonSlowConsumerHarness {
	private JsonSlowConsumerHarness() {
	}

	public static void main(String[] args) throws Exception {
		PrintWriter output = new PrintWriter( new FileOutputStream( FileDescriptor.out ), true, StandardCharsets.UTF_8 );
		JsonBenchmarkSupport.verifyAffinity();
		output.println(
				"fixture,encodedBytes,cursorEncodedBound,workerArrayBound,slowWallMs,totalCpuMs,workerCpuMs,workerBusyMs,workerCpuOccupancy" );
		ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		threads.setThreadCpuTimeEnabled( true );
		for ( String name : new String[] { "small", "mapping", "unicode", "nested", "scalar-1m", "scalar-8m", "bulk-10000" } ) {
			JsonBenchmarkFixtures fixture = new JsonBenchmarkFixtures();
			fixture.fixture = name;
			fixture.setup();
			var parts = java.util.List.copyOf( fixture.parts );
			var body = ElasticsearchJsonBody.ndjson( parts );
			byte[] expected = fixture.expectedBytes;
			try ( JsonBoundedWorker worker = new JsonBoundedWorker() ) {
				JsonBenchmarkSupport.verify( fixture, parts, worker );
				if ( worker.encodedCapacity() != 16384 ) {
					throw new IllegalStateException( "Worker storage bound changed" );
				}
				for ( int capacity : new int[] { 1, 2, 5, 8192 } ) {
					byte[] actual = JsonBenchmarkSupport
							.bytes( sink -> JsonBenchmarkSupport.incremental( body, sink, ByteBuffer.allocate( capacity ) ) );
					if ( !Arrays.equals( expected, actual ) ) {
						throw new IllegalStateException( "Capacity mismatch " + capacity );
					}
				}
				for ( var part : parts ) {
					try ( JsonWriteCursor cursor = JsonBodies.openCursor( part ) ) {
						if ( cursor.write( ByteBuffer.allocate( 0 ) ) != 0 ) {
							throw new IllegalStateException( "Zero capacity progress" );
						}
						cursor.write( ByteBuffer.allocate( 5 ) );
						Field field = cursor.getClass().getDeclaredField( "pending" );
						field.setAccessible( true );
						byte[] pending = (byte[]) field.get( cursor );
						if ( pending != null && pending.length > 6 ) {
							throw new IllegalStateException( "Unbounded cursor" );
						}
						cursor.close();
						if ( field.get( cursor ) != null ) {
							throw new IllegalStateException( "Cancelled cursor retains encoded output" );
						}
					}
				}
				ByteArrayOutputStream received = new ByteArrayOutputStream();
				IdentityHashMap<byte[], Boolean> deliveredBuffers = new IdentityHashMap<>();
				long cpuBefore = cpu( threads );
				long start = System.nanoTime();
				long[] workerTime = new long[2];
				worker.write( sink -> {
					long workerStart = System.nanoTime();
					long workerCpu = threads.getCurrentThreadCpuTime();
					JsonBenchmarkSupport.jackson( parts, sink );
					workerTime[0] = threads.getCurrentThreadCpuTime() - workerCpu;
					workerTime[1] = System.nanoTime() - workerStart;
				}, new OutputStream() {
					@Override
					public void write(int value) {
						received.write( value );
					}

					@Override
					public void write(byte[] bytes, int offset, int length) {
						deliveredBuffers.put( bytes, Boolean.TRUE );
						if ( bytes.length != 8192 || deliveredBuffers.size() > 2 ) {
							throw new IllegalStateException( "Worker delivered unbounded storage" );
						}
						LockSupport.parkNanos( 100_000 );
						received.write( bytes, offset, length );
					}
				} );
				long wall = System.nanoTime() - start;
				long cpu = cpu( threads ) - cpuBefore;
				if ( !Arrays.equals( expected, received.toByteArray() ) ) {
					throw new IllegalStateException( "Slow consumer incomplete" );
				}
				output.printf( Locale.ROOT, "%s,%d,6,16384,%.3f,%.3f,%.3f,%.3f,%.3f%n", name, expected.length,
						wall / 1e6, cpu / 1e6, workerTime[0] / 1e6, workerTime[1] / 1e6, (double) workerTime[0] / wall );
				worker.close();
				if ( worker.encodedCapacity() != 0 ) {
					throw new IllegalStateException( "Closed worker retains encoded storage" );
				}
			}
			try ( JsonBoundedWorker worker = new JsonBoundedWorker() ) {
				try {
					worker.write( sink -> JsonBenchmarkSupport.jackson( parts, sink ), new OutputStream() {
						@Override
						public void write(int value) throws IOException {
							throw new IOException( "intentional consumer cancellation" );
						}

						@Override
						public void write(byte[] bytes, int offset, int length) throws IOException {
							throw new IOException( "intentional consumer cancellation" );
						}
					} );
					throw new IllegalStateException( "Consumer failure ignored" );
				}
				catch (IOException expectedFailure) {
					// close cancels any blocked producer and asserts termination.
				}
				worker.close();
				if ( worker.encodedCapacity() != 0 ) {
					throw new IllegalStateException( "Cancelled worker retains encoded storage" );
				}
			}
		}
		// Lone surrogates must be escaped to preserve valid UTF-8 output.
		var lone = new org.hibernate.search.util.json.JsonObject();
		lone.addProperty( "text", "\ud800" );
		String loneOutput = new String( JsonBenchmarkSupport.bytes( JsonBodies.of( lone )::writeTo ), StandardCharsets.UTF_8 );
		if ( !"{\"text\":\"\\ud800\"}".equals( loneOutput ) ) {
			throw new IllegalStateException( "Surrogate policy mismatch" );
		}
		output.println( "All full-byte, capacity, cancellation and surrogate-policy assertions passed." );
	}

	private static long cpu(ThreadMXBean threads) {
		long total = 0;
		for ( long id : threads.getAllThreadIds() ) {
			long value = threads.getThreadCpuTime( id );
			if ( value > 0 ) {
				total += value;
			}
		}
		return total;
	}
}
