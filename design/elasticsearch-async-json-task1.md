# Task 1 checkpoint: existing Gson behavior and performance baseline

Task 1 is complete: reproducible measurements and passing characterization fixtures exist. No production behavior, dependency, public API, parser, transport or JSON model was changed. Task 2 has not started.

This checkpoint covers the current implementation at `8a6f54cf007d2a35ba4a1e1ab04b5be528b00b02`. Measurements were collected on 2026-10-02. The limitations below apply to the evidence; this is not a claim that a replacement implementation is faster.

## Inventory

[gson-imports.tsv](elasticsearch-async-json-task1/gson-imports.tsv) lists every production source file with direct Gson imports: 278 files, including 274 in `backend/elasticsearch` and two each in the REST 5 and OpenSearch client modules. Generated sources are excluded from that count. The processor emits additional Gson references as source text.

Imported types include `Gson`, `GsonBuilder`, all five tree node types, `JsonSyntaxException`, `LongSerializationPolicy`, `TypeAdapter`, `TypeAdapterFactory`, `SerializedName`, `TypeToken`, `JsonReader`, `JsonToken` and `JsonWriter`. No direct Gson import was found in the AWS module; its signing code consumes the common interceptor's entity/body abstractions.

| Surface | Existing Gson dependency |
| --- | --- |
| Predicate/sort DSL factories | `fromJson(JsonObject)` and string overloads |
| Aggregation DSL factory | `AggregationFinalStep<JsonObject>` from object/string input |
| Projection DSL factory | `ProjectionFinalStep<JsonObject>` for source, explanation and JSON hit |
| Search query/result APIs | `explain(...)`, `responseBody()` and transformer `body()` return `JsonObject`; scroll results inherit result behavior |
| Native field DSL | `mapping(JsonObject)` and `IndexFieldTypeOptionsStep<S, JsonElement>` |
| Schema export | `List<JsonObject> bodyParts()`; file serialization through Gson |
| Client SPI | `ElasticsearchRequest.Builder.body(JsonObject)`, `List<JsonObject>` request parts, response `JsonObject`, factory `GsonProvider` parameter |
| Gson service SPI | `GsonProvider` owns serialize-nulls/no-serialize-nulls instances and `JsonLogHelper`; `GsonProviderHelper` registers codecs, creates user-facing Gson and renders pretty JSON |
| Internal field codecs and document builders | `JsonElement` encode/decode/converter generics, trees for documents, predicates, sorts, aggregations, work results and validation |
| Client extensions | REST factories receive `GsonProvider`; common interceptor context exposes body streams for signing; four entity implementations share the content provider |

`Elasticsearch7SearchSyntax` additionally uses `LongSerializationPolicy.STRING` to serialize selected search values as JSON strings. `GsonUtils.deepCopy` copies known mapping/settings models through `toJsonTree` plus `fromJson`, while transformer trees use `JsonObject.deepCopy`. `GsonUtils.setOrAppendToArray` distinguishes missing properties from explicit JSON null and retains prior values when creating arrays.

### Codecs and generation

`GsonProviderHelper.registerAdapters` registers 14 generated factories:

- `IndexAliasDefinition`.
- `AnalyzerDefinition`, `CharFilterDefinition`, `NormalizerDefinition`, `TokenFilterDefinition`, `TokenizerDefinition`.
- `Analysis`, `IndexSettings`.
- `DynamicTemplate`, `ElasticsearchDenseVectorIndexOptions`, `OpenSearchVectorTypeMethod`, `OpenSearchVectorTypeMethod.Parameters`, `PropertyMapping`, `RootTypeMapping`.

It also registers handwritten `NamedDynamicTemplateJsonAdapterFactory`, `DynamicTypeJsonAdapter` and `RoutingTypeJsonAdapter`. `PropertyMapping.format` uses the handwritten `FormatJsonAdapter` through `@GsonTypeAdapter`.

`build/gson-processor` reads `@GsonSerializable`, `@SerializeExtraProperties`, `@GsonTypeAdapter` and Gson `@SerializedName`. Its compile-time model walks inherited fields, finds public getters/setters, sorts known fields by Java name, generates exact-type factories, constructors, accessor calls and nested/generic adapter lookups. Unknown members go into insertion-ordered extra-property maps when present; otherwise they are skipped. Custom field adapters are instantiated directly. This is generated bean mapping, without runtime field inspection. Gson still supplies its built-in tree/collection/scalar adapters.

The new codec tests run with a `ReflectionAccessFilter` that blocks reflective field mapping for every class, including nested codecs. They also assert that each registered root adapter is not reflective. Fixtures exercise every registration, routing, all three dynamic values, named templates, date format splitting/joining, vector parameters, renamed fields, extras, explicit nulls and precision-sensitive numbers. A separate exact-number assertion avoids relying on Gson's lossy numeric equality.

Existing quirks captured by fixtures:

- Dynamic values serialize as strings (`"true"`, `"false"`, `"strict"`); a JSON boolean is rejected.
- Routing `{ "required": false }` reads as `OPTIONAL`, but serializing `OPTIONAL` throws. Empty routing objects read as null; unknown routing properties throw.
- Named templates require exactly one member.
- Formats are joined strings, not arrays. Trailing `||` segments disappear on read.
- Serialize-nulls policy affects known fields and extra JSON members, including nested tree members.
- Known generated members serialize in sorted Java-field order, followed by extras in their insertion order.

## Behavioral fixtures

New tests are in the backend's `src/test/java` tree:

| Test | Cases | Behavior captured |
| --- | ---: | --- |
| `JsonAccessorCharacterizationTest` | 8 | Intermediate/terminal adaptation, creation, writes, indexes, narrowing, reuse, diagnostics/root failures |
| `GsonCodecCharacterizationTest` | 23 | All 14 generated registrations, three registered handwritten codecs and custom format adapter; reflective fallback blocked |
| `GsonModelCharacterizationTest` | 3 | Member order, duplicates, null/missing, copy ownership, number lexemes/conversions/equality/hashes and escaping |
| `ElasticsearchSearchRequestTransformerCharacterizationTest` | 3 | Copy on first body access, repeated access, per-transformation isolation, path-only sharing and body-count validation |
| `ElasticsearchNativeJsonCharacterizationTest` | 1 | Native node identity, Java/JSON null conversion, independent string round trip and large integer preservation |
| `ElasticsearchRawResultCharacterizationTest` | 1 | Original response tree identity and large integer retention |
| `ElasticsearchSchemaExportCharacterizationTest` | 2 | Shared body trees, unmodifiable collections, query-parameter files and zero/one/multiple body framing |

The raw-result fixture observes tree mutability; the public `responseBody()` contract still tells consumers not to modify the returned tree.

Existing `GsonParsingTest` retains its routing regression. Each of the three existing REST HTTP entity matrices now includes a single 1 MiB string, adding eight cases per client. Their existing cases already cover small/many/large objects, bulk newlines, content length, UTF-8 regressions, stream/synchronous output, repeated production and backpressure every 5/100/500 bytes. The five skipped cases per client are existing assumptions about whether multi-part content length can be known initially.

Existing broader fixtures were inspected and reused as migration references: `ElasticsearchExtensionIT` covers native predicate/sort/aggregation inputs and raw projections/results; `ElasticsearchFieldTypesIT`/`ElasticsearchFieldAttributesIT` cover native mappings; `ElasticsearchCustomIndexMappingIT`/`ElasticsearchCustomIndexSettingsIT` cover custom schema JSON; `ElasticsearchSearchQueryIT` and `ElasticsearchSearchQueryRequestTransformerIT` cover query/result/transformer behavior; `ElasticsearchIndexSchemaManagerExporterIT` covers schema exports; `ElasticsearchGsonConcurrencyIT` covers adapter initialization under concurrency. Those integration tests were not executed at this checkpoint.

### Accessor compatibility details

There are 53 production files calling `JsonAccessor.root()`. `property(...)` implicitly inserts an object typing step; `element(...)` inserts an array typing step. Adaptation therefore occurs at intermediate traversal locations, including apparently ordinary chained property calls, not just terminal scalar reads. The parser does not use these accessors.

| Operation | Existing behavior |
| --- | --- |
| Typed `get` | Repeatedly unwraps unexpected singleton arrays; empty arrays and singleton nulls become absent; multiple elements fail |
| `asArray().get` | Returns the actual selected array without flattening, including singleton arrays of arrays |
| Untyped `get` | Missing is empty `Optional`; explicit null is present `JsonNull` |
| Intermediate object traversal | Adapts nested singleton arrays to objects, preserving those containers during writes |
| Typed `set` at selected location | Replaces that location, including its existing terminal singleton wrappers |
| `getOrCreate` | Creates only when the raw selected node is missing/JSON null; adapted absence is not considered missing |
| Existing terminal `[]`/`[[null]]` | `getOrCreate` returns null without calling the supplier or replacing the array |
| Existing intermediate `[]`/`[null]` | Reads are absent; `set`/`getOrCreate` fail with `NullPointerException` instead of replacing the container |
| Indexed writes | Extend arrays with JSON nulls; missing parent arrays/objects are created |
| Indexed reads | Out-of-range positive index is absent; negative index on an existing array throws `IndexOutOfBoundsException` |
| Numeric access | Numeric nodes narrow/truncate (`1.9` to long `1`; `4294967297` to integer `1`); string/boolean nodes fail ordinary typed numeric access |
| `JsonElementTypes.BIG_INTEGER` | Exception to scalar type checks: also accepts textual primitives |
| Diagnostics | Include intermediate `(JsonObject)` and terminal type annotations in runtime paths |

Definitions are immutable and reusable across independent trees. Root null fails with `AssertionFailure`; replacing the root is unsupported. Migration must account for both read adaptation and write/creation behavior at each intermediate location.

Tree characterization additionally records last-value-wins duplicate properties while preserving the property's original position, insertion-order iteration, order-insensitive object equality, independent mutable containers after deep copy and shared immutable primitive nodes. Parsed numbers retain lexemes and exact long conversion. Gson numeric equality can equate adjacent integers above `2^53`; equal numeric primitives with differing Java number representations can have differing hashes. Request Gson escapes HTML-sensitive characters; `JsonElement.toString()` leaves them literal. These are observations, not decisions to repair behavior during migration.

## Client buffering and diagnostics inventory

All four Search entity implementations delegate to `GsonHttpEntityContentProvider`. It uses a 1,024-character buffer and 1,024-byte pages, probes output during construction and suspends only between complete JSON body parts. `ProgressiveCharBufferWriter.needWritingPages` grows when the sink refuses data. A single scalar/document is therefore fully serialized before suspension. `writeTo` independently serializes parts to an `OutputStreamWriter`, including trailing newlines. Closing the provider replaces writer state and rewinds part traversal.

| Client | Existing response/request path |
| --- | --- |
| JDK | `BodySubscribers.mapping(ofInputStream(), GsonJsonMapper)` reads through a charset-aware `InputStreamReader`; publisher delegates to `BodyPublishers.ofInputStream(this::getContent)`; no Search response byte cap was found |
| REST 4 | Default heap-buffered response consumer, then charset-aware Gson parsing of the entity |
| REST 5 | Default `BufferedByteConsumer`/`BasicAsyncResponseConsumer`, then charset-aware Gson parsing of the entity |
| OpenSearch | Default heap-buffered entity/response consumer, then charset-aware Gson parsing of the entity |

The backend clients configure deadline/request timeouts on request options but do not install a streaming response consumer. The three REST defaults impose a 100 MiB response buffer limit; that is distinct from the JDK path. Backend request logging checks `isDebugEnabled()` before attaching its formatting callback, and successful responses avoid formatting unless trace is enabled. Explicit `JsonLogHelper` formatting produces complete strings and joins bulk parts with newlines (a backslash followed by `n` in compact diagnostic mode).

Primary dependency source references, inspected from locally resolved `*-sources.jar` files under `~/.m2/repository`:

- REST 4 `org.elasticsearch.client:elasticsearch-rest-client:9.5.4`: `HttpAsyncResponseConsumerFactory.java:52` has the 100 MiB default; `RestClient.java:849` creates a consumer per attempt; `ResponseException.java:62` calls `EntityUtils.toString`; `RestClient.java:958` implements compressed `getContent()` by writing the entire request into a byte-array stream. Response gzip wrapping is at `RestClient.java:337`.
- REST 5 `co.elastic.clients:elasticsearch-rest5-client:9.5.4`: `Constants.java:24` defines 100 MiB and an 8 KiB initial buffer; `HttpAsyncResponseConsumerFactory.java:63` constructs `BufferedByteConsumer`; `BufferedByteConsumer.java` accumulates bytes before completing a byte-array entity; `ResponseException.java:64` materializes an entity string.
- OpenSearch `org.opensearch.client:opensearch-rest-client:3.8.0`: `HttpAsyncResponseConsumerFactory.java:66` has the 100 MiB default; `RestClient.java:1233` creates a consumer per attempt; `ResponseException.java:82` materializes an entity string; `RestClient.java:1394` buffers the complete compressed request in `getContent()`, and optional content-length calculation invokes that path at line 1427. `nio/HeapBufferedAsyncEntityConsumer.java` accumulates response bytes.

Compression wrappers and error constructors remain client-level exceptions to investigate during Task 6. This checkpoint did not execute compressed/signing/network lifecycle scenarios or measure their retained memory.

AWS signing uses `HttpEntityContentStreamProvider`: requesting a new stream first closes the previous stream, then calls interceptor `content()`. JDK/REST 4 expose their entity streams; REST 5/OpenSearch additionally adapt repeatable async entity producers to the common 1 KiB input stream. Those adapters still inherit the provider's complete-body-part buffering. Closing streams is part of the existing replay/reset lifecycle and must be preserved when independent cursors are introduced later.

## Reproducible performance baseline

Added `JsonBaselineBenchmark` and `JsonBufferRetentionProbe` to the existing Elasticsearch performance module. They need no Elasticsearch server. All state construction is outside measured operations. The probe alone uses reflection to inspect existing private provider buffers; production codecs remain reflection-free.

Environment: Red Hat OpenJDK 25.0.4.1+1, x86_64 Linux, Intel Core i9-12900KS, 24 online CPUs. CPU cgroup has no quota; memory limit is approximately 75 GB. See [environment.txt](elasticsearch-async-json-task1/environment.txt). Gson is 2.14.0, managed Jackson Core is 2.22.1 (not used by these benchmarks), JMH is 1.37 and Maven wrapper is 3.9.16. The three REST dependency versions are recorded above.

Measured runs use one thread, three fresh JVM forks, three 1-second warmup iterations, three 1-second measurement iterations and `-Xms256m -Xmx256m`, with JMH's GC profiler. The run logs record JVM/blackhole details. Default annotations specify five warmup/measurement iterations; the recorded commands explicitly use three. Runs were sequential. This machine was not isolated or CPU-pinned; some confidence intervals are wide. Future comparisons must use identical commands/settings and assess variability rather than treating these point estimates as a performance gate.

| Fixture | Parts | Encoded request bytes, including newlines |
| --- | ---: | ---: |
| `small` | 1 | 45 |
| `mapping` | 1 | 165 |
| `unicode` | 1 | 3,852 |
| `nested` | 1 | 347 |
| `scalar-1m` | 1 | 1,048,588 |
| `scalar-8m` | 1 | 8,388,620 |
| `bulk-10000` | 20,000 | 3,257,780 |

Unicode repeats accented/CJK/supplementary characters and escapes; nesting has 32 object levels. Scalar fixtures contain ASCII strings of exactly 1/8 MiB. Bulk fixtures contain 10,000 metadata/document pairs, with 256-character string fields.

### Output throughput and allocation

Both output benchmarks include provider construction and its initial probe. `pagedBody` drains to a discard encoder accepting 8 KiB per invocation. `synchronousBody` calls the existing provider's `writeTo` with a null output stream, including its independent serialization after the constructor probe. The synchronous result therefore represents that existing provider lifecycle, not isolated Gson serialization.

| Fixture | Paged ops/s | Paged allocated B/op | Synchronous ops/s | Synchronous allocated B/op |
| --- | ---: | ---: | ---: | ---: |
| small | 2,692,333 | 3,872 | 1,091,506 | 5,752 |
| mapping | 1,875,438 | 3,784 | 612,334 | 6,504 |
| unicode | 104,688 | 7,000 | 31,778 | 90,648 |
| nested | 1,020,649 | 3,992 | 202,467 | 12,480 |
| scalar-1m | 653.28 | 1,142,811 | 471.91 | 3,250,120 |
| scalar-8m | 78.32 | 9,112,346 | 54.79 | 25,899,704 |
| bulk-10000 | 280.57 | 8,217,758 | 113.25 | 41,288,228 |

[Raw output results](elasticsearch-async-json-task1/body-throughput.json) contain per-fork iterations, errors and GC metrics; [run log](elasticsearch-async-json-task1/body-throughput.log) contains execution details.

### Parsing, accessors and codec initialization

Parsing consumes an already materialized string and creates the existing Gson tree; it excludes network buffering and UTF-8 decoding. The bulk is excluded because `parseFirstPart` would measure only its first metadata part.

| Fixture | Parse ops/s | Allocated B/op |
| --- | ---: | ---: |
| small | 3,206,073 | 3,640 |
| mapping | 2,138,975 | 4,528 |
| unicode | 181,799 | 15,880 |
| nested | 778,356 | 10,968 |
| scalar-1m | 1,296.68 | 5,211,298 |
| scalar-8m | 145.50 | 41,682,209 |

The ordinary `hits.total.value` accessor measured 55,048,147 ops/s and 48 B/op; nested singleton-array adaptation measured 21,415,636 ops/s and 48 B/op. Repeated provider construction, including eager mapping/settings codec initialization on two Gson instances, measured 60,334 ops/s (approximately 16.6 microseconds/op) and 28,877 B/op. The generated root mapping codec measured 1,367,051 ops/s and 4,600 B/op on the mapping fixture.

Three cold single-shot forks measured first provider construction at 28.760, 31.592 and 25.991 ms. This includes helper class initialization and codec initialization triggered by the first call, but excludes JVM launch and JMH setup. Three samples do not establish a reliable confidence interval; cold allocation was not measured. Warm allocation is reported separately above.

Raw results/logs: [parsing](elasticsearch-async-json-task1/parsing.json), [accessors/provider](elasticsearch-async-json-task1/accessors-provider.json), [cold provider](elasticsearch-async-json-task1/cold-provider.json), [mapping codec](elasticsearch-async-json-task1/mapping-codec.json); matching `.log` files are stored beside them.

### Retained encoded buffers

[buffer-retention.csv](elasticsearch-async-json-task1/buffer-retention.csv) distinguishes pending encoded bytes, byte-buffer capacity, character-buffer capacity and page count. It excludes tree storage, object/page overhead and transport queues. Snapshots cover construction, a zero-accepting sink, one 8 KiB acceptance budget and closing.

| Fixture | Pending bytes at construction/zero acceptance | Retained byte capacity | Pages |
| --- | ---: | ---: | ---: |
| small | 45 | 1,024 | 1 |
| unicode | 3,852 | 4,096 | 4 |
| scalar-1m | 1,048,588 | 1,049,600 | 1,025 |
| scalar-8m | 8,388,620 | 8,389,632 | 8,193 |
| bulk-10000 | 1,280 | 2,048 | 2 |

Every provider also retains 2,048 bytes of character-buffer capacity. Constructor probing can serialize several small bulk parts before backpressure is detected. After accepting 8 KiB, the 8 MiB string still retains 8,380,428 pending bytes. Closing releases all encoded pages from provider state, while a fresh character buffer remains. This proves that current page-based buffering does not bound retained encoded storage for a single large document/string. The bulk's small prefix must not be mistaken for evidence of a per-token bound.

### Commands

Build the actual backend before packaging the benchmark so stale installed snapshot artifacts cannot determine the baseline:

```bash
./mvnw -pl backend/elasticsearch clean install -DskipTests -DskipITs
./mvnw -pl integrationtest/performance/backend/elasticsearch clean package -DskipTests -DskipITs
```

The packaged provider, accessor, registration helper and generated property factory bytecode were checked to match the checkout's compiled classes. Existing installed reactor dependencies were reused; no full reactor build was needed for this checkpoint.

```bash
java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  'JsonBaselineBenchmark.(synchronousBody|pagedBody)' \
  -f 3 -wi 3 -i 3 -w 1s -r 1s -t 1 -jvmArgs '-Xms256m -Xmx256m' \
  -prof gc -rf json -rff design/elasticsearch-async-json-task1/body-throughput.json

java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  'JsonBaselineBenchmark.(accessor|adaptedAccessor|initializeProvider)' \
  -f 3 -wi 3 -i 3 -w 1s -r 1s -t 1 -jvmArgs '-Xms256m -Xmx256m' \
  -prof gc -rf json -rff design/elasticsearch-async-json-task1/accessors-provider.json

java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  'JsonBaselineBenchmark.initializeProvider' \
  -bm ss -tu ms -f 3 -wi 0 -i 1 -t 1 -jvmArgs '-Xms256m -Xmx256m' \
  -rf json -rff design/elasticsearch-async-json-task1/cold-provider.json

java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  'JsonBaselineBenchmark.parseFirstPart' \
  -p fixture=small,mapping,unicode,nested,scalar-1m,scalar-8m \
  -f 3 -wi 3 -i 3 -w 1s -r 1s -t 1 -jvmArgs '-Xms256m -Xmx256m' \
  -prof gc -rf json -rff design/elasticsearch-async-json-task1/parsing.json

java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  'JsonBaselineBenchmark.mappingCodec' -p fixture=mapping \
  -f 3 -wi 3 -i 3 -w 1s -r 1s -t 1 -jvmArgs '-Xms256m -Xmx256m' \
  -prof gc -rf json -rff design/elasticsearch-async-json-task1/mapping-codec.json

java -cp integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  org.hibernate.search.integrationtest.performance.backend.elasticsearch.JsonBufferRetentionProbe \
  > design/elasticsearch-async-json-task1/buffer-retention.csv
```

Use `-p fixture=mapping` for `mappingCodec`; running that method on arbitrary body fixtures is not a representative mapping measurement. Future performance comparison/slow-consumer work remains Task 4.

## Verification and remaining limits

The final targeted clean verification passed for the backend, REST 5 and OpenSearch client modules: 379 test cases, zero failures/errors, 15 existing assumption skips. The build cache was disabled for this final run: all 111 goals executed. It also passed formatting, Checkstyle, forbidden APIs and module packaging. Backend/REST 4 has 155 cases; each separate REST client has 112. The performance module's [clean package](elasticsearch-async-json-task1/benchmark-build.log) also passed its required static checks.

```bash
./mvnw -pl backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client \
  clean verify \
  -Dtest=JsonAccessorCharacterizationTest,GsonCodecCharacterizationTest,GsonModelCharacterizationTest,ElasticsearchSearchRequestTransformerCharacterizationTest,ElasticsearchRawResultCharacterizationTest,ElasticsearchNativeJsonCharacterizationTest,ElasticsearchSchemaExportCharacterizationTest,GsonParsingTest,ClientRest4GsonHttpEntityTest,ClientRest5GsonHttpEntityTest,ClientOpenSearchGsonHttpEntityTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DskipITs -Dno-build-cache
```

[verification.log](elasticsearch-async-json-task1/verification.log) retains the final build evidence. Earlier attempts identified/corrected fixture expectations and test compilation/style issues; non-clean packaging also hit Moditect's existing output collision, resolved by the final clean verification. No remaining build failure is being hidden.

No full reactor or Elasticsearch/OpenSearch service integration suite was run. The JDK HTTP lifecycle was inventoried from source; the three REST entity matrices validate entity behavior, not complete network clients. Actual signing/compression retention, end-to-end response buffering, slow-consumer latency, CPU/event-loop occupancy and heap object overhead are not measured here. These are subsequent transport/performance gates. The baseline contains no candidate implementation and cannot establish a regression or improvement.
