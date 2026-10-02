# Task 2 checkpoint: Search JSON model, accessor SPI and incremental parsing

Task 2 is complete. `hibernate-search-util-json` now provides a Search-owned tree, immutable consumer accessor definitions, and bounded incremental input parsing using Jackson Core 2.22.1. No backend migration, transport integration, request streaming, codecs or performance gate was implemented. Those remain subsequent tasks.

The baseline and Task 1 checkpoint were committed as `cfee8bdf65` before this work. Verification was collected on 2026-10-02 with the repository's Java 17 main-source target, Red Hat OpenJDK 25.0.4.1 and Maven wrapper 3.9.16.

## Module and model

Added `util/json` as a published public module, registered it in the reactor, public BOM and build parent's explicit dependency management, and added it to the distribution dependency set and aggregate Javadoc source paths. Public-parent conventions supply license inclusion, sources, flattening, publication checks and Moditect packaging. There are no generated logging or bean-mapping classes, so this module disables annotation processing without relaxing compiler warnings.

`org.hibernate.search.util.json` contains `JsonElement`, `JsonObject`, `JsonArray`, `JsonPrimitive` and `JsonNull`. These are the actual tree representation. Objects retain insertion order, duplicate properties replace values at their original position, missing members differ from explicit null, and copies have independent mutable containers with shared immutable scalar nodes. Navigation, addition, replacement, removal, iteration and typed scalar getters follow familiar Gson tree conventions. Mutation ownership is documented: a submitted request tree must remain unchanged until all retries complete.

Direct textual scalar getters retain integer syntax/range checks, whereas numeric nodes and explicitly requested `Number` conversions retain narrowing. Big-integer getters require integer text; decimal/exponent lexemes fail as characterized.

Parsed numbers retain their exact JSON lexemes, including exponent spelling, decimal zeroes and negative integer zero. Jackson's async parser normalizes `-0` to `0`; the implementation restores its sign from the token byte span, with tests covering EOF, delimiters and fragmented input. No number is routed through `double` to build the tree.

Intentional model corrections relative to the Task 1 characterization:

- Numeric equality compares exact decimal values, so adjacent integers above `2^53` are distinct. Equal values across integer/decimal representations have equal hashes. Canonicalization uses coefficient digits and a `BigInteger` exponent, avoiding `BigDecimal` scale limits during equality/hashing. Numeric-node getters still narrow/truncate, including `1.9` to long `1` and `4294967297` to integer `1`.
- Numeric primitives snapshot the supplied number's text, including mutable Java `Number` implementations, and reject non-finite/non-JSON numeric representations.
- `toString()` is diagnostic rendering that may allocate a complete string. It preserves numeric text, leaves HTML-sensitive characters literal and escapes surrogate code units, including isolated escaped surrogates in string values, to retain valid diagnostic round trips. It is not a request-body output SPI. Task 3's resumable writer remains unimplemented.

## Accessor behavior

Interfaces and element types are in `org.hibernate.search.util.json.spi`; concrete chains are in `.impl`. Accessors navigate completed trees or mutate trees under construction. The parser never uses them.

```java
JsonAccessor<Long> strict = JsonAccessor.root()
        .property("hits").property("total").property("value").asLong();

JsonAccessor<Long> adapted = JsonAccessor.root()
        .property("hits").unwrapSingletonArrays()
        .property("total").unwrapSingletonArrays()
        .property("value").unwrapSingletonArrays().asLong();
```

Typed access is strict at every intermediate and terminal step. `unwrapSingletonArrays()` explicitly adapts the selected location: nested singleton arrays unwrap repeatedly, empty arrays read as absent, multiple elements fail, and non-arrays pass through for type checking. `asArray()` preserves the selected array unless adaptation was explicitly requested.

Writes traverse existing adapted containers without replacing them; setting the selected terminal location replaces its raw value, including terminal wrappers. `getOrCreate` tests raw presence before adaptation: missing/JSON-null nodes create values, while existing terminal `[]` and `[[null]]` return null without invoking the supplier or replacing the wrappers. This nullable outcome is now documented instead of retaining the old misleading non-null promise.

The surprising intermediate `[]`/`[null]` mutation behavior remains non-replacing, but fails with a descriptive `IllegalStateException` and path instead of an accidental `NullPointerException`. Mutating an absent adapted array similarly reports a descriptive failure. Definitions remain reusable, indexed writes fill gaps with JSON null, and diagnostics retain object/type path annotations. Valid empty property names are now supported. Big-integer element conversion retains the characterized exception that textual primitives can be converted; ordinary numeric accessors still reject strings and booleans.

## Input state machine and ownership

`JsonParsing` is a shareable factory with immutable `JsonParsingOptions`; `JsonParserSession` is single-consumer. `feed(ByteBuffer)` accepts at most the configured input slice, advances the caller's position on success, and drains all available tokens to `NOT_AVAILABLE`. Callers repeat feeding while their buffer has remaining bytes. Input storage belongs to the caller again after the call, including on failure; the position is unspecified on failure. There is no input queue or executor.

Jackson's `ByteBufferFeeder` uses absolute reads and does not advance the supplied buffer position. The session advances the caller explicitly and replaces Jackson's retained input reference with an empty shared read-only buffer after draining. Tests inspect that the retained input buffer has zero capacity and prove a partial tree already exists before response completion. Stack frames hold only the tree container and current member name, not accumulated raw JSON.

`endOfInput()` signals the feeder, drains remaining tokens, and returns exactly one completed root. Empty/whitespace-only input, truncation, malformed syntax, extra roots and trailing garbage fail. A completed root is not exposed before end-of-input validation. An empty HTTP body is a transport decision; it is not converted to JSON null by this module.

Completion, failure and cancellation close parser state and release partial roots, stack frames, feeder references and transcoding buffers. Further operations fail; repeated close is safe. Tests inspect cleanup on each terminal path. There are no hidden scheduling hops. Token completion and tree allocation can take significant CPU even with a bounded feed; this is not an event-loop latency guarantee.

A narrow internal Core factory/parser subclass repairs a Core 2.22.1 continuation-state bug: when a field-name high-surrogate escape finishes exactly at a buffer boundary, Core can return `NOT_AVAILABLE` without updating its current token, causing the following low-surrogate escape to be interpreted as a new field name. The subclass updates that token through Core's protected state helper only in this pending-surrogate case. It uses no reflection or extra input buffering; every split of an escaped supplementary field name is retained as a regression test. This version-specific workaround should be reevaluated when Core is upgraded.

A constant-state UTF-8 validator additionally rejects overlong sequences, encoded surrogates, out-of-range code points, invalid continuations and truncated sequences. Jackson alone accepted some noncanonical byte sequences in focused probes. This validator stores no input and is tested across every split of malformed fixtures.

## Constraints and reusable transport primitives

Constraints are centralized, explicitly configured on each Core factory, and independent of application-wide Jackson defaults:

| Constraint | Default |
| --- | ---: |
| Nesting depth | 1,000 |
| Decoded string length | 20,000,000 characters |
| Field-name length | 50,000 UTF-8 bytes, following Core's async name counting |
| Number length | 1,000, following Core's numeric token counting |
| Input bytes accepted per feed | 8,192 |
| Total original input bytes | Unlimited (`0`) |

These limits are configurable. Core document-length and token-count limits are explicitly disabled; no default response byte cap is introduced. Strict parsing deliberately rejects comments, single-quoted strings, non-finite numbers, leading zeroes and empty input. These constraints and stricter malformed-input handling differ from legacy Gson conveniences; they have not yet been applied to existing backend/transport paths.

`withMaxInputBytes(...)` counts original incoming bytes, including whitespace, before any transcoding. REST adapters must select their existing 100 MiB limit during Task 6. The JDK adapter must keep the default unlimited byte count. Existing transports remain unchanged at this checkpoint.

`createParser(Charset)` provides explicit incremental charset conversion with an 8 KiB pending byte buffer, a 4 Ki-character buffer and an 8 KiB UTF-8 buffer. It retains only incomplete decoder/encoder state between calls, reports malformed source input, and counts original bytes rather than expanded UTF-8 bytes. UTF-16LE/BE, BOM-aware UTF-16, ISO-8859-1, Shift_JIS and stateful ISO-2022-JP are tested at all byte boundaries, with a large supplementary-character UTF-16 fixture testing bounded progress. HTTP charset/header selection remains Task 6. String convenience parsing also encodes in bounded UTF-8 chunks. Escaped isolated surrogates remain available in string values; Core deliberately rejects broken surrogate pairs in field names, an additional strictness difference from Gson.

The main checklist separates these completed reusable primitives from their unchecked Task 6 HTTP application. No claim is made that existing network responses already stream through this parser.

## Verification

The new module has 75 passing cases: model 7, accessors 8, parser 56, API/source boundaries 2 and lifecycle cleanup 2. Parameterized parser cases internally exercise every short-fixture split with heap, direct, sliced and read-only buffers, plus randomized fragmentation of a large Unicode token. No new tests were skipped.

Final focused clean install passed for 15 modules and all 504 goals executed with build caching disabled. It includes 454 cases: 75 new module cases, 155 backend/REST 4 baseline cases, 112 REST 5 cases and 112 OpenSearch cases. There were zero failures/errors and 15 existing assumption skips (five in each REST entity matrix). AWS and upstream modules compiled and packaged; no AWS-specific test was selected. The baseline entity matrices still exercise the existing Gson request producers, not new transport integration.

```bash
./mvnw -pl util/json spotless:apply clean verify -DskipITs -Dno-build-cache

./mvnw clean install \
  -pl util/json,backend/elasticsearch,backend/elasticsearch-client/elasticsearch-rest5-client,backend/elasticsearch-client/opensearch-rest-client,backend/elasticsearch-aws \
  -am \
  -Dtest=JsonModelTest,JsonAccessorTest,JsonParserTest,JsonApiBoundaryTest,JsonParserCleanupTest,JsonAccessorCharacterizationTest,GsonCodecCharacterizationTest,GsonModelCharacterizationTest,ElasticsearchSearchRequestTransformerCharacterizationTest,ElasticsearchRawResultCharacterizationTest,ElasticsearchNativeJsonCharacterizationTest,ElasticsearchSchemaExportCharacterizationTest,GsonParsingTest,ClientRest4GsonHttpEntityTest,ClientRest5GsonHttpEntityTest,ClientOpenSearchGsonHttpEntityTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DskipITs -Dno-build-cache

./mvnw -pl bom/public install -DskipTests -Dno-build-cache
./mvnw -Pdist -pl distribution validate -Dno-build-cache
./mvnw -pl util/json dependency:tree -Dscope=runtime -Dno-build-cache
```

The clean checks pass formatting, Checkstyle, forbidden APIs, dependency convergence, publication enforcement, sources and Moditect packaging. Distribution validation passes both aggregate Javadoc source-path and Lucene-next dependency-copy rules. The first distribution check caught a missing entry in the build parent's explicit artifact management (it does not import the public BOM); this was corrected and final checks passed. No full distribution ZIP or aggregated Javadocs were built.

A named external JPMS consumer compiled and ran a model/parser/accessor smoke test. A second compile correctly rejected imports from `.impl`. The resulting descriptor exports model/SPI publicly, exports `.impl` only to the engine, requires Jackson Core non-transitively and requires common utilities transitively. Runtime dependency inspection contains Jackson Core 2.22.1 and common utilities' existing dependencies; neither Gson nor Databind is present. Maven enforcement bans both dependencies in this module. Tests inspect every compiled model/SPI signature and reject Gson/Jackson/implementation types, and inspect production sources for Gson, Databind or reflective field/method mapping.

Evidence: [focused reactor build](elasticsearch-async-json-task2/verification.log), [module clean verify](elasticsearch-async-json-task2/module-verification.log), [distribution validation](elasticsearch-async-json-task2/distribution-validation.log), [runtime dependencies](elasticsearch-async-json-task2/dependencies.log), [JPMS checks](elasticsearch-async-json-task2/jpms-checks.log).

No performance comparison, encoded output retention measurement, full reactor build, Elasticsearch/OpenSearch service integration, signing/compression test or network lifecycle test was run. Parser token storage and materialized tree size still grow with content; the bounded claim concerns input slices and transcoding buffers. Output retention and throughput/allocation/CPU measurements remain Tasks 3–4; transport limits, charset selection, retry/status interpretation and cancellation integration remain Task 6.
