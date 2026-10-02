# Task 3 checkpoint: replayable JSON output

Task 3 adds the Search-owned output SPI, an iterative resumable UTF-8 writer, synchronous replay/signing adapters, an internal direct Jackson 3 comparison writer, and an independently tested Elasticsearch framing helper. No live backend request entity, transport, signing interceptor or codec is migrated. Tasks 4–6 remain open, and future Jackson 2 support remains documentation only.

## Output and ownership contracts

[`JsonBodies`](../util/json/src/main/java/org/hibernate/search/util/json/spi/JsonBodies.java) creates [`JsonBody`](../util/json/src/main/java/org/hibernate/search/util/json/spi/JsonBody.java) sources. Each `openCursor()` or `openStream()` starts independent replay state; a partial transmission, cancelled cursor, concurrent traversal or signing read cannot move another cursor. `writeTo(OutputStream)` drains the same production cursor implementation. Output and signing streams initially use 8 KiB buffers; output neither flushes nor closes the caller's stream. Signing stream close releases its cursor and buffer. There are no background threads, mandatory executors or reflective dispatch.

Sources retain the submitted tree by reference, without copying the tree or storing an encoded payload. Trees must be acyclic and remain unchanged from body creation through consumption and across retries/signing. Bodies are shareable; cursors and opened streams are single-consumer state machines. Closing or cancelling a cursor is idempotent, releases tree/scalar/iterator/encoded references and rejects subsequent writes. Finishing traversal also releases its retained state, including the deque backing storage.

[`JsonWriteCursor`](../util/json/src/main/java/org/hibernate/search/util/json/spi/JsonWriteCursor.java) fills only a caller buffer's current remaining region, advances its position by the returned byte count and preserves its limit. It retains no destination. Heap, direct and sliced destinations are supported. Zero remaining capacity does no traversal work; read-only destinations are rejected before consuming state. Completion is reported even when the last byte exactly fills the destination. After completion, writes return zero until the cursor is closed. Cancellation leaves `isComplete()` false unless output had already finished.

The sink owns any emitted bytes. If a sink accepts only some or none of a filled buffer, its adapter must retain the unread region and drain it before requesting replacement output. The SPI does not impose demand or wakeup policies. Tests model zero/partial acceptance; HTTP producers, publishers and their emitted-storage lifetimes remain Task 6.

## Traversal, escaping and retention

[`Utf8JsonWriteCursor`](../util/json/src/main/java/org/hibernate/search/util/json/impl/Utf8JsonWriteCursor.java) uses iterator frames and scalar offsets. It can suspend inside a name, string, numeric lexeme, JSON escape or multibyte UTF-8 sequence. Encoded retained capacity is exactly six bytes while active, independent of individual token length, number length, member count or bulk size. Frames and iterator storage grow with nesting depth, and references point into the submitted tree. Number lexemes were already validated when constructing `JsonPrimitive`; output never rescans an entire number at the start of a write call.

This bound excludes the already materialized source tree, caller-owned destination buffers, transport queues and parser token storage. The synchronous drain/signing adapters each add one 8 KiB buffer. A content-length probe uses at most an 8 KiB scratch buffer and its configured total byte budget; encoded probe bytes are discarded. No event-loop latency or throughput advantage is claimed.

Shared output policy preserves member insertion order and exact numeric lexemes, including negative zero, decimal trailing zeroes and exponent spelling. Strings and names share these escaping rules:

| Input | Output |
| --- | --- |
| Quote/backslash and named JSON controls | Standard short JSON escapes |
| Other controls below U+0020 | Lowercase `\uXXXX` |
| `<`, `>`, `&`, `=`, apostrophe | Lowercase `\uXXXX`, matching request Gson HTML escaping |
| U+2028/U+2029 | `\u2028`/`\u2029`, matching request Gson |
| Ordinary Unicode, including valid surrogate pairs | UTF-8 |
| Isolated high/low surrogate | Lowercase Unicode escape, preserving its code unit |

Well-formed Unicode, HTML-sensitive characters and exact numbers match the existing Gson request bytes in backend tests. Lone surrogates intentionally differ: legacy Gson through `OutputStreamWriter` emits `?`, while the new writer emits an escape. All synchronous, incremental and signing paths use the new policy consistently. The existing strict parser still rejects broken surrogate pairs in property names; this output checkpoint does not change that parser policy.

[`JacksonJsonBodyWriter`](../util/json/src/main/java/org/hibernate/search/util/json/impl/JacksonJsonBodyWriter.java) writes Search nodes directly to an `OutputStream` with Jackson Core 3.2.2. It uses iterative traversal, Core's UTF-8 generator, custom HTML/line-separator escaping and lowercase hex, with target closing/flushing disabled. It is separate from diagnostic `toString()` rendering and is not selected by the production body factory. It is available internally for Task 4 comparison; no benchmark campaign was started.

## Bounded length and Elasticsearch framing

`JsonBodies.of(root)` probes at most 8,192 output bytes once. A body finishing within that budget records its exact length, including the exact-budget boundary. A larger body records `-1` permanently, even after a complete signing or output traversal. The probe closes its cursor and drops its scratch storage; subsequent cursors start at the beginning. `withContentLengthProbe(source, maximumBytes)` supplies the same stable semantics for independently framed sources, and `openCursor(root)` permits traversal without any preliminary probe. Probe storage remains at most 8 KiB even for a configured larger total budget.

[`ElasticsearchJsonBody.ndjson(...)`](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/client/common/json/impl/ElasticsearchJsonBody.java) lives in the backend and appends exactly one newline after every part, including the final part. An empty list emits no bytes and has known length zero. It opens only the current part cursor and retains no encoded buffer. The submitted list and trees remain owned by the source and must not change during consumption. A bounded length probe includes framing bytes.

The backend adds a dependency on `hibernate-search-util-json` solely to compile this helper. Its package remains an implementation package, qualified-exported to the engine by existing module conventions. The utility module has no HTTP or Elasticsearch framing dependency. Existing Gson entities, schema/document builders, codecs, public model types, retry publishers and AWS signing continue on their current paths until Tasks 5–6.

## Verification and evidence

Verified on 2026-10-02 with Red Hat OpenJDK 25.0.4.1, Maven wrapper 3.9.16 and compilation `--release 17`. Build caching was disabled.

The final clean focused reactor passed 15 modules and all 504 executed goals. It ran 480 cases with zero failures/errors and 15 preexisting REST entity assumption skips: JSON 93, backend/REST4 163, REST5 112, OpenSearch 112. New output/retention/framing cases all pass without skips. AWS and upstream modules compile/package; there is no actual AWS signing or cluster integration test in this checkpoint. Checkstyle, forbidden APIs, compilation with `-Werror`, dependency convergence/publication enforcement, source packaging and Moditect all pass. A final JSON-only clean verification after ownership documentation changes also passed 93 cases and 39 goals.

- [`JsonOutputTest`](../util/json/src/test/java/org/hibernate/search/util/json/JsonOutputTest.java): capacities 0/1/2/5/8192, caller position/limit, heap/direct/slices, byte identity with Jackson across every UTF-16 code unit, exact numeric lexemes, stable bounded lengths, independently concurrent cursors, retry after a partial write, caller-owned retained storage, signing streams and synchronous sink ownership/failure.
- [`JsonOutputRetentionTest`](../util/json/src/test/java/org/hibernate/search/util/json/impl/JsonOutputRetentionTest.java): scalar/name/number and array growth from small to MiB-scale fixtures at capacities 1/2/5/8192; instrumented encoded capacity never exceeds six bytes; incremental SHA-256 agrees with direct Jackson output; 50,000 nested containers serialize and cancel without recursion; cancellation/completion/failing output release state; the large-number length probe writes only its 8 KiB budget and closes its cursor.
- [`ElasticsearchJsonBodyTest`](../backend/elasticsearch/src/test/java/org/hibernate/search/backend/elasticsearch/client/common/json/impl/ElasticsearchJsonBodyTest.java): deterministic/final newline, empty/single bodies, 10,000 parts, zero/partial sink acceptance, independently interleaved cursors, replay after cancellation, signing bytes, Gson request byte comparison and the explicit lone-surrogate difference. Gson is used only in backend tests; no Gson dependency is added to the JSON module.
- Model/SPI signature scanning now checks generic interfaces and superclasses as well as members, covering the new output SPI. Existing source enforcement continues rejecting Gson, Databind and reflective field/method mapping from production JSON sources.

An external named module compiles and runs through the published body/cursor/signing SPI. Attempts to import the resumable cursor or Jackson comparison implementation correctly fail. JSON's descriptor exports model/SPI publicly, keeps `.impl` qualified to the engine and requires `tools.jackson.core` non-transitively. Runtime dependency inspection shows Core 3.2.2 and existing common dependencies only in JSON; no Gson, Core 2 or Databind. Backend runtime resolution includes the new JSON dependency and its still-required legacy Gson/Core 2 dependencies.

```bash
./mvnw -pl util/json,backend/elasticsearch spotless:apply -Dno-build-cache
./mvnw -pl util/json spotless:apply clean verify -DskipITs -Dno-build-cache
./mvnw clean install \
  -pl util/json,backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws -am \
  -Dtest=JsonModelTest,JsonAccessorTest,JsonParserTest,JsonApiBoundaryTest,JsonParserCleanupTest,JsonOutputTest,JsonOutputRetentionTest,ElasticsearchJsonBodyTest,JsonAccessorCharacterizationTest,GsonCodecCharacterizationTest,GsonModelCharacterizationTest,ElasticsearchSearchRequestTransformerCharacterizationTest,ElasticsearchRawResultCharacterizationTest,ElasticsearchNativeJsonCharacterizationTest,ElasticsearchSchemaExportCharacterizationTest,GsonParsingTest,ClientRest4GsonHttpEntityTest,ClientRest5GsonHttpEntityTest,ClientOpenSearchGsonHttpEntityTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DskipITs -Dno-build-cache
./mvnw -pl util/json dependency:build-classpath -DincludeScope=runtime \
  -Dmdep.outputFile=/tmp/json-task3-runtime-classpath -Dno-build-cache
python3 design/elasticsearch-async-json-task3/verify.py /tmp/json-task3-runtime-classpath
./mvnw -pl util/json,backend/elasticsearch dependency:tree -Dscope=runtime -Dno-build-cache
```

Evidence: [final clean reactor log](elasticsearch-async-json-task3/reactor-verification.log), [final module clean verification](elasticsearch-async-json-task3/module-verification.log), [JPMS output SPI checks](elasticsearch-async-json-task3/jpms-verification.log), [runtime dependency trees](elasticsearch-async-json-task3/dependencies.log), [reproducible JPMS script](elasticsearch-async-json-task3/verify.py), and the linked assertion sources. Log trailing whitespace is normalized for repository checks. An initial test-only sentinel check used a buffer's reduced limit when checking an untouched suffix; the test was corrected before final verification. A subsequent non-clean package check hit Moditect's existing-output-file guard; rerunning with `clean` passed. No outstanding check failed.

No throughput/allocation/CPU/fairness gate, full reactor, distribution ZIP/aggregate Javadocs, actual HTTP producer/publisher lifecycle, network slow-consumer test, compression test, real signing interceptor, Java 17 runtime execution, native image or cluster integration was run. Performance comparisons are Task 4. Codec/model integration is Task 5. Live framing, request demand/wakeup/storage ownership, transport retries/subscriptions, cancellation and signing integration are Task 6. This checkpoint prepares and tests their reusable output components only.
