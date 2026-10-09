# Internal JSON parsing-route audit

Source checkpoint: `e13f4935cc`, reviewed on 2026-10-08 before Task 5.
Scope: production sources in `backend/elasticsearch`, both additional REST
client modules, `util/json`, and the existing codec generator. This is a source
and call-site audit, not runtime tracing or an audit of dependency internals.

**Finding:** the inspected metadata and deep-copy paths already operate on trees
without serialized-text round trips. Genuine String-input APIs exist and should
retain the selected character parser. The migration must keep byte sources on
bounded byte-fed parsing and existing trees on direct codecs.

The user accepted Task 4's documented performance tradeoffs provisionally for
migration. The integer UTF-8 gap and scalar variability remain measured
limitations, not resolved performance issues. See the [parsing reference](elasticsearch-json-parsing-reference.md)
for scores and the [masterplan](elasticsearch-async-json.md) for subsequent tasks.

## Call-site inventory and required treatment

| Production path | Current input and handling | Migration requirement |
| --- | --- | --- |
| [JDK client](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/client/impl/ClientJdkElasticsearchClient.java), `GsonJsonMapper` / `JsonObjectBodyHandler` | `BodySubscribers.ofInputStream()` supplies bytes; `InputStreamReader` feeds Gson. No complete response String at this call site. | Task 6: byte-fed parser session with demand, EOF, cancellation and error handling. Do not replace the Reader with a whole-body String conversion. |
| [REST 4](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/client/impl/ClientRest4ElasticsearchClient.java), [REST 5](../backend/elasticsearch-client/elasticsearch-rest5-client/src/main/java/org/hibernate/search/backend/elasticsearch/client/rest5/impl/ClientRest5ElasticsearchClient.java), [OpenSearch](../backend/elasticsearch-client/opensearch-rest-client/src/main/java/org/hibernate/search/backend/elasticsearch/client/opensearch/rest/impl/ClientOpenSearchElasticsearchClient.java), `parseBody` | Entity content stream through a charset-aware Reader to Gson. Client-owned entity buffering is separate from this observation. | Task 6: parse bytes incrementally through the available client consumer/carrier; retain explicit charset support and document concrete client limitations. Do not add `EntityUtils.toString`, `readAllBytes` plus String construction, or text rendering of a parsed tree. |
| [Metadata work](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/work/impl/GetIndexMetadataWork.java), aliases/settings/mappings | `gson.fromJson(JsonElement, type)` consumes existing tree nodes. The method name does not imply parsing text. | Task 5: direct generated/registered codec decode from Search nodes. No `node.toString()` followed by parsing. |
| [Schema deep copy](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/json/impl/JsonUtils.java), called by [metadata builder](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/document/model/lowlevel/impl/LowLevelIndexMetadataBuilder.java) | Object to Gson tree to object; no JSON String. Copies preserve configured null handling. | Task 5: generated codec/tree copy preserving semantics. Do not turn this tree round trip into a text round trip. |
| [Custom settings/mappings files](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/impl/ElasticsearchBackendImpl.java), `customIndexSettings` / `customIndexMappings` | Resource InputStream to UTF-8 Reader to typed Gson codec during bootstrap. | Task 5: bounded stream reads into parser session, then explicit codec decode. No whole-file text merely to use `parse(String)`. Preserve ownership and contextual errors. |
| Native JSON [predicate](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/predicate/impl/ElasticsearchSearchPredicateBuilderFactory.java), [sort](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/sort/impl/ElasticsearchSearchSortBuilderFactory.java), [aggregation](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/aggregation/impl/ElasticsearchSearchAggregationBuilderFactory.java), [mapping](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/dsl/impl/ElasticsearchNativeIndexFieldTypeMappingStepImpl.java) | Explicit caller-supplied `jsonString`, or separate tree overload. | Task 5: String overload uses the accepted character convenience; tree overload works directly on the tree. No simulated network input or String-size dispatch. |
| Raw DSL / raw projection [converters](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/impl/ElasticsearchIndexValueFieldType.java) and [field codec](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/codec/impl/AbstractElasticsearchFieldCodec.java) | Raw DSL deliberately accepts JSON text; raw projection deliberately returns text. Normal native [JSON field encode/decode](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/codec/impl/ElasticsearchJsonElementFieldCodec.java) returns an existing node. | Task 5: preserve explicit text contracts, including null and custom scalar behavior; normal values/projections remain tree/value-based. Do not use raw text converters as internal intermediates. |
| [Request transformer](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/query/impl/ElasticsearchSearchRequestTransformerContextImpl.java) | Copies the original tree with `deepCopy` when needed. | Task 5: structural Search-node copy preserving mutation isolation; no rendering/reparse. |
| [Request entity content provider](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/client/common/json/entity/spi/JsonHttpEntityContentProvider.java) | Progressive output serializes a whole body part before checking backpressure; the initial probe can retain a large part. This is known Task 1 output buffering, not a String-parser detour. | Task 6: integrate resumable body/cursor and bounded length probe. Do not copy this full-part buffering into the replacement. |
| [Logging helper](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/client/common/json/spi/JsonLogHelper.java) and client `log` methods | Deliberate diagnostic text. Successful-request logging returns before rendering when trace is disabled; failure logging is a separate branch. | Tasks 5/6: preserve diagnostic boundaries and disabled-success-log guard; do not reuse logged text for parsing or transport. Review exception rendering separately from ordinary request processing. |
| [Schema export](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/schema/management/impl/ElasticsearchIndexSchemaExportImpl.java) | Writes JSON through a Writer to a file; no parse round trip. | Task 5: preserve streaming export and formatting contracts; no whole-body String prerequisite. |
| [JSON utility entry points](../util/json/src/main/java/org/hibernate/search/util/json/spi/JsonParsing.java) | `parse(String)` selects the character parser; `createParser()` selects byte-fed sessions. No backend production caller of the new String entry point was found at this checkpoint. | Keep this source-based distinction during migration. Known object codecs decode nodes rather than invoking a parser. |

The existing [codec generator](../build/json-processor/src/main/java/org/hibernate/search/build/json/JsonCodecWriter.java)
emits direct typed adapter operations. Its replacement must use direct operations
on Search trees, not generated rendering and reparsing of fields.

## Practical examples

The String convenience is appropriate at an explicit caller-text boundary:

```java
JsonElement query = parsing.parse( suppliedJsonQuery );
```

Existing trees instead go to a codec. These codec names are conceptual until
Task 5 defines its contract:

```java
// Desired: already parsed response -> direct generated codec.
IndexSettings settings = indexSettingsCodec.decode( settingsNode );

// Avoid: render a complete value just to parse it again.
IndexSettings reparsedSettings = indexSettingsCodec.decode(
        parsing.parse( settingsNode.toString() ) );
```

For original byte streams, read into bounded storage and feed a session. HTTP
callbacks must follow the client's demand/lifecycle contract; see the actual
SPI examples in the [parsing reference](elasticsearch-json-parsing-reference.md).
An InputStreamReader itself does not materialize a whole String, but selecting
the byte-fed route avoids the character parser for data already available as bytes.

## Verification checklist for migration agents

- [x] Task 5: classify every new `JsonParsing.parse(String)` call by its argument's
  source. Native JSON String overloads and raw DSL String values are legitimate;
  explain additional boundaries in this inventory.
- [x] Task 5: inspect generated sources and direct codec/conversion helpers for
  serialization followed by parsing. Gson compatibility conversion must traverse
  nodes directly. Verify metadata, deep-copy, null, native-field and raw-text
  behavior with characterization tests.
- [x] Task 5: custom schema files use bounded reads and sessions; verify
  close/EOF/error behavior without whole-file String materialization.
- [ ] Task 6: inspect each adapter's success, error and retry paths. Bytes reach
  sessions without a complete raw String/char-array copy; trees reach codecs
  without rendering. List unavoidable client-owned exceptions explicitly.
- [ ] Task 6: verify request backpressure within large parts, bounded length
  probes, cancellation and replay using retention/slow-consumer fixtures.
  Paginated writes alone do not prove bounded retained serialization.
- [x] Task 5: retain disabled logging guards and distinguish explicit String
  projections, diagnostics and file export from internal data flow.
- [x] Repeat the source audit after the Task 5 migration checkpoint. Inspect call
  sites rather than imposing a blanket ban on `toString` or String APIs.
- [ ] Task 6: repeat the logging and source-route audit for the new transport adapters.

Useful searches from the repository root; inspect overloads and callers:

```sh
rg -n 'decode\(|fromJson\(|parseString\(|parseReader\(|toJsonTree\(' backend/elasticsearch backend/elasticsearch-client build/json-processor --glob '*.java' --glob '!**/src/test/**'
rg -n 'JsonParsing|readAllBytes|readString|StringReader|InputStreamReader|EntityUtils|ByteArrayOutputStream' backend/elasticsearch backend/elasticsearch-client util/json --glob '*.java' --glob '!**/src/test/**'
rg -n 'fromJsonStringToElement|fromJsonElementToString|deepCopy\(' backend/elasticsearch/src/main/java
```

The pre-migration audit is complete. The checklist above belongs to later
implementation tasks and is deliberately not marked complete by this review.

## Task 5 repeated source audit

The earlier table describes the pre-migration checkpoint. Its links now point to
the replacement files where paths moved. Task 5's current inventory is
[parsing-routes.tsv](elasticsearch-async-json-task5/parsing-routes.tsv); production
Gson compatibility references are in
[gson-compatibility.tsv](elasticsearch-async-json-task5/gson-compatibility.tsv).

- `JsonCodecService.decode(String, Class)` directly invokes the character parser.
  Its callers are native predicate/sort/aggregation/mapping String inputs and the
  explicit raw DSL String contract. There is no String-size dispatch or staging.
- `decode(InputStream, Charset, Class)` reads at most 8192 bytes, feeds the bounded
  parser session until each slice is consumed, validates EOF and decodes the tree.
  All four HTTP adapters retain their existing completion/lifecycle boundary;
  custom schema files use the UTF-8 overload and close their resource streams.
- Metadata uses direct registered codec decode. `JsonUtils.deepCopy` composes
  codec encode/decode without text; generated codecs copy extra trees structurally
  according to null policy. Aliases use an explicitly typed map codec.
- Transformers use Search `deepCopy`; native values and raw response projections
  retain their node identity. Gson conversion exists only in the four deprecated
  input default methods and public converter; it walks nodes directly.
- Request entities now use `ElasticsearchJsonBody.ndjson`, the existing cursor
  output SPI and fixed-size pending storage. Schema export writes compact cursor
  bytes directly. Stable unknown large-body length replaces the former length
  discovery by completing serialization/signing.
- `JsonCodecService.write` produces text only for explicit raw projections and
  diagnostics. Its bounded output decoder is not an input parser route. Successful
  response formatting remains guarded by trace enablement in all four clients.

Generated-source and backend-boundary tests check that codecs do not introduce
Gson/Jackson types, reflective field mapping, or rendering/reparsing. Source review
found no ordinary whole-response String/char-array or whole-body byte helper.
Client-owned REST response buffers, compressed-request wrappers and diagnostic
error constructors remain the documented external limitations. This seam does not
complete the unchecked Task 6 demand, cancellation, response consumer or retry
policy work, and source checks alone do not establish network latency guarantees.

The internal integration-test client's historical mapping/settings/alias fixture
constructor remains a deliberate legacy-input boundary: it parses supplied lenient
fixture Strings with Gson and calls the deprecated direct tree converter once.
It does not parse transport bytes, convert requests/responses, or render/reparse a
Search tree. See the [test fixture allowlist](elasticsearch-async-json-task5/test-fixture-compatibility.tsv).
Production native String APIs remain strict and use the character parser.


## Task 6 repeated transport audit

The current adapters replace Task 5's completion-boundary InputStreams with direct
network consumers: JDK `ClientJdkJsonBodySubscriber` feeds `List<ByteBuffer>`;
REST4 `ClientRest4JsonResponseConsumer` reads an 8192-byte decoder buffer;
REST5/OpenSearch response consumers feed HC5-delivered buffers directly. Explicit
charsets select the bounded transcoding session. Completed carrier trees go directly
to `JsonCodecService.decode`; successful/error/retry routes do not render/reparse
JSON or traverse a Search/Gson bridge. Original owned String APIs continue to use
the character parser. Custom-file stream handling remains Task 5's bounded adapter.

Request output uses independent cursors for JDK subscriptions, asynchronous entity
attempts, client-owned OpenSearch stream attempts and signing. Partial/zero writes
retain one bounded suffix and wake transport output. Cancellation releases active
parser/cursor state without depending on low-level socket cleanup.

[Task 6 source audit](elasticsearch-async-json-task6/source-audit.tsv) includes pinned
dependency internals: normal buffered response consumers are replaced, gzip layer
ownership and input caps are preserved, and concrete diagnostic/request-compression
exceptions remain documented. REST error constructors can materialize a normalized
replay String; malformed diagnostics retain an explicitly truncated 8192-byte
prefix. Optional client request compression can still accumulate compressed bytes.
Sniffer/background requests belong to the dependency client, outside Search request
factories. Enabled diagnostics/projections can explicitly format whole trees.

[Task 6 report](elasticsearch-async-json-task6.md) records lifecycle, compression,
charset, retry, signing, retained-storage and real-network evidence, including the
HC5 cancellation/header-delivery race. No event-loop latency guarantee follows from
bounded feeds: growing scalar/tree finalization and signing are synchronous.


## Task 7 final enforcement checkpoint

All planned migration tasks are complete; the historical inventory above describes the
pre-migration checkpoint and its assigned work. [Task 7](elasticsearch-async-json-task7.md)
adds the user-facing [JSON reference](../documentation/src/main/asciidoc/public/reference/_backend-elasticsearch-json.adoc)
and [migration guide](../documentation/src/main/asciidoc/migration/index.adoc), compiled examples,
and enforcement across the JSON utility, codec processor, backend, all client modules and AWS module.
Compiled public/protected contracts reject Jackson types, including generics, superinterfaces,
fields, constructors, throws and CLASS-retained/type-use annotations. The JSON module also
rejects Gson and exposed implementation types. Negative fixtures verify the checker.
Source/codegen checks enforce the exact [five-file Gson compatibility allowlist](elasticsearch-async-json-task7/gson-compatibility.tsv)
and reject reflective field/method mapping. Existing runtime unknown-type and processor
unsupported-type tests continue to prohibit a reflective codec fallback. Scoped Maven rules
reject both Jackson majors' Databind dependencies without banning unrelated modules.
The test-only `TestElasticsearchClient` historical single-quoted fixture boundary and benchmark
legacy implementations remain as documented in Task 5; they must not become production routes.

Ordinary String/byte/tree routes and the explicit Task 6 client-owned exceptions are unchanged.
Misleading Gson names in the two HC5 interceptor adapters are corrected; no compatibility
surface is renamed. The remaining Jackson synchronous comparison writer is exercised by
output tests and the 45-entry benchmark jar, and is not an obsolete normal-route writer.
No automatic worker hop, strict event-loop latency bound, Vert.x transport, or new performance
parity claim follows from this completed migration.
