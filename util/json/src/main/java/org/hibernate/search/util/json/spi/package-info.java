/**
 * Reusable consumer accessors and incremental parsing services, independent of HTTP and backend protocols.
 * Accessors operate on trees after parsing or during request construction; parser tree construction uses its own stack.
 * Accessor definitions and parser factories are shareable; parser sessions are single-consumer state machines.
 * Strict typed access requires explicit singleton-array adaptation at each terminal or intermediate selected location.
 * Parser sessions consume bounded slices synchronously without retaining caller storage between calls.
 * Adapters choose their scheduling and demand policy; a bounded slice does not establish an event-loop latency guarantee.
 * Cancellation uses close, which releases partial trees and parser buffers.
 * No-body interpretation, response byte caps, HTTP charset selection, compression and retries belong to transports.
 */
package org.hibernate.search.util.json.spi;
