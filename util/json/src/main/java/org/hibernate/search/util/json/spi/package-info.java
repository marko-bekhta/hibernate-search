/**
 * Jackson-native codecs and incremental parsing and output services, independent of HTTP and backend protocols.
 * Codec registries, parser factories and replayable output bodies are shareable; parser sessions and output cursors
 * are single-consumer state machines.
 * Parser sessions consume bounded slices synchronously without retaining caller storage between calls.
 * Adapters choose their scheduling and demand policy; a bounded slice does not establish an event-loop latency guarantee.
 * Output cursors fill caller-owned buffers with six encoded pending bytes. Primitive numeric fallback
 * uses twenty unencoded characters; large exact numbers can retain separately rendered numeric text.
 * Synchronous output and signing streams drain independent cursors using bounded buffers.
 * Submitted source trees must remain unchanged across consumption and replay.
 * Cancellation uses close, which releases partial trees, parser buffers and output traversal state.
 * No-body interpretation, response byte caps, HTTP charset selection, compression and retries belong to transports.
 */
@Incubating
package org.hibernate.search.util.json.spi;

import org.hibernate.search.util.common.annotation.Incubating;
