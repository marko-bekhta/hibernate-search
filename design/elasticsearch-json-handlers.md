# Jackson trees and typed JSON handlers: implementation plan

Status: execution authorized, 2026-10-13; implementation and scoped integration
review completed, 2026-10-14. The production and documentation cutovers compile,
focused correctness checks pass, and selected live Elasticsearch examples pass
through all four clients. Integrated performance tradeoffs are investigated and
reported rather than automatic release blockers. Delivery is an uncommitted,
reviewable working-tree change; final parent review and any broader release
validation remain separate from this implementation evidence.
This document records the revised direction discussed after the async JSON migration.
It supersedes the earlier plan's Search-owned public tree, exclusion of Databind,
and exact numeric-lexeme preservation requirements. Existing transport, streaming,
codec and compatibility requirements continue unless explicitly changed here.

## Agreed direction

- Use Jackson 3 Databind nodes internally, initially aligned with managed Core
  3.2.2. Using nodes does not require reflective Java model mapping. Retain generated
  and handwritten codecs with direct field/accessor calls.
- Public JSON integration uses a small typed handler. `T` means a JSON tree/value
  from the user's library, not an entity DTO. Generalize the PoC's object-only
  `JsonObjectHandler<T>` into the implemented `JsonHandler<T>` mechanism for native values. Object-only APIs still validate an object root; adapters
  can offer object-typed and general-value-typed instances. Do not make backend
  internals dynamically select a JSON provider.
- Recognize the trusted built-in Jackson 3 handler explicitly. Its import/export
  path uses the internal node without serializing or parsing. Custom handlers must
  still run, even when their Java value type is also `ObjectNode`.
- Use a neutral text fallback initially. Evaluate String versus Reader/Writer at
  the boundary checkpoint using real costs. Do not introduce a public replacement
  tree or token API merely to optimize that fallback.
- Preserve numeric values within documented supported ranges until projection
  conversion. Do not round exact integers or decimals through `double`. Original
  exponent spelling, whitespace and numeric lexical round trips are not guaranteed.
- Preserve ownership rules: predicate inputs stay stable while Search uses them;
  returned source/raw JSON is borrowed and read-only. The Jackson transformer gets
  one independent copy before its callback. Install its returned tree only after
  success; the caller must not mutate it afterward. Do not add a defensive second
  copy after the callback. Other handlers follow the same public ownership rules
  even where conversion happens to give additional isolation.
- Normal processing keeps bounded incremental parsing and resumable output,
  cancellation cleanup, replay/signing consistency, and current transport behavior.
  Ordinary DSL use must not pass through user-handler conversion.

## Existing evidence and baselines

The standalone PoC is in the sibling worktree
`/home/agentuser/hibernate-search-json-handler-poc/experiments/json-handler-poc`.
The initial plan snapshot recorded **24 passing test invocations** and an
optional second transformer copy. Those are historical observations, not the
current contract. The aligned PoC now records **65 passing invocations**, a
general-value handler, typed native fields, exact supported numeric values,
ordinary-node validation and **one pre-copy with zero post-copies**. Preserve the
historical snapshot when comparing the design's evolution.

The PoC remains standalone functional evidence: it neither integrates the backend
nor establishes performance. Its incremental adapter omits production UTF-8 and
input ownership/lifecycle protections; do not transplant it as a production parser
or writer. The later boundary slice in the candidate worktree records **74 passing
invocations** against kernel v3 and uses explicit DSL/transport/generated-code
stand-ins. It is also separate from the actual production integration below.

### Final implementation evidence

| Evidence | Result and scope |
| --- | --- |
| Approved handler/API contract | [Implementation contract](elasticsearch-json-handlers-contracts.md); built-in Jackson 3 and Gson factories, custom handler invocation, generic native metadata, object/null/ownership rules and deprecated Gson compatibility |
| Latest small boundary corrections | **26 focused backend tests pass**: carrier 5, handler bridge 19, accessor numeric policy 2; clean install/module/style checks pass. Covers eager empty-request handler/token checks and trusted-node versus textual fallback limits. |
| Documentation examples | All example/IT sources compile and HTML renders; **5 non-service JSON examples pass**, including explicit Jackson handlers and deprecated Gson carrier use. |
| Live Elasticsearch correctness | **44 tests pass**: seven selected documentation classes, 11 tests each through REST4, REST5, OpenSearch client and JDK client against repository-pinned **Elasticsearch 9.5.4** started by Testcontainers/Podman. Covers predicate/sort/aggregation, source/explanation/hit, transform/response/explain, schema export and typed native indexing/projection. This does not claim an OpenSearch-server matrix. |
| Final writer guard | **81 focused utility/output/numeric/API tests pass** after rejecting malformed raw-map/list null members during direct output, including cleanup/replay checks. This is a later correctness guard, not a new performance result. |
| Integrated performance | [Performance report](elasticsearch-json-handlers-performance.md): ordinary-classpath frozen controls, 33 primary pairs and 15 separate allocation pairs, followed by a bounded accessor-only control. Raw fork/provenance artifacts remain preserved. |

Commands and logs for the latest boundary and live-service verification:

```sh
./mvnw -pl backend/elasticsearch clean install \
  -Dtest=ElasticsearchJsonCarrierTest,JsonHandlerBridgeTest,JsonAccessorNumericPolicyTest \
  -DfailIfNoTests=false
./mvnw -pl documentation verify -Dtest=JsonMigrationExamplesTest \
  -Dtest.lucene.skip=true \
  '-Dit.test=Elasticsearch*DslIT,ElasticsearchNativeTypeIT,ElasticsearchHibernateOrmSchemaManagerIT' \
  -DfailIfNoTests=false
```

The logs are `/tmp/json-handler-boundary-gap-install-2.log` and
`/tmp/json-handler-live-es-documentation.log`; each client has an XML summary
under `documentation/target/failsafe-reports`. The live campaign completed with
zero failures/errors/skips, and Testcontainers/Ryuk cleaned up all created servers.
These are actual service tests, distinct from earlier compile-only evidence.

The integrated parser/writer/numeric rows stayed within the investigation
threshold; the accessor-only control retains reported tradeoffs. Adapting reads
remain about **7.65% below** the Search-tree control, while strict reads report
**7.83% below** with a wide confidence interval and inconsistent fork improvement.
One diagnostic GC fork measured strict reads at **48 B/op**, versus **32 B/op**
for the control, and adapting reads at **48 B/op**, versus **64 B/op**. These are
reported measurements, not universal allocation bounds or end-to-end backend
rates. The user's API neutrality/async/Vert.x priorities take precedence over
indefinite tuning of these residual costs.

Frozen performance artifacts remain separate from later request-export and
writer-validation guards. No new whole-backend performance claim is made for the
final correctness install. The [baseline report](elasticsearch-json-handlers-baseline.md)
is historical control evidence; its original candidate-status notes are not the
current implementation status.

### Actual remaining limitations

- Gson and custom-handler imports/exports materialize JSON text at their explicit
  boundaries. Trusted supplied numbers can exceed the default 1000-token input
  limit, while textual fallback imports can reject them; no silent rounding occurs.
- Trees and decoded scalar strings remain materialized; bounded encoded buffers
  do not imply constant memory or a fixed event-loop latency. Validation is a
  traversal, and parser/output/signing/interceptors still execute synchronously.
- Custom handlers must be thread-safe and preserve arbitrary members/nulls. Their
  fidelity and independent representations cannot be inferred from a type token.
  Submitted/returned trees remain subject to the stable-input/read-only ownership rules.
- Live coverage here is selected Elasticsearch 9.5.4 correctness across four
  clients, not the full server/version/JDK matrix. Java 17 compilation does not
  establish Java 17 runtime testing. Full indexing/search throughput, network
  latency, native-image and complete deployment performance are unmeasured.
- Existing client-owned diagnostic/compression materialization and HC5 cancellation
  socket limitations remain documented in the JSON reference. Vert.x example/SPI
  preparation does not claim a built-in Vert.x Elasticsearch transport or automatic offload.

These source references were frozen for the implementation:

| Reference | Revision | Purpose |
| --- | --- | --- |
| Current Search-tree baseline | `8e570e5e7cdd3018c5991e5c864064c1769430f2` | Primary control for this change |
| Historical production Gson baseline | `a853b204cb3dd8d07a49a2779c55bfe25bc024bd` | Secondary control, parent of backend migration `9a135c76d4` |
| Jackson candidate | Record exact commit and patch at each checkpoint | Candidate under evaluation |

Use separate worktrees and matching harness revisions. A harness backport may
adapt imports/API calls but must preserve workload, lifecycle, consumption and
measurement boundaries. Record any mismatch explicitly. Keep historical code in
Git history; do not restore a benchmark-local `legacy` implementation.

## Checkpoints and deliverables

### 1. Contracts, API inventory, and reproducible controls

Owner: one GPT-6.1 Sol agent; parent reviews the contract and migration map.

- Produce a before/after signature table for predicates, sorts, aggregations,
  source/explanation/hit projections, raw responses, explain, schema export,
  native field mappings/values and custom client APIs/SPIs.
- Distinguish released Gson API compatibility from this branch's new Search-tree
  API. Verify release status before requiring compatibility scaffolding for new
  branch-only APIs. Define the treatment of no-handler return methods and deprecated
  Gson overloads, including Java overload ambiguity and generic inference.
- Specify object root validation, null/missing results, trailing input, callback
  failure, chained transformations and ownership. Generic export cannot verify
  an arbitrary user's representation structurally: fidelity is a handler contract.
- Native field values also allow arrays/scalars/null. Bind a general-value handler
  when defining the native field type: conceptually `asNative(handler)` defines
  both indexing input and projection output as `T`. For Gson this is `JsonElement`,
  for the built-in Jackson handler it is `JsonNode`; the Jackson path is direct.
  Keep mapping JSON's object-root requirement separate from the field value type.
  Preserve runtime field Java-type metadata, typed references, converters and
  field compatibility checks as the native options become generic in `T`.
  The historical Gson codec and current Search codec both encode Java null as
  JSON null and decode missing/JSON null as Java null. Preserve that field-level
  convention independently of nested JSON null nodes in the handler's tree.
- Locate the public handler API, built-in Jackson adapter and built-in Gson adapter.
  Keep neutral contracts independent of library types; explicit library adapters
  may name their library. Resolve utility module publication/JPMS, generated-code
  dependencies, BOM/distribution and custom-client SPI implications together.
- Define a common numeric policy for text input and incremental input, integer
  range, decimal scale/value, supported exponent range, numeric equality/coercion,
  and floating signed zero. Characterize actual projection behavior rather than
  inheriting byte-for-byte lexeme assertions. Test long values beyond 2^53,
  BigInteger/BigDecimal, overflow/narrowing, and negative floating zero.
- Scope accepted Jackson trees to ordinary acyclic JSON values. Decide consistent
  treatment of POJO/raw/binary/custom/nonfinite nodes; do not let a direct path
  accidentally enable reflective serialization or raw-text injection.
- Adapt the PoC's transformer to the agreed single-copy policy. Prepare shared
  correctness fixtures and matched benchmark harnesses. Capture the primary
  baseline before changing production code; record historical Gson controls for
  comparable workloads.

Exit evidence: reviewed signatures and package/dependency map, passing contract
fixtures, baseline raw results plus environment/harness provenance, and a list of
explicit compatibility changes. No broad implementation begins with undefined
public return types or an unverified baseline.

### 2. Representative Jackson slice and early performance investigation

Owners: kernel agent and independent benchmark agent in separate worktrees.
Parent integrates and runs timing after agent builds have stopped.

- Apply the checkpoint 1 dependency/API policy to the candidate modules as they
  adopt Databind. Existing enforcer bans and source/compiled boundary tests would
  otherwise prevent the candidate build. Retain checks for unintended exposure.
- Adapt the existing production parsing state machine/tree builder to Jackson
  nodes in an isolated candidate, preserving transport protections and cleanup.
  Retain numeric precision with an explicit node-construction policy; all-BigInteger
  allocation is an experiment, not an automatic requirement for small integers.
- Adapt resumable output traversal to Jackson nodes. Keep within-scalar suspension,
  destination ownership, content-length probing, stable bytes for retries/signing,
  NDJSON framing and synchronous adapters. Do not replace this with an unbounded
  serialized-body cache or a mandatory worker thread.
- Exercise one complete representative flow: handler predicate import, request
  construction, scoped transformation, body writing, fragmented response parsing,
  and source export. Include a generated mapping codec example. Reuse production
  components where their type dependencies allow; label any stand-ins explicitly.
- Measure the direct Jackson and fallback paths, including copying, serialization,
  parsing and result consumption at their true invocation frequency. Verify custom
  `ObjectNode` handlers are invoked and the trusted handler has no hidden conversion.
- Run the early matrix below against the primary baseline. Investigate regressions
  before propagating a new representation through the repository.

Exit evidence: functional slice, precision/ownership/streaming checks, primary
throughput/allocation results, and a cost breakdown for optional handler boundaries.
The slice is not described as a production fast path if it still converts between
Search and Jackson trees. Remove temporary bridges during checkpoint 3.

### 3. Integrate the internal tree and public boundaries

Owner: integration agent, with independently scoped codec/API tasks after shared
signatures are fixed. Parent controls changes to shared utility types and POMs.

- Update remaining approved dependency and API-boundary guards with the cutover,
  including transitive bans in the processor, backend, REST modules and AWS.
- Replace Search-owned nodes across utility internals and backend consumers;
  adapt internal accessors only where backend semantics require them. Avoid a new
  public navigation abstraction. Retain missing/null and singleton-array behavior.
- Adapt `build/json-processor`, generated schema codecs, handwritten codecs,
  registry, validation/equality, field codecs, document/query construction, logging
  and schema export. Preserve reflection-free model processing and null policies.
  In particular, recursive omission of null object members with retained null array
  positions is codec behavior; replacing it with `deepCopy()` is not equivalent.
- Implement handler entry/exit points from checkpoint 1, including native values,
  compatibility bridges, missing source, aggregation result types and explain.
- Integrate request/response types and custom-client SPIs with JDK, REST 4, REST 5
  and OpenSearch clients, plus AWS signing. Changes to shared types may need one
  atomic compiling cutover; do not force an artificial sequence of broken commits.
- Keep exactly one internal tree in the final normal route. No persistent
  Search/Jackson dual-tree representation or round trips through text in ordinary
  query/index/schema processing.

Exit evidence: compiling reactor, focused backend/codec/API tests, four-client
transport tests, AWS signing/replay checks, and a repository reference/dependency
inventory showing intended adapters as the only alternative-library boundaries.

### 4. Integrated correctness and performance review

Owner: validation/performance agent on the integrated candidate; parent reviews.

- Run focused unit and characterization suites, then relevant Elasticsearch and
  OpenSearch integration tests on the supported runtime matrix. Record unavailable
  services as missing evidence, not passes. Include supported JDK coverage for the
  numeric boundary behavior seen in the PoC.
- Test successful/error/empty responses, truncation, malformed input, charsets,
  compressed routes, retry, cancellation, slow consumers and transport ownership.
  Preserve the current client-specific response limits, original-byte accounting
  before transcoding, diagnostic prefixes and logging guards.
- Rerun the performance matrix on the actual production candidate. Include backend
  query construction, native field ingestion, mapping/schema codecs, raw/source
  extraction and real transport serialization/parsing rather than just tree methods.
- Compare against the same baseline and primary workloads used at checkpoint 2.
  Reconcile historical Gson comparisons separately; archived earlier scores are
  context, not directly comparable new measurements.

Exit evidence: integrated test report, raw JMH results and allocation profiles,
retained-buffer bounds, known limitations and explanations for accepted deltas.
Performance thresholds below trigger bounded investigation and documented tradeoffs;
they do not automatically block this implementation. API neutrality, correctness,
async-friendly streaming and the Vert.x-facing contracts remain the main priorities.

### 5. Remove obsolete code and finish the public contract

Owner: cleanup/documentation agent after checkpoint 4; parent reviews final diff.

- Remove superseded public Search-tree implementation, unused codecs/adapters and
  experiment bridges according to the compatibility decision. Update examples,
  API docs, migration guide, module metadata and dependency policies.
- Finalize and tighten the boundary checks updated during checkpoints 2–3:
  neutral contracts remain neutral, adapters may reference their library, internal
  Jackson use is expected. Keep checks that detect accidental leakage elsewhere.
- Update benchmark documentation and fixtures for the accepted numeric semantics.
  Preserve historical reports as historical evidence.
- Run final checks appropriate to touched files, API/dependency inventory and clean
  build. Report test evidence, remaining limitations and performance results.

Exit evidence: no unused replacement tree, reproducible performance artifacts,
reviewable migration guidance, and a clean final implementation diff.

## Performance protocol

Use `integrationtest/performance/backend/scripts/run-json-benchmarks.sh` and its
summarizer as the basis. Extend the harness for handler operations rather than
embedding a second production implementation. First use short runs to verify
selectors, setup and complete consumption. Smoke numbers are not acceptance data.

For primary timing, use the same JDK/JVM, heap/GC, CPU affinity, one JMH thread and
fixtures for baseline/candidate. Start with three independent forks, five one-second
warmup and five one-second measurement iterations, fixed 1 GiB heap and G1.
Use at least eight warmups for numeric/scalar paths with known stabilization issues.
Use matched physical cores for worker controls and record topology and host load.
Interleave baseline/candidate campaigns when investigating close results. Do not
run other agents' builds, tests or benchmarks on the measurement host during timing.
Profile allocation separately with `-prof gc`; record bytes/op and GC behavior.
Keep raw per-fork samples, exact commands, source/patch/harness checksums and metadata.

| Workload | Required coverage | Review checkpoints |
| --- | --- | --- |
| Complete output | Small query, mapping, Unicode, nested, 1 MiB/8 MiB scalar, bulk-10000; fresh/reused; synchronous/incremental | 2 and 4 |
| Complete input | Materialized String and fragmented UTF-8 separately; small, Unicode, scalar, integer/mixed numeric | 2 and 4 |
| Input fragments/buffers | Representative Unicode/scalar/numeric inputs; feeds 64/1024/8192; heap/direct/read-only | 2 guards and 4 |
| Numeric consumption | Build plus no reads, selected reads and repeated reads; precise decimal and large integer | 2 and 4 |
| Accessors | Strict and singleton-array adapting reads on completed equivalent trees | 2 and 4 |
| Handler import/export | Jackson direct, Gson fallback, custom ObjectNode fallback; realistic small and large objects; source per hit | 2 and 4 |
| Transformer | No transformation/path-only control; Jackson single copy; Gson round trip; small/large bodies | 2 and 4 |
| Backend integration | Query/document building, native values per document, schema codecs, projection/result extraction and each transport | 4 |

Allocation and retention are different: report handler text/tree materialization
separately from normal-route encoded buffers and transport queues. Full-tree memory
is expected; encoded scratch space must not grow with body/scalar size. Bounded
input feeding alone is not an event-loop latency guarantee.
Retain the current six-byte encoded cursor scratch bound, tiny destination
capacities 1/2/5/8192, and release-of-references checks. Report normalized output
lengths: fewer bytes per document can affect writer scores independently of speed.

Acceptance rules:

- Correctness, isolation, no hidden direct-path conversion, repeatable signing/body
  bytes and streaming buffer/cleanup invariants are hard requirements.
- A repeatable throughput decrease above 5% on a primary matched workload triggers
  investigation, using the existing repository convention. It is neither a JUnit
  assertion nor proof of statistical significance. Do not average away a material
  regression on a distinct common path. Inconclusive/noisy results require a better
  controlled repeat before drawing a performance conclusion.
- Proposed allocation investigation threshold: inspect repeatable >10% extra bytes/op
  on large normal workloads and absolute growth on small operations. This is a
  diagnostic threshold, not existing repository policy or an automatic release blocker.
  Explain added size-proportional allocation on normal paths even if throughput improves. Optional
  text fallback has an explicit conversion cost, reported separately; compare it
  with the equivalent explicit conversion workload, not a borrowed-node no-op.
- Record material regressions with affected operations, magnitude, measured
  uncertainty and reason. Make bounded corrections where justified, then report
  residual tradeoffs. Do not indefinitely tune or delay API/async/Vert.x work solely
  because a throughput or allocation threshold was exceeded.
- Compare logical JSON/numeric values where spelling is intentionally normalized.
  Retain exact-byte assertions for deterministic output of the same candidate tree,
  replay/signing, escaping and framing. Unsupported extreme-number fixtures belong
  in rejection/limit tests; replace them with supported numeric workloads for timing.
  Never normalize numbers via double in the test oracle.

### Initial commands and execution budget

Run builds and tests in each corresponding worktree before timing. Confirm exact
selectors against the revision being built; adapted harnesses must compile and
pass their setup checks on each control as well as the candidate.

```sh
./mvnw clean install -pl util/json,backend/elasticsearch -am \
  -DskipITs -Dno-build-cache

./mvnw clean install -pl integrationtest/performance/backend/elasticsearch -am \
  -DskipTests -DskipITs -Dno-build-cache

integrationtest/performance/backend/scripts/run-json-benchmarks.sh \
  --skip-build --out /tmp/json-handler-baseline-ingress --warmup 8 \
  --fixtures small,unicode,scalar-1m,numbers-10000 \
  --pattern 'JsonUtf8IngressBenchmark.searchParser$'
```

The output directory must be new for each run. Let the runner select allowed
physical cores or supply a verified selection; do not assume historic `0,2` fits
the current host. Use the existing JSON utility tests, backend codec/accessor/native
characterization tests, transformer tests, body/response tests, each client transport
suite and slow-consumer harness as the core validation map. Add tests for new
contracts rather than duplicating all implementation branches.

Run tiers: smoke/setup checks first; focused early comparison next; the full matrix
after integration. At eight warmups, five measurements and three forks, one parameter
combination costs 39 timed seconds per revision, plus startup/setup. A focused comparison
is likely tens of minutes; a broad matched campaign takes hours, with profiling,
repeats and historical controls adding time. Enumerate case count before scheduling;
the early checkpoint need not repeat every large cross-product.

The audited host has JDK 25, Maven, jq and taskset. A Java 17 target build is not a
Java 17 runtime test. Provision supported JDKs and Elasticsearch/OpenSearch services
for the integrated validation matrix; server-independent JMH needs no server.

## Delegation and review discipline

Use explicit `gpt-6.1-sol` agents with self-contained assignments. Every task gets
its base revision, accepted contracts, owned files/modules, exclusions, required
checks and deliverables. Use isolated worktrees for overlapping migrations. Shared
interfaces and POM changes have one owner. Do not let multiple agents independently
redesign handler signatures or numeric policy.

The parent is the integration/review owner. Delegate independent contract tests,
benchmark preparation and module-local consumers in parallel after signatures are
fixed. Parser/writer and generated-code consumers depend on the internal contract;
client integrations depend on request/response types. Run performance campaigns
serially with other machine-heavy work stopped.

At every checkpoint, report implementation status, exact test commands/results,
performance evidence when applicable, unresolved issues and next dependency.
A checkpoint is an evidence/review boundary, not an automatic permission prompt:
once implementation of this plan is authorized, continue through correctness checks
and reviewed evidence. Performance thresholds are investigation triggers.
Seek a decision only for a material contract change, unresolved compatibility choice
or measured tradeoff beyond the agreed scope. Do not commit or publish merely
because a checkpoint passed; follow the session's explicit delivery instructions.

## Follow-up work outside this implementation

Ship the current Jackson 3 and Gson adapters with the neutral handler API.
Gson and custom-handler fallback conversion remains textual for this implementation.
Additional optional library adapters and direct tree-to-tree conversion can be
considered as future follow-ups; do not implement or explore them in this plan.
