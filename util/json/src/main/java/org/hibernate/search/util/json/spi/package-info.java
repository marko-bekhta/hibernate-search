/**
 * Reusable consumer accessors and incremental parsing and output services, independent of HTTP and backend protocols.
 * Accessors operate on trees after parsing or during request construction; parser tree construction uses its own stack.
 * Accessor definitions, parser factories and replayable output bodies are shareable; parser sessions and output cursors
 * are single-consumer state machines.
 * Strict typed access requires explicit singleton-array adaptation at each terminal or intermediate selected location.
 * Parser sessions consume bounded slices synchronously without retaining caller storage between calls.
 * Adapters choose their scheduling and demand policy; a bounded slice does not establish an event-loop latency guarantee.
 * Output cursors fill caller-owned buffers and retain only traversal state and one encoded character/escape.
 * Synchronous output and signing streams drain independent cursors using bounded buffers.
 * Submitted source trees must remain unchanged across consumption and replay.
 * Cancellation uses close, which releases partial trees, parser buffers and output traversal state.
 * No-body interpretation, response byte caps, HTTP charset selection, compression and retries belong to transports.
 */
package org.hibernate.search.util.json.spi;
