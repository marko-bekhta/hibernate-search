# Asynchronous request preparation: dispatcher integration handoff

This document hands Search-side APIs to the separate `dispatcher-vertx-quarkus`
integration session. Search implements the SPI, configured AWS provider and a local
Vert.x example. **The actual WIP dispatcher is not wired or tested here.** No
Quarkus checkout was cloned or modified. The reference used for read-only inspection
is the downloaded `/tmp/quarkus-vertx-client` source, corresponding to
[the WIP branch](https://github.com/marko-bekhta/quarkus/tree/feat/elasticsearch-vertx-client/independent-projects/elasticsearch-rest-client).

## Production API and ownership

All neutral types are in the published backend module, package
`org.hibernate.search.backend.elasticsearch.client.common.authentication.spi`:

```java
@FunctionalInterface
interface ElasticsearchRequestPreparation {
    CompletionStage<Result> prepare(Request request, Execution execution);
    record Request(URI uri, String method,
                   Map<String, List<String>> headers, JsonBody body) {
        Request(URI uri, JsonBody body); // POST, initially empty headers
        Request withHeaders(Map<String, List<String>> replacement);
    }
    record Result(Map<String, List<String>> headers) {}
    interface Execution {
        <T> CompletionStage<T> blocking(Callable<T> action);
        void checkActive();
    }
}
```

The source declarations, including constructors, are public. The URI must be
absolute HTTP(S), without user info/fragment. It contains effective authority and
encoded path/query, not a decoded/reconstructed query map. Snapshot method and
headers must be the values the transport will actually send. Request and Result
copy the header map and every value list; neither can hold a mutable Vert.x
request. Header spelling and multiple values are retained. Result is a **complete
header replacement**, not a delta. It cannot replace URI, method or body.

`body == null` means absent; a `JsonBody` emitting `null` is JSON null. `JsonBody`
is retained, not copied. Its source tree/bytes must remain stable through signing,
transmission, retries and independent cursors. No cached validation bit is added.
Existing JSON ownership/external-validation rules remain in force.

`Execution` is supplied per request/attempt by the integration. Search supplies
no executor, worker queue or admission policy. `blocking` schedules on an explicitly
selected existing integration facility and reports admission failures through a
failed stage (the AWS adapter also catches synchronous rejection).
`checkActive` must be thread-safe, touch no mutable transport state and throw when
cancelled, expired or superseded. A completed stage may run its callbacks on any
thread. Resume transport work explicitly on the owning Vert.x context.

Stage cancellation alone is not a cancellation protocol. Signal the integration's
state and reject late completion before applying headers/sending. Streams/cursors
are owned and closed by the thread that opened them. Do not close a worker's signing
cursor from an event loop or cancellation callback.

## Configuration and lifecycle

For a Search-backed client factory, use the existing bean resolver and backend-masked
property source **once during client creation**:

```java
BeanHolder<ElasticsearchRequestPreparation> preparation =
        ElasticsearchRequestPreparations.create(beanResolver, propertySource);
// Retain preparation and use preparation.get().prepare(snapshot, execution).
// At client shutdown: stop active attempts, then preparation.close().
```

The helper discovers `ElasticsearchRequestPreparationProvider`, invokes
`provide(ElasticsearchRequestPreparationProviderContext)`, retains provider beans
and returned `Optional<BeanHolder<ElasticsearchRequestPreparation>>` resources,
and composes enabled preparations in discovery order. Every preparation receives
the previous complete headers and the same body/destination. No provider means a
pass-through result. Header-mutating providers must precede the final signer; the
helper preserves resolver order and does not infer dependency order. For custom
combinations, use explicit `compose(List)` ordering; post-signing header mutation
is outside the contract. Only the configured AWS provider combination is verified here. Setup failure closes already-created holders; shutdown closes
all holders. Stop active work before closing credentials. Cancelling an HTTP result does not
establish that its blocking credential lookup has returned: track/drain running
preparation actions before releasing the holder, with shutdown policy owned by
the integration. Do not race provider close against an active signing worker. Shutdown/resource closing
may itself block; choose its execution policy in the integration.

`hibernate-search-backend-elasticsearch-aws` registers the new provider role through
its existing `ElasticsearchAwsBeanConfigurer`. It uses the same backend-masked
settings as synchronous REST signing:

- `aws.signing.enabled` (default false), `aws.region` (required when enabled).
- `aws.credentials.type`, including the existing default/static/custom factories.
- Static `aws.credentials.access_key_id` / `aws.credentials.secret_access_key`.
- Backend `version`: `amazon-opensearch-serverless` selects service `aoss`; other
  supported distributions select `es`. Existing obsolete-key checks are retained.

Created credential providers belong to the asynchronous provider holder; if they
implement AutoCloseable it closes them at shutdown. A custom credentials factory
returning shared resources must expose a suitable non-owning provider wrapper.
Direct construction is also available:

```java
new org.hibernate.search.backend.elasticsearch.aws.spi.ElasticsearchAwsRequestSigning(
        credentialsProvider, regionName, serviceName, clock);
```

That constructor **borrows** credentials and creates no executor. Its caller owns
credentials and lifecycle. The configured provider uses `Clock.systemUTC()`;
fixed clocks are available for deterministic tests.

Existing REST4/REST5/OpenSearch/JDK factories still discover
`ElasticsearchRequestInterceptorProvider` and execute synchronous interceptors at
their existing placement. They are not moved to workers and do not automatically
run both roles. Do not install both synchronous and asynchronous signing on one
attempt. Most `client.common.json.entity.spi` classes predate the JSON migration
as renamed Gson helpers; leave those packages alone.

## Hook ordering in the default dispatcher

The inspected WIP default path is effectively:

```java
httpClient.request(options).compose(req -> {
    applyHeaders(req, request);
    return req.send(request.body()); // currently a complete Buffer
});
```

Replace the final step with asynchronous preparation followed by streaming:

1. Select the destination/retry and preserve the original operation deadline.
2. Create the actual `HttpClientRequest`; install terminal/error/cancel handlers.
3. Apply all request options, user configurers, headers, path prefix and framing
   choices that can affect signed metadata. No bytes may be sent yet.
4. On the event loop, freeze effective authority, method, raw encoded URI and every
   header value, retaining the stable replayable body. For tested HTTP/1 semantics,
   an explicit Host header wins; otherwise use `req.authority()`. Set the effective
   Host explicitly. Construct snapshot URI from scheme + authority + `req.getURI()`.
   Do not reconstruct it from the node alone or decode/re-encode query parameters.
5. Invoke `preparation.prepare(snapshot, execution)` and return/compose its stage;
   never block the event loop waiting for it. A Vert.x adapter can use
   `context.executeBlocking(checkedCallable, false)` or a caller-owned
   `WorkerExecutor.executeBlocking`, and `Future.fromCompletionStage(stage, context)`
   to resume on the original context. Make admission/rejection and queue policy explicit.
6. Recheck terminal flag, original deadline and attempt generation. If current,
   replace the complete headers on that same request and stream the same snapshot
   body. Do not run a mutating user callback after signing. Do not change authority,
   method, path/query or wire body after signing.
7. Consume/discard the response with the dispatcher's ordinary status/error policy.
   A retry creates a new transport request and snapshot/signature/cursor.

The AWS adapter omits Content-Length, Transfer-Encoding, Connection, Expect and
User-Agent from the SDK signing input, while retaining them in its complete result
because the transport can finish framing. It removes stale Authorization,
X-Amz-Date, X-Amz-Security-Token and X-Amz-Content-Sha256 before generating fresh
values. Apply results as replacement so an expired session token cannot survive a
refresh to basic credentials. Other user headers are passed to the SDK, which applies
its own canonicalization rules. Destination Host/authority parity is the integration's
responsibility; do not silently sign a different host than the transport sends.

## Adapting the current Buffer body without rebuffering JSON

The WIP request carrier currently stores `Buffer`. A true streaming Search path must
carry its existing stable `JsonBody` alongside transport metadata instead of first
serializing it into that Buffer. Do not stringify/reparse the tree or collect an
entire encoded body just to satisfy `req.send(Buffer)`.

After authentication, open a **new** cursor for transmission. Fill a per-attempt
8192-byte ByteBuffer, copy only written bytes to a fresh Vert.x Buffer, write it,
and produce the next chunk only after write completion, yielding a context turn.
This ownership copy prevents the scratch array from aliasing an outstanding write.
At most one application write is outstanding in the example. Close the cursor on
completion, failure or cancellation; call request.end() when exhausted. Use known
`JsonBody.contentLength()` when available; unknown HTTP/1 lengths use chunked framing.
Do not invoke a blocking `JsonBody.writeTo` against the event loop transport.

If the WIP must temporarily retain an already-owned UTF-8 JSON/NDJSON Buffer path, represent exactly
those stable bytes with a replayable JsonBody cursor adapter (or an explicitly
separate existing-body branch), and preserve lifetime/immutability. A Buffer is
mutable, so document ownership transfer or take an explicit snapshot; do not claim
this avoids the existing complete Buffer allocation. Search does not add another
encoded-body abstraction or change JsonBody for this integration.

The AWS adapter invokes the synchronous SDK signer inside Execution.blocking.
Credential lookup and body hashing run there; its ContentStreamProvider opens
independent bounded JsonBody streams, closes previous streams on replay and closes
its final stream in try-with-resources on every exit. Checks run around credential
lookup and each signing read. Transmission never reuses a signing cursor. SDK2.55.4
signAsync's ordinary checksum path buffers payloads; no supported precomputed-hash
shortcut is assumed. The complete JSON tree remains materialized, and bounded output
storage is not a bound on CPU time for a pathological scalar.

## Retries, failures, cancellation and deadlines

Keep existing dispatcher policy for selecting nodes/status retries. Authentication
failure or executor rejection is an authentication/preparation failure: reset the
not-yet-sent request and propagate it, rather than marking the destination dead or
retrying every node with the same broken credentials. Do not send unsigned fallback
requests. Connection/status retries, when allowed, must authenticate again for the
new effective destination and use a new sending cursor under the original deadline.
Automatic redirects need an explicit re-signing policy; the example disables them.

Maintain an attempt generation, terminal flag and original monotonic deadline.
Cancellation from any thread sets a thread-safe signal; transport reset/cursor
cleanup occurs on its event loop. Deadline expiry maps to the dispatcher's timeout
failure, explicit cancellation to its cancellation API. Recheck the deadline when
applying completion/processing retries as well as scheduling a timer. Ignore
callbacks from old generations. Reset a request acquired before signing even if
credential lookup remains blocked. A blocking credentials provider cannot be
forcibly stopped by this SPI: the worker eventually observes cancellation and closes
its own stream; it may outlive the cancelled operation. Admission limits therefore
remain important integration policy.

## Verified here and dispatcher checklist

Search tests cover immutable snapshots/results, ordered asynchronous composition,
failure/cancellation propagation, provider discovery and resource release on setup
failure/shutdown. AWS tests cover es/aoss selection, static settings through the real
Search bean resolver, disabled/required-region behavior, empty payload, exact checksum,
replay, stale session removal, bounded owner-thread cursors, cancellation and rejection.

The local Vert.x example verifies HTTP/1.1 wire signatures against a fixed-clock SDK
reference, received-byte checksums, effective Host/authority overrides, encoded
path/query, method/multi-value headers, context responsiveness, worker selection,
independent replay, 503 destination retry, cancellation and deadlines during initial
and retry credential lookup. Synthetic credentials and local HTTP servers are used.
No live AWS endpoint or WIP dispatcher, HTTP/2, proxy, redirect, load/performance or
all production dispatcher failure combinations are validated by those checks.

Before calling the separate dispatcher integration complete:

- Compile against these actual Search APIs; enable configured AWS provider discovery
  and close its holder after active requests stop. Test signing disabled and setup errors.
- Test the actual hook after all user request configuration and before first write;
  compare signed metadata against actual received bytes/headers, including duplicate
  query parameters, escaped paths, default/explicit ports and authority overrides.
- Prove large JSON uses a JsonBody carrier and bounded writes, rather than the old
  complete Buffer path. Exercise retries/replay and pending-write ownership.
- Exercise worker admission/rejection, blocked credentials, deadline while queued,
  cancellation while hashing/writing/receiving, late completion and stale-generation
  callbacks. Confirm timeout/cancellation/error mapping and no unsigned sends.
- Exercise real dispatcher node health/status policy without treating credential
  failures as destination failures; confirm fresh signatures on destination retries.
- Keep existing synchronous REST/AWS regression checks. Add service/HTTP2/proxy
  verification only when those cases are intentionally supported.

Request gzip/compression and compressed-body/signing ordering are **deferred**.
The WIP compressionEnabled path currently advertises response gzip; that is not
request compression. Resolver dispatcher integration is **out of scope**. This work
makes no claim that either has been implemented. Final default-dispatcher wiring
belongs to the separate integration session.
