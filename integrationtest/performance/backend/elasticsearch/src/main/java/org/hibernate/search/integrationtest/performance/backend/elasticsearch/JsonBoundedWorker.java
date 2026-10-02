package org.hibernate.search.integrationtest.performance.backend.elasticsearch;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Benchmark-only bridge. Two owned 8 KiB arrays circulate; no unbounded payload queue. */
final class JsonBoundedWorker implements AutoCloseable {
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final ArrayBlockingQueue<byte[]> free = new ArrayBlockingQueue<>( 2 );
	private final ArrayBlockingQueue<Chunk> ready = new ArrayBlockingQueue<>( 2 );
	private Future<?> task;
	private byte[][] owned = { new byte[8192], new byte[8192] };

	JsonBoundedWorker() throws Exception {
		free.add( owned[0] );
		free.add( owned[1] );
		executor.submit( JsonBenchmarkSupport::verifyAffinity ).get();
	}

	void write(JsonBenchmarkSupport.Writer writer, OutputStream destination) throws Exception {
		task = executor.submit( () -> {
			try {
				try ( OutputStream output = new OutputStream() {
					private byte[] buffer;
					private int position;

					@Override
					public void write(int value) throws IOException {
						try {
							if ( buffer == null ) {
								buffer = free.take();
							}
							buffer[position++] = (byte) value;
							if ( position == buffer.length ) {
								flush();
							}
						}
						catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new IOException( e );
						}
					}

					@Override
					public void write(byte[] bytes, int offset, int length) throws IOException {
						try {
							while ( length > 0 ) {
								if ( buffer == null ) {
									buffer = free.take();
								}
								int count = Math.min( length, buffer.length - position );
								System.arraycopy( bytes, offset, buffer, position, count );
								position += count;
								offset += count;
								length -= count;
								if ( position == buffer.length ) {
									flush();
								}
							}
						}
						catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new IOException( e );
						}
					}

					@Override
					public void flush() throws IOException {
						if ( position > 0 ) {
							try {
								ready.put( new Chunk( buffer, position, null ) );
								buffer = null;
								position = 0;
							}
							catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								throw new IOException( e );
							}
						}
					}

					@Override
					public void close() throws IOException {
						flush();
					}
				} ) {
					writer.write( output );
				}
				ready.put( new Chunk( null, 0, null ) );
			}
			catch (Exception e) {
				if ( !Thread.currentThread().isInterrupted() ) {
					try {
						ready.put( new Chunk( null, 0, e ) );
					}
					catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
					}
				}
			}
		} );
		try {
			while ( true ) {
				Chunk chunk = ready.take();
				if ( chunk.error != null ) {
					throw chunk.error;
				}
				if ( chunk.bytes == null ) {
					break;
				}
				destination.write( chunk.bytes, 0, chunk.length );
				free.put( chunk.bytes );
			}
			task.get();
		}
		finally {
			if ( !task.isDone() ) {
				task.cancel( true );
			}
		}
	}

	int encodedCapacity() {
		return owned == null ? 0 : owned[0].length + owned[1].length;
	}

	@Override
	public void close() throws InterruptedException {
		if ( task != null ) {
			task.cancel( true );
		}
		executor.shutdownNow();
		if ( !executor.awaitTermination( 10, java.util.concurrent.TimeUnit.SECONDS ) ) {
			throw new IllegalStateException( "Worker did not terminate" );
		}
		ready.clear();
		free.clear();
		owned = null;
		task = null;
	}

	private record Chunk(byte[] bytes, int length, Exception error) {
	}
}
