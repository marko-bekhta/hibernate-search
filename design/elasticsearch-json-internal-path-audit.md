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
| [Schema deep copy](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/gson/impl/GsonUtils.java), called by [metadata builder](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/document/model/lowlevel/impl/LowLevelIndexMetadataBuilder.java) | Object to Gson tree to object; no JSON String. Copies preserve configured null handling. | Task 5: generated codec/tree copy preserving semantics. Do not turn this tree round trip into a text round trip. |
| [Custom settings/mappings files](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/impl/ElasticsearchBackendImpl.java), `customIndexSettings` / `customIndexMappings` | Resource InputStream to UTF-8 Reader to typed Gson codec during bootstrap. | Task 5: bounded stream reads into parser session, then explicit codec decode. No whole-file text merely to use `parse(String)`. Preserve ownership and contextual errors. |
| Native JSON [predicate](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/predicate/impl/ElasticsearchSearchPredicateBuilderFactory.java), [sort](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/sort/impl/ElasticsearchSearchSortBuilderFactory.java), [aggregation](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/aggregation/impl/ElasticsearchSearchAggregationBuilderFactory.java), [mapping](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/dsl/impl/ElasticsearchNativeIndexFieldTypeMappingStepImpl.java) | Explicit caller-supplied `jsonString`, or separate tree overload. | Task 5: String overload uses the accepted character convenience; tree overload works directly on the tree. No simulated network input or String-size dispatch. |
| Raw DSL / raw projection [converters](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/impl/ElasticsearchIndexValueFieldType.java) and [field codec](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/codec/impl/AbstractElasticsearchFieldCodec.java) | Raw DSL deliberately accepts JSON text; raw projection deliberately returns text. Normal native [JSON field encode/decode](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/types/codec/impl/ElasticsearchJsonElementFieldCodec.java) returns an existing node. | Task 5: preserve explicit text contracts, including null and custom scalar behavior; normal values/projections remain tree/value-based. Do not use raw text converters as internal intermediates. |
| [Request transformer](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/search/query/impl/ElasticsearchSearchRequestTransformerContextImpl.java) | Copies the original tree with `deepCopy` when needed. | Task 5: structural Search-node copy preserving mutation isolation; no rendering/reparse. |
| [Request entity content provider](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/client/common/gson/entity/spi/GsonHttpEntityContentProvider.java) | Progressive output serializes a whole body part before checking backpressure; the initial probe can retain a large part. This is known Task 1 output buffering, not a String-parser detour. | Task 6: integrate resumable body/cursor and bounded length probe. Do not copy this full-part buffering into the replacement. |
| [Logging helper](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/client/common/gson/spi/JsonLogHelper.java) and client `log` methods | Deliberate diagnostic text. Successful-request logging returns before rendering when trace is disabled; failure logging is a separate branch. | Tasks 5/6: preserve diagnostic boundaries and disabled-success-log guard; do not reuse logged text for parsing or transport. Review exception rendering separately from ordinary request processing. |
| [Schema export](../backend/elasticsearch/src/main/java/org/hibernate/search/backend/elasticsearch/schema/management/impl/ElasticsearchIndexSchemaExportImpl.java) | Writes JSON through a Writer to a file; no parse round trip. | Task 5: preserve streaming export and formatting contracts; no whole-body String prerequisite. |
| [JSON utility entry points](../util/json/src/main/java/org/hibernate/search/util/json/spi/JsonParsing.java) | `parse(String)` selects the character parser; `createParser()` selects byte-fed sessions. No backend production caller of the new String entry point was found at this checkpoint. | Keep this source-based distinction during migration. Known object codecs decode nodes rather than invoking a parser. |

The existing [codec generator](../build/gson-processor/src/main/java/org/hibernate/search/build/gson/TypeAdapterFactoryWriter.java)
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

- [ ] Task 5: classify every new `JsonParsing.parse(String)` call by its argument's
  source. Native JSON String overloads and raw DSL String values are legitimate;
  explain additional boundaries in this inventory.
- [ ] Task 5: inspect generated sources and direct codec/conversion helpers for
  serialization followed by parsing. Gson compatibility conversion must traverse
  nodes directly. Verify metadata, deep-copy, null, native-field and raw-text
  behavior with characterization tests.
- [ ] Task 5: custom schema files use bounded reads and sessions; verify
  close/EOF/error behavior without whole-file String materialization.
- [ ] Task 6: inspect each adapter's success, error and retry paths. Bytes reach
  sessions without a complete raw String/char-array copy; trees reach codecs
  without rendering. List unavoidable client-owned exceptions explicitly.
- [ ] Task 6: verify request backpressure within large parts, bounded length
  probes, cancellation and replay using retention/slow-consumer fixtures.
  Paginated writes alone do not prove bounded retained serialization.
- [ ] Tasks 5/6: retain disabled logging guards and distinguish explicit String
  projections, diagnostics and file export from internal data flow.
- [ ] Repeat the source audit after each migration checkpoint. Inspect call
  sites rather than imposing a blanket ban on `toString` or String APIs.

Useful searches from the repository root; inspect overloads and callers:

```sh
rg -n 'fromJson\(|parseString\(|parseReader\(|toJsonTree\(' backend/elasticsearch backend/elasticsearch-client build/gson-processor --glob '*.java' --glob '!**/src/test/**'
rg -n 'JsonParsing|readAllBytes|readString|StringReader|InputStreamReader|EntityUtils|ByteArrayOutputStream' backend/elasticsearch backend/elasticsearch-client util/json --glob '*.java' --glob '!**/src/test/**'
rg -n 'fromJsonStringToElement|fromJsonElementToString|deepCopy\(' backend/elasticsearch/src/main/java
```

The pre-migration audit is complete. The checklist above belongs to later
implementation tasks and is deliberately not marked complete by this review.
