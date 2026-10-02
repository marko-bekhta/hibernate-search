# Async JSON handling and a reusable Search JSON SPI

## 1. Outcome, scope, and decisions

Create a published `hibernate-search-util-json` module containing a Search-owned JSON model, reusable accessor SPI, streaming SPI, and Jackson Core implementation. Migrate Elasticsearch backend processing to that model while preserving reflection-free codecs.

The design decisions are complete. Performance and transport feasibility checks below are implementation gates. Execute one task at a time, report its changes and verification evidence, and stop at the checkpoint before starting the next task.

### Agreed boundaries

- Support the existing JDK, Elasticsearch REST 4, Elasticsearch REST 5, and OpenSearch transports.
- Prepare reusable contracts for Vert.x; do not implement a Vert.x transport.
- Keep the module independent of Elasticsearch so another backend, including Solr, can reuse it.
- Use managed Jackson Core 3.2.2 only in the JSON module; introduce neither Databind nor reflective object mapping. Retain independent Jackson 2 requirements of pinned clients.
- Preserve materialized JSON trees for random access. Eliminate redundant serialized-body buffering.
- Support synchronous and resumable output without mandatory worker scheduling.
- Retain deprecated Gson input bridges and a public deprecated converter; migrate JSON return types and native field values.
- Put accessors in SPI, with explicit opt-in coercion methods.
- Preserve existing observable backend behavior through characterization tests.

The Jira issues were fetched through their public REST endpoints, including comments. [HSEARCH-2835](https://hibernate.atlassian.net/browse/HSEARCH-2835) targets incremental response parsing into a tree. [HSEARCH-2817](https://hibernate.atlassian.net/browse/HSEARCH-2817) adds codec performance, custom adapters, and random-access considerations.

### Why this approach

| Option | Assessment |
|---|---|
| Keep Gson parsing | Its reader-based parsing does not provide the required resumable input contract |
| Jackson async parser into Gson trees | Viable smaller migration, but retains Gson coupling |
| Jackson Core into Search trees | Selected: async parsing, reusable representation, and no Databind requirement |
| Jackson Databind/tree model | Unnecessary dependency and replacement API coupling |
| Another async parser, such as Actson | Possible, but does not remove the need for our model/codecs and offers no established advantage for this repository |

Do not claim a performance advantage until measured.

## 2. Module, model, and SPI contracts

### Published module

Create `util/json`, artifact `org.hibernate.search:hibernate-search-util-json`.

- Follow public-module conventions for publication, BOM, distribution, JPMS, licensing, and documentation.
- Use `org.hibernate.search.util.json` for model APIs and `.spi` for accessors, streaming, and codecs.
- Keep concrete implementations in `.impl`.
- Depend on managed `tools.jackson.core:jackson-core:3.2.2`; manage the Jackson 3 BOM separately from the Jackson 2 BOM needed by other modules.
- Expose neither Jackson nor Gson types in this module's API/SPI.
- Keep HTTP handling, Elasticsearch framing, and Gson conversion outside the module.

### Search JSON model

Provide `JsonElement`, `JsonObject`, `JsonArray`, `JsonPrimitive`, and `JsonNull`, with familiar Gson-like navigation and mutation methods.

The nodes are the actual internal representation, not wrappers over another library's tree. Passing a tree to public consumers must not require conversion.

Preserve insertion order and distinguish missing properties from JSON null. Preserve parsed number lexemes rather than routing all numbers through `double`. Characterize existing numeric conversion, equality, and hashing behavior before porting it.

Document mutation ownership: builders and transformers may modify trees before submission; submitted request bodies must remain unchanged until all retries complete.

### Accessor SPI

Port reusable accessor chains into the JSON module:

```java
private static final JsonAccessor<Long> TOTAL_HITS_VALUE_PROPERTY_ACCESSOR =
        JsonAccessor.root()
                .property("hits")
                .property("total")
                .property("value")
                .asLong();
```

Accessors navigate and modify trees in backend code. They are **not used by the parser**.

```text
Network chunks -> async parser -> tree builder -> completed response tree
                                                       |
                                            backend accessor extraction
```

Request construction can use accessors on trees under construction. Response extraction starts after parsing completes.

Preserve `get`, `set`, `getOrCreate`, array indexing, path diagnostics, and reusable immutable accessor definitions.

Make array adaptation explicit:

```java
JsonAccessor<Long> tolerant =
        JsonAccessor.root()
                .property("value")
                .unwrapSingletonArrays()
                .asLong();
```

Contract for `unwrapSingletonArrays()`:

- Applies at the selected path location.
- Repeatedly unwraps singleton arrays.
- Treats an empty array as absent for reads.
- Rejects arrays with multiple elements.
- Leaves non-array values available for subsequent type checking.
- Does not flatten a value accessed as an array unless explicitly selected.

Ordinary typed access rejects unexpected arrays. Numeric narrowing/coercion remains separately defined; "strict" must not accidentally imply a new exact-integer conversion policy.

For writes through an adapted path, preserve the existing behavior: selecting an existing singleton container for traversal must not silently replace that container; replacing the value at the selected location still targets that location. Capture existing `set` and `getOrCreate` behavior in tests before implementing the adapter.

**Migration requirement:** inventory adaptation at intermediate path steps, not just terminal scalar access. Existing chains implicitly adapt arrays during object traversal. Add explicit adaptation wherever needed to preserve behavior.

### Streaming SPI

Provide the following capabilities:

| Capability | Contract |
|---|---|
| Async parser session | Incrementally consumes byte buffers; explicit end-of-input; yields one Search value |
| Replayable JSON body | Opens independent cursors and supports synchronous output |
| Write cursor | Fills caller-provided buffers and resumes after capacity is exhausted |
| Known-type codec | Explicit/generated conversion between a known Java type and Search nodes |

Factories are shareable; parser/cursor instances are single-consumer state machines. Cancellation and closing release retained buffers and partial state.

Input ownership must be documented: callers cannot reuse or release input until consumed. No unbounded internal queue is permitted.

Jackson's feeder must receive input only when requested; drain tokens until `NOT_AVAILABLE`, then signal end-of-input and validate completion. [Feeder contract](https://github.com/FasterXML/jackson-core/blob/jackson-core-3.2.2/src/main/java/tools/jackson/core/async/NonBlockingInputFeeder.java)

### Execution and memory limits

Bounded buffering and event-loop fairness are separate requirements:

- Cursor output must suspend within large names, strings, and numeric lexemes.
- Adapters choose scheduling; the JSON SPI must not impose an executor or hidden thread hop.
- Feed input in bounded slices and allow continuation between slices.
- Measure CPU time for completing large tokens and building trees. Jackson may perform substantial work when a token completes.
- Do not advertise strict event-loop latency guarantees based only on a non-blocking parser.
- A future Vert.x adapter may offload expensive processing or synchronous interceptors using its own execution policy.

## 3. API migration and compatibility

| Surface | Change |
|---|---|
| Predicate/sort `fromJson(JsonObject)` | Search overload plus deprecated Gson default-method bridge |
| Aggregation `fromJson(...)` | Search return type; deprecated Gson input bridge |
| Native mapping `mapping(JsonObject)` | Search overload plus deprecated Gson input bridge |
| String input overloads | Retain and parse into Search nodes |
| Raw projections, `responseBody()`, `explain(...)` | Return Search types |
| Transformer `body()` | Mutable Search object |
| Native field values | Search `JsonElement` |
| Schema export bodies | Search objects |
| Client factory/request/response SPI | Search model and library-neutral services |

Keep primary method names. Document source/binary breaks for changed return types, native fields, and SPIs. Do not build a second legacy API for old return values.

Add deprecated public `org.hibernate.search.backend.elasticsearch.gson.GsonJsonConversion` with `fromGson` and `toGson` overloads for elements, objects, and arrays.

- Walk trees directly; never serialize and reparse.
- Preserve numbers, nulls, and member order.
- Return independent trees.
- Document traversal/allocation costs.
- Use the converter only in compatibility paths.
- Mark all Gson bridges and the utility for removal together in a future breaking release.

Keep Gson as a backend dependency while those signatures remain. The JSON module must remain Gson-free.

Preserve the transformer's existing copy-on-first-body-access behavior.

## 4. Ordered implementation checklist

### Task 1 — Baseline and characterization

- [x] Inventory Gson imports, public signatures, generic types, annotations, generated adapters, logging, and client extensions.
- [x] Capture fixtures for model semantics, mapping/settings codecs, native values, raw results, schema export, and transformers.
- [x] Cover all registered handwritten/generated adapters, including routing, dynamic values, formats, and named dynamic templates.
- [x] Characterize accessors: intermediate and terminal coercion, nested singleton arrays, null/missing values, creation, mutation, and error paths.
- [x] Record baseline throughput, startup codec cost, allocation, and retained encoded bytes.
- [x] Include large bulks and single documents with large strings; paged buffering alone does not guarantee bounded retention.
- [x] Record JVM, dependency versions, fixture sizes, and benchmark settings.

**Exit:** reproducible baseline and behavioral fixtures exist.

Completed checkpoint: [Task 1 report](elasticsearch-async-json-task1.md), including verification evidence and measurement limitations.

### Task 2 — Model, accessor SPI, and parser

- [x] Add module publication and dependency wiring.
- [x] Implement the Search model and port accessor behavior with explicit coercion.
- [x] Implement a stack-based token-to-tree builder independent of accessors.
- [x] Add async parsing and synchronous string conveniences.
- [x] Validate malformed/truncated input, trailing content, number precision, and duplicate-property semantics.
- [x] Configure parser constraints centrally and document intentional differences from existing behavior.
- [x] Keep absent-body interpretation at the transport boundary.
- [x] Provide configurable original-input byte counting, with no default response cap; applying REST-specific limits remains Task 6.
- [x] Provide bounded incremental charset transcoding; selecting explicit HTTP charsets at transport boundaries remains Task 6.

**Exit:** fragmented and contiguous input produce equivalent trees, with no accumulated raw response.

Completed checkpoint: [Task 2 report](elasticsearch-async-json-task2.md), with the [Jackson 3 follow-up and future Jackson 2 option](elasticsearch-async-json-jackson3.md). Jackson 2 fallback is not implemented; pursue it only on demonstrated need. Transport application of limits and charsets remains unchecked below.

### Task 3 — Output implementation

- [x] Implement iterative UTF-8 serialization with traversal frames and scalar/escape offsets.
- [x] Suspend within strings, names, and numbers.
- [x] Handle zero-capacity destinations without spinning.
- [x] Use 8 KiB adapter buffers initially; test much smaller capacities.
- [x] Add synchronous output by draining the same cursor.
- [x] Add an internal Jackson synchronous generator implementation for comparison.
- [x] Use cursor-backed writing as the new output SPI default, including independent signing streams; live transport application remains Task 6.
- [x] Keep Elasticsearch body-part framing and trailing newlines in a separately tested backend helper; live entities remain on Gson.
- [x] Allow a bounded small-body probe; never serialize a large body fully merely to calculate content length.
- [x] Give replay and signing reads independent cursor state; transport retry/subscription lifecycle integration remains Task 6.

**Exit:** synchronous and incremental bytes agree; retained encoded output does not grow with body size.

Completed checkpoint: [Task 3 report](elasticsearch-async-json-task3.md), with bounded-retention assertions, byte comparisons, clean verification and explicit transport deferrals. Performance claims and throughput gating remain Task 4.

### Task 4 — Performance gate

- [ ] Keep reusable JMH benchmarks, fixture builders, bounded-worker comparison code, buffer-retention probes and slow-consumer harnesses committed under `integrationtest/performance/backend/elasticsearch`, extending the existing `JsonBaselineBenchmark` and `JsonBufferRetentionProbe` infrastructure. Do not leave reusable benchmark code only in temporary directories or checkpoint documents.
- [ ] Document benchmark selectors, fixture parameters, build/run commands, environment requirements and profiling commands in `integrationtest/performance/backend/README.md`. Keep server-independent JSON benchmarks runnable without Elasticsearch.
- [ ] Commit representative raw JMH JSON, measurement environment and comparison reports alongside the Task 4 checkpoint. Keep machine-specific build outputs/profiler recordings out of source control. Retain legacy comparisons in benchmark code while useful; do not retain obsolete production implementations solely to keep benchmarks compiling.
- [ ] Compare the existing writer, Jackson synchronous generation, synchronous cursor drain, incremental cursor, and a benchmark-only bounded worker bridge.
- [ ] Test small requests, mappings, large bulks, individual large scalars, Unicode, and nested documents.
- [ ] Measure throughput, allocation, queued bytes, slow-consumer latency, CPU, and worker occupancy.
- [ ] Benchmark accessor chains separately, including explicit coercion.
- [ ] Use at least three JMH forks and retain raw results.
- [ ] Investigate repeatable throughput regressions above 5% in representative uncongested workloads before broad migration.
- [ ] Treat that threshold as an investigation trigger, not statistical proof.
- [ ] Resolve regressions without restoring whole-body buffering or mandatory thread hops.

**Exit:** comparison is recorded and material regressions are resolved.

### Task 5 — Reflection-free codecs and backend migration

- [ ] Replace the Gson processor with a Search JSON codec processor, retaining compile-time model analysis.
- [ ] Generate direct constructor/accessor/collection/nested-codec calls.
- [ ] Register codecs explicitly; prohibit runtime field inspection and reflective fallback.
- [ ] Keep codecs tree-based initially. Decode known types after async tree construction completes.
- [ ] Preserve null omission, explicit nulls, field naming, extra properties, unknown fields, and custom representations.
- [ ] Migrate schema, document building, validation, predicates, sorts, aggregations, projections, and work execution.
- [ ] Migrate accessor chains with explicit adaptation at every required path step.
- [ ] Replace Gson services and client SPI types.
- [ ] Apply public API changes and deprecated input bridges.
- [ ] Remove obsolete Gson adapters/providers once production callers migrate.

**Exit:** ordinary backend processing uses Search nodes and explicit/generated codecs only.

### Task 6 — Transport integration

**JDK**

- [ ] Replace response `InputStream` mapping with a subscriber consuming `List<ByteBuffer>`.
- [ ] Apply demand control and propagate parsing errors/cancellation.
- [ ] Replace input-stream request publishing with a demand-aware cursor publisher.
- [ ] Handle reentrant/invalid demand, repeated subscriptions, and cancellation.
- [ ] Never reuse emitted storage while subscribers may retain it.

**REST 4, REST 5, and OpenSearch**

- [ ] Install custom per-attempt response consumers on every request.
- [ ] Merge consumer options with existing timeout configuration.
- [ ] Parse chunks directly and associate the completed tree or parse failure with the response.
- [ ] Convert from that carrier without serializing/reparsing.
- [ ] Preserve status, headers, warnings, host selection, and retry behavior.
- [ ] Adapt request producers to cursor partial writes and transport wakeups.
- [ ] Release per-attempt state and propagate cancellation through available client handles.

**Cross-cutting behavior**

- [ ] Apply original-input byte counting to preserve REST response-size limits without adding a JDK response cap.
- [ ] Preserve explicit HTTP charset selection using the bounded incremental transcoding from Task 2.
- [ ] Preserve expected errors, retryable responses, non-JSON proxy responses, and connection reuse.
- [ ] Delay reporting body parse failures where necessary to preserve status-based retries.
- [ ] Provide lazy entity replay where required by REST diagnostics.
- [ ] Document REST-client error constructors that materialize strings as specific client limitations.
- [ ] Decode compressed responses incrementally, respecting existing decompression layers.
- [ ] Characterize client compression wrappers that buffer requests; record unavoidable exceptions with source references and tests.
- [ ] Preserve signing with an independent cursor-backed stream; repeated traversal is acceptable, accumulated payload bytes are not.
- [ ] Keep disabled logging free of serialization work. Explicit full-body diagnostic formatting may allocate strings.

**Exit:** all four clients pass lifecycle and streaming tests.

### Task 7 — Documentation and enforcement

- [ ] Document migration for raw results, projections, transformers, native fields, and custom clients.
- [ ] Document accessor coercion and distinguish it from parser behavior.
- [ ] Document buffer ownership, lifecycle, replay, cancellation, and adapter scheduling.
- [ ] Document remaining synchronous signing/interceptor behavior for future Vert.x integration.
- [ ] Add checks rejecting Jackson types in public API/SPI and Gson references outside the compatibility allowlist.
- [ ] Verify no Databind dependency is introduced.
- [ ] Update JPMS, distribution, BOM, and documentation tests.
- [ ] Remove superseded writers and dead helpers.

## 5. Verification and acceptance

### Required tests

**Parsing:** all split points for short fixtures; randomized large-input fragmentation; UTF-8/escape/number boundaries; heap/direct/sliced/read-only buffers; no body, JSON null, truncation, trailing garbage, limits, cancellation, and cleanup.

**Accessors:** strict versus explicit adaptation; terminal and intermediate paths; nested singleton arrays; empty/multiple-element arrays; missing/null parents; creation and mutation; numeric conversion; path diagnostics; reuse across independent trees.

**Writing:** capacities 0, 1, 2, 5, and 8192; zero-accepting transports; large names/strings/numbers; Unicode and surrogate policy; framing; retry after partial transmission; independent cursors; exact signing bytes.

**Compatibility/codecs:** all generated and handwritten fixtures; routing regression; transformer isolation; independent Gson conversions; no conversion on normal paths; no reflective fallback.

**Transport:** slow upload/download; early rejection; parsing before response completion; demand exhaustion; compression; signing; malformed errors; retries; deadlines; cancellation; connection reuse.

Instrument retained output buffers to prove they stay bounded as individual string and bulk sizes increase. Memory assertions must distinguish tree storage and parser token storage from transport queues.

### Commands

Focused module verification after creating `util/json`:

```bash
./mvnw clean install \
  -pl util/json,backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws \
  -am -DskipITs
```

Backend integration tests against repository-pinned distributions:

```bash
./mvnw clean install \
  -pl integrationtest/backend/elasticsearch -am \
  -Dtest.elasticsearch.distribution=elastic

./mvnw clean install \
  -pl integrationtest/backend/elasticsearch -am \
  -Dtest.elasticsearch.distribution=opensearch
```

Inspect separate client reports; one client's success does not validate the others.

Full reactor:

```bash
./mvnw clean install
```

Performance:

```bash
./mvnw clean install \
  -pl integrationtest/performance/backend/elasticsearch -am \
  -DskipTests -DskipITs

java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  -f 3 -wi 5 -i 5 -prof gc
```

Add JSON/accessor/streaming microbenchmarks to this infrastructure and document selectors, fixture parameters, and controlled slow-consumer scenarios. Compare baseline and candidate under identical conditions.

**Final implementation report:** tests actually run, per-client results, throughput/allocation comparisons, buffer-bound evidence, accessor compatibility results, remaining client-imposed buffering, API migration notes, and the Gson allowlist.
