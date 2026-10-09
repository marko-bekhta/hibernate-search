package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Locale;

import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.client.common.gson.entity.spi.ContentEncoder;
import org.hibernate.search.integrationtest.performance.backend.elasticsearch.legacy.client.common.gson.entity.spi.GsonHttpEntityContentProvider;

/**
 * Baseline-only inspection of encoded storage owned by the existing HTTP provider.
 * Reflective inspection is intentionally confined to this performance diagnostic.
 * It does not traverse the input tree, allocate serialized copies during snapshots,
 * or count buffers belonging to the transport.
 */
public final class JsonBufferRetentionProbe {

	private JsonBufferRetentionProbe() {
	}

	public static void main(String[] args) throws Exception {
		PrintWriter output = new PrintWriter( new FileOutputStream( FileDescriptor.out ), true, StandardCharsets.UTF_8 );
		output.println( "fixture,parts,encodedBytes,phase,pendingBytes,byteCapacity,charCapacityBytes,pages" );
		for ( String fixture : new String[] {
				"small",
				"mapping",
				"unicode",
				"nested",
				"scalar-1m",
				"scalar-8m",
				"bulk-10000" } ) {
			JsonBaselineBenchmark.Bodies bodies = new JsonBaselineBenchmark.Bodies();
			bodies.fixture = fixture;
			bodies.setup();
			GsonHttpEntityContentProvider provider = new GsonHttpEntityContentProvider( bodies.gson, bodies.parts );
			snapshot( output, bodies, provider, "constructed" );
			ProbeEncoder encoder = new ProbeEncoder();
			provider.produceContent( encoder );
			snapshot( output, bodies, provider, "zero-accepting" );
			encoder.budget = 8192;
			provider.produceContent( encoder );
			snapshot( output, bodies, provider, "after-8192-byte-budget" );
			provider.close();
			snapshot( output, bodies, provider, "closed" );
		}
	}

	private static void snapshot(PrintWriter output, JsonBaselineBenchmark.Bodies bodies,
			GsonHttpEntityContentProvider provider, String phase)
			throws Exception {
		Object writer = field( provider, "writer" );
		Deque<?> pages = (Deque<?>) field( writer, "needWritingPages" );
		long pending = 0;
		long capacity = 0;
		int count = 0;
		for ( Object value : pages ) {
			ByteBuffer page = (ByteBuffer) value;
			pending += page.remaining();
			capacity += page.capacity();
			count++;
		}
		ByteBuffer current = (ByteBuffer) field( writer, "currentPage" );
		if ( current != null ) {
			pending += current.position();
			capacity += current.capacity();
			count++;
		}
		CharBuffer chars = (CharBuffer) field( writer, "charBuffer" );
		output.printf( Locale.ROOT, "%s,%d,%d,%s,%d,%d,%d,%d%n", bodies.fixture, bodies.parts.size(), bodies.encodedBytes,
				phase, pending, capacity, chars.capacity() * 2, count );
	}

	private static Object field(Object instance, String name) throws Exception {
		Field field = instance.getClass().getDeclaredField( name );
		field.setAccessible( true );
		return field.get( instance );
	}

	private static class ProbeEncoder implements ContentEncoder {
		private int budget;
		private boolean completed;

		@Override
		public int write(ByteBuffer src) {
			int size = Math.min( budget, src.remaining() );
			src.position( src.position() + size );
			budget -= size;
			return size;
		}

		@Override
		public void complete() {
			completed = true;
		}

		@Override
		public boolean isCompleted() {
			return completed;
		}
	}
}
