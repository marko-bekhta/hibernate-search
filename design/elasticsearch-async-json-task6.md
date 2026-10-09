# Task 6: incremental transport integration

All four Search adapters now parse network chunks directly into asynchronous JSON
sessions. Completed Search trees go directly to the codec service. Normal request
output traverses replayable Search trees using bounded cursor buffers. No normal
Search transport route accumulates a complete raw response/request byte array or
String. Materialized trees and large individual String tokens still grow with the
JSON content, as required by the existing model.

## Execution and lifecycle

The JDK response subscriber consumes `List<ByteBuffer>`, processes each supplied
batch synchronously, and requests one more batch after consuming it. It completes
with a Search object tree; malformed input, EOF errors and cancellation close the
session and cancel the subscription. Caller buffer position is consumed correctly;
heap, direct, sliced and read-only inputs need not remain valid after the callback.
The request publisher creates one cursor per subscription, emits only with positive
demand, counts demand per emission, supports reentrant demand, rejects non-positive
demand and closes on cancellation/completion. Each emission owns fresh read-only
storage: a recipient can retain it without later emissions modifying it. There is
no publisher queue or eager full traversal before demand.

REST4, REST5 and OpenSearch install custom response-consumer factories on every
Search request, preserving timeout options through `toBuilder`. Consumers parse as
chunks arrive and attach a minimal repeatable entity carrier to the response.
REST4 gets an independent consumer for each low-level attempt. HC5 can retry inside
one low-level attempt using the same consumer: each new response head resets parser,
byte count, capacity and terminal state. A per-request scope independently releases
the currently registered consumer when the user cancels or a deadline/timeout fires.
Available client cancellation handles are invoked. Completion, failure, release and
late callbacks are idempotently guarded; cancelled requests cannot reopen parsing.

Request entities retain at most one 8192-byte pending buffer, preserve partial writes,
and request transport output when the sink accepts zero or only some bytes. HC5
availability remains positive until output completes. Reset/release closes the
attempt cursor and drops pending storage. Each `getContent` call owns a separate
cursor stream, including signing and retries. Permanent request cancellation closes
all open client-owned streams and prevents later replay. Large content length remains
`-1` after the bounded initial probe, signing and subsequent output.

Parsing and cursor production execute on the transport callback thread. No mandatory
executor or worker hop has been added. Bounded feeds bound encoded storage; finishing
one large scalar/tree token can still consume substantial callback time. Signing and
HTTP interceptors remain synchronous. These are explicit execution-policy limits,
not a guarantee of constant callback latency. Cancellation cleanup waits for an
in-flight synchronous callback to relinquish parser/cursor ownership.

## Errors, replay, limits and compression

REST parser failures are recorded while the consumer drains the response, rather
than aborting status-based failover before the client sees a retryable status. A
malformed/non-JSON 503 therefore still allows host/client retries. Final conversion
reports the original parser failure with status context. Empty/whitespace and absent
HTTP entities produce absent bodies; JSON null is a parsed null root. Trailing input
and truncated JSON remain errors. Successful and malformed-response draining permits
connection reuse; the JDK's immediate parser cancellation can close that connection.

All three pinned REST clients construct `ResponseException` using
`EntityUtils.toString` before finishing their status/retry decision. This unavoidable
client-owned diagnostic String is a specific exception to the no-raw-body-copy goal.
The entity is repeatable so the client does not introduce a `BufferedHttpEntity`
copy. Replay lazily serializes the completed tree as **normalized UTF-8 JSON**, not
original bytes. Number lexemes, member order and tree content remain available;
original whitespace/encoding are not reconstructed. Malformed input cannot replay a
tree: Search retains only the first 8192 decoded original-charset bytes, converts
that bounded prefix for diagnostics, and explicitly marks truncation. A split
multibyte character at the prefix boundary can appear as a replacement character.
This deliberately bounds malformed-response diagnostic retention and changes the
old complete malformed-body diagnostic. The parser failure remains available.

The REST default 100 MiB input limit is preserved at the consumer boundary, including
chunked responses and continued draining after a syntax failure. REST4 previously
checked declared entity length only; cumulative counting now also enforces its
configured limit for unknown-length/chunked input. Custom consumer
limits are tested. Counting happens before charset transcoding: UTF-16 input uses
its actual input-byte count. REST4 receives compressed bytes before the old client's
post-completion gzip wrapper, so its cap counts compressed bytes. Its new incremental
gzip decoder removes the same encoding/length headers that the client's prior
`convertResponse` removed and avoids double decoding. REST5/OpenSearch HC5 default
response decompression runs before the custom consumer, so their cap counts delivered
decompressed original-charset bytes, matching the prior consumer layer. Remaining
explicit gzip layers (including JDK) use a bounded incremental decoder with CRC,
header/trailer, truncation and concatenated-member checks. JDK retains no default
response cap. Explicit HTTP charset uses the existing bounded transcoding session.

Pinned REST request-compression wrappers still accumulate a whole compressed request
in `ContentCompressingEntity.getContent`. OpenSearch's optional forced compressed
content length invokes that method too. Unit fixtures verify the returned buffered
stream and source-failure behavior; this client-owned exception is not claimed to be
streaming. Normal uncompressed requests remain bounded. OpenSearch's
`HttpUriRequestProducer.create` wraps every entity in a client-owned
`HttpEntityAsyncEntityProducer`, bypassing Search's `AsyncEntityProducer` callbacks;
it reads the independent bounded cursor stream instead. Its normal request path is
bounded, but the client owns its final transport buffer and wakeups.

The HC5 low-level cancellation handle cancels the returned future, but a stalled
response socket can remain open when cancellation races with header delivery. This
was reproduced with the stock REST5 buffered consumer and with automatic retries
both enabled and disabled. The committed [stock-client probe](elasticsearch-async-json-task6/tools/Task6CancelProbe.java)
and [recorded output](elasticsearch-async-json-task6/cancellation-probe.txt) retain
that evidence. After running the REST5 transport fixture, reproduce it with
`python3 design/elasticsearch-async-json-task6/tools/run-cancellation-probe.py`; it
prints observations, rather than asserting a race outcome is universal. Search
frees parser/request cursor state independently.
The local lifecycle fixture verifies prompt application cancellation/timeout and a
subsequent successful request for both HC5 adapters; it does not claim prompt socket
EOF for that dependency race. REST4/JDK local cancellation additionally observes
socket EOF. No client retry policy was changed to hide this limitation.

Disabled Search request logging skips diagnostic formatting; enabled DEBUG error
logging/TRACE successful logging and explicit String projections remain intentional
full-body formatting boundaries. Signing reads an independent cursor stream and
subsequent request output starts at the beginning. The local SigV4 fixture uses dummy
credentials and WireMock; it verifies traversal/output and signing context, not an
AWS endpoint or independent cryptographic signature validation.

## Evidence and verification

[source-audit.tsv](elasticsearch-async-json-task6/source-audit.tsv) records the exact
pinned source entries/methods inspected from installed `*-sources.jar` artifacts.
[source-artifacts.tsv](elasticsearch-async-json-task6/source-artifacts.tsv) includes
SHA-256 provenance and primary Maven source-artifact links.
[retention-evidence.tsv](elasticsearch-async-json-task6/retention-evidence.tsv) maps
bounded-storage and lifecycle claims to committed fixtures. Network fixtures use
latches: real parser sessions detect a malformed prefix before the server allows
EOF. This proves parsing begins before the response completes, rather than merely
chunking an already buffered payload. Storage instrumentation separates fixed
encoded buffers from growing trees and tokens.

The focused unit campaigns pass **887 cases**: processor 5, JSON 105, backend
519, REST5 129 and OpenSearch 129, with zero failures/errors/skips. This adds 67
transport cases to Task 5's 820-case baseline. Fresh runs use `-Dno-build-cache`;
[unit-tests.tsv](elasticsearch-async-json-task6/unit-tests.tsv) records the counts.

```sh
./mvnw test -pl build/json-processor,util/json,backend/elasticsearch-aws -DskipITs
./mvnw test -pl util/json -Dno-build-cache -DskipITs
./mvnw clean install -pl backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client -DskipITs -Dno-build-cache
./mvnw test -pl backend/elasticsearch -DskipITs -Dno-build-cache
```

The selected migration matrix runs Elasticsearch 9.5.4 and OpenSearch 3.8.0
against all four client executions. It reports **704 cases: 696 executed passes and
eight expected AWS-profile skips**, with zero failures/errors. This includes 64
new transport-fixture cases. [live-tests.tsv](elasticsearch-async-json-task6/live-tests.tsv)
records all 88 nonempty Failsafe XML reports. These are selected live fixtures,
not a claim that the entire backend integration suite ran.

```sh
./mvnw clean verify -pl integrationtest/backend/elasticsearch -Dit.test=ElasticsearchExtensionIT,ElasticsearchFieldTypesIT,ElasticsearchFieldAttributesIT,ElasticsearchCustomIndexMappingIT,ElasticsearchCustomIndexSettingsIT,ElasticsearchSearchQueryIT,ElasticsearchSearchQueryRequestTransformerIT,ElasticsearchIndexSchemaManagerExporterIT,ElasticsearchJsonCodecConcurrencyIT,ElasticsearchContentLengthIT,ElasticsearchJsonTransportIT -Dtest.elasticsearch.distribution=elastic
# Repeat with -Dtest.elasticsearch.distribution=opensearch
```

The final local-network campaign explicitly covers both non-JSON and truncated-JSON
503 retry bodies, interrupted partial uploads, large individual fields, slow
upload/download, early rejection, connection reuse, original-byte limits,
charset/gzip, user cancellation and application timeout. Parser-before-EOF proof
uses the additional adapter unit fixtures against actual HTTP clients. There are
no timing sleeps; latches gate transfers, with finite safety timeouts.

The three REST factory classes run only in their matching client execution.
Together, factory (30), signing (4) and vector (20) seams contribute **54 additional
passing cases**, with zero skips. The final four-client local transport rerun passes
32 cases. [adapter-seams.tsv](elasticsearch-async-json-task6/adapter-seams.tsv) records
all 86 cases from these campaigns, separately from the migration matrix. Vector
fixtures cover the historical mapping helper on ES with REST4 (4 cases) and on OS
with all four clients (16 cases).

Ten methods per factory pass: `simple_http`, `simple_httpClientConfigurer`, `error`,
`unparseable`, `multipleHosts_failover_serverError`, `multipleHosts_failover_timeout`,
`multipleHosts_failover_fault`, `timeout_read`, `timeout_request` and `clientInstance`.
For example:

```sh
./mvnw -pl integrationtest/backend/elasticsearch dependency:properties failsafe:integration-test@it failsafe:verify@it '-Dit.test=ClientRest4ElasticsearchClientFactoryIT#simple_http+simple_httpClientConfigurer+error+unparseable+multipleHosts_failover_serverError+multipleHosts_failover_timeout+multipleHosts_failover_fault+timeout_read+timeout_request+clientInstance,ElasticsearchJsonTransportIT,ElasticsearchIndexSchemaManagerValidationMappingBaseIT#vector_success_1+vector_success_2' -Dtest.elasticsearch.distribution=elastic -Dfailsafe.client.elasticsearch.summaryFile=/tmp/task6-rest4-final-seams.xml
# REST5/OpenSearch: corresponding factory class/execution, plus ElasticsearchJsonTransportIT; omit the ES vector selector
```

The final transport/vector invocation uses all four execution/verification pairs
shown in the signing command below, with this selector and independent summary files:

```sh
-Dit.test=ElasticsearchJsonTransportIT,ElasticsearchIndexSchemaManagerValidationMappingBaseIT#vector_success_1+vector_success_2
-Dtest.elasticsearch.distribution=opensearch
```

Signing traversal/output runs with dummy credentials, fresh summary files and the
local WireMock fixture for every client:

```sh
HIBERNATE_SEARCH_AWS_STATIC_CREDENTIALS_ACCESS_KEY_ID=task6-test-access-key \
HIBERNATE_SEARCH_AWS_STATIC_CREDENTIALS_SECRET_ACCESS_KEY=task6-test-secret \
./mvnw -pl integrationtest/backend/elasticsearch dependency:properties \
 failsafe:integration-test@it failsafe:integration-test@it-opensearch-client \
 failsafe:integration-test@it-elasticsearch-rest5-client failsafe:integration-test@it-jdk-client \
 failsafe:verify@it failsafe:verify@it-opensearch-client \
 failsafe:verify@it-elasticsearch-rest5-client failsafe:verify@it-jdk-client \
 '-Dit.test=ElasticsearchContentLengthIT#payloadJustAboveBufferSize_requestPostProcessing' \
 -Dtest.elasticsearch.distribution=elastic \
 -Dtest.elasticsearch.connection.aws.signing.enabled=true \
 -Dtest.elasticsearch.connection.aws.region=us-east-1 \
 -Dtest.elasticsearch.connection.aws.credentials.type=static \
 -Dfailsafe.client.elasticsearch.summaryFile=/tmp/task6-rest4-signing.xml \
 -Dfailsafe.client.opensearch.summaryFile=/tmp/task6-os-signing.xml \
 -Dfailsafe.client.elasticsearch.rest5.summaryFile=/tmp/task6-rest5-signing.xml \
 -Dfailsafe.client.jdk.summaryFile=/tmp/task6-jdk-signing.xml
```

The clean full reactor install succeeds (3147 goals, 2972 executed, 175 cached),
including production/test/docs consumers, style, forbidden API, dependency and
Moditect checks and benchmark compilation. Final adapter lifecycle changes are
separately clean-built with fresh unit execution, and the final touched Java files
pass `checkstyle:check`. Distribution validation succeeds and the rebuilt shaded
benchmark jar lists all 45 JMH entries:

```sh
./mvnw clean install -DskipTests -DskipITs
./mvnw checkstyle:check -pl backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,integrationtest/backend/elasticsearch,util/internal/test/common -DskipTests -DskipITs
./mvnw -Pdist -pl distribution validate -DskipTests -DskipITs
java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar -l
```

Jackson 3 Core remains pinned at 3.2.2, with no Databind or reflective codec fallback.
Normal Search routes have no Gson references; the same five deprecated boundary
files from Task 5 remain. JDK 25.0.4.1 compiles production at release 17. No JDK 17
runtime, native-image, AWS endpoint, independent signature verification, new timing
campaign, full distribution ZIP or aggregated Javadoc execution is claimed.
Task 7's project-wide migration documentation and enforcement remain separate work.
