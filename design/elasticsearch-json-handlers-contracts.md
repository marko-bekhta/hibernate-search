# JSON handler implementation contract

Checkpoint 1 recommendation, 2026-10-13; source baseline
`8e570e5e7cdd3018c5991e5c864064c1769430f2`. This specifies the implementation
choices for [the execution plan](elasticsearch-json-handlers.md). It is a contract
deliverable, not evidence that implementation, functional checks or timing gates
have passed. The parent owns changes to this contract and the production cutover.

## Release evidence and compatibility decision

Local tag inspection finds no tag containing backend migration `9a135c76d4` or
utility documentation checkpoint `1cd22d67ee`. The latest local tag is
`9.0.0.Beta1`, revision `e11d15f21b909b27041390510b5b87def7111022`.
That tag has Gson application JSON APIs and no `util/json` directory. Its native
options use Gson `JsonElement`; its projection/result/explain/export APIs return
Gson objects. Its mutable transformer `body()` and client request/response
carriers also use Gson. `8.4.0.Final` independently confirms the Gson raw-result
API. These are released surfaces, including a prerelease; the current snapshot
version does not by itself authorize their removal.

The decision is to remove branch-only Search JSON nodes and their overloads,
restore released Gson signatures where practical as deprecated adapters, and
add handler-based application APIs. Do not add Jackson overloads to every
application interface: the explicit Jackson adapter supplies the direct path.
No-handler methods retain their Gson result types. This costs conversion only
when a deprecated or alternative-library boundary is invoked.

This release finding is local evidence, not proof about tags absent from the
checkout or downstream users of unreleased branch builds. Final release review
must confirm the tag inventory. Commands used were `git tag --contains`,
`git show 9.0.0.Beta1:<path>`, and `git ls-tree`.

| Surface | Released Gson behavior | Current branch behavior | Chosen final treatment |
| --- | --- | --- | --- |
| Predicate/sort `fromJson(String)` | JSON text input | Search tree internally, strict parser | Keep descriptor and current migration's strict-parser policy; internal Jackson parse |
| Predicate/sort `fromJson(Gson JsonObject)` | Accept object | Deprecated conversion to Search | Keep deprecated, delegate to Gson handler |
| Aggregation `fromJson(String/JsonObject)` | Return `AggregationFinalStep<Gson JsonObject>` | Return Search object | Restore released result generic; add typed variants |
| `source/explanation/jsonHit()` | Gson projection result | Search object result | Restore deprecated Gson defaults; add `...(handler)` |
| `responseBody()/explain(...)` | Gson object result | Search object result | Restore deprecated Gson defaults; add typed variants |
| Schema export `bodyParts()` | `List<Gson JsonObject>` | Search list | Restore deprecated Gson default; add `bodyParts(handler)` |
| Transformer `body()` | Mutable Gson object | Mutable Search object | Restore deprecated scoped Gson editing; add `transformBody` |
| Native `asNative()` + mapping/options | Gson field value | Search field value | Keep deprecated Gson-specialized interfaces; add typed native interfaces |
| Request/response carrier Gson methods | Gson objects | Search objects | Restore deprecated methods; separate Jackson-native accessors |
| Search tree/model/accessor public API | Absent in inspected tags | Newly published branch module | Remove replacement model; keep necessary accessors internal |
| Custom client factory/provider | Factory accepts `GsonProvider` | Factory accepts `JsonProvider` | Explicit narrow major-version SPI migration, detailed below |

Deprecations use `@Deprecated` without an invented removal release. Add
`forRemoval=true` only if the release owner establishes a removal schedule.
Compiled descriptors and generic signatures both matter: changing only a
generic result can preserve erasure while breaking source use and runtime casts.

## Handler and package contract

Put the neutral API in utility artifact `hibernate-search-util-json`,
public package `org.hibernate.search.util.json`:

```java
public interface JsonHandler<T> {
    Class<T> javaType();
    String toJson(T value);
    T fromJson(String json);
}
```

`T` is the JSON library's tree/value class, not a DTO, entity, or application
domain class. A library's map-backed JSON object is a valid representation;
Search does not add automatic Java Map or POJO binding. A scalar is a scalar node/value in that representation,
not a new permission for arbitrary object mapping. There is no reflective
backend fallback and no inferred handler based on a value's runtime class.

`javaType()` is a non-null, stable runtime token. It must describe every non-null
value accepted/returned by this handler; native fields retain it for existing
runtime indexing/projection validation and metadata. Do not replace it with
`Object.class`, an unchecked discovered class, or a generic `TypeToken` for DTOs.
Handlers must support concurrent invocation, immutable configuration and safe
sharing across scopes, cached predicates, field types, and queries. A custom
handler that uses a non-thread-safe parser must provide its own isolation.
Search does not synchronize handlers or instantiate one per hit.

Place explicit adapters in these backend packages:

```java
// org.hibernate.search.backend.elasticsearch.json.jackson.JacksonJsonHandlers
public static JsonHandler<tools.jackson.databind.node.ObjectNode> object();
public static JsonHandler<tools.jackson.databind.JsonNode> value();

// org.hibernate.search.backend.elasticsearch.gson.GsonJsonHandlers
public static JsonHandler<com.google.gson.JsonObject> object();
public static JsonHandler<com.google.gson.JsonElement> value();
```

Each accessor returns a stable singleton. Both Jackson singletons are trusted
final implementations; only exact singleton identity activates the direct path.
Do not use `instanceof`, `javaType()==ObjectNode.class`, handler equality, subclass
markers, or a publicly implementable optimization SPI. A custom
`JsonHandler<ObjectNode>` or `JsonHandler<JsonNode>` always has its conversion
methods invoked. Jackson 2 and user-configured Jackson 3 handlers use fallback.
The object and value Jackson adapters use the same numeric/parser policy.

The fallback imports `handler.toJson(value)` through the strict internal parser,
and exports by rendering the internal node then invoking `handler.fromJson(text)`.
It initially uses String. Full boundary text materialization is an explicit cost,
never an internal transport route. Generic import checks `javaType().isInstance`
before invoking the handler. Generic export checks the returned runtime type;
the handler promises fidelity and arbitrary-member preservation. Search cannot
structurally inspect an unknown representation without invoking another
conversion, so it does not pretend to validate it by reflecting into the value.

## Application API signatures

`JsonHandler` below is the neutral API. Existing scope/root type parameters are
unchanged. New handler parameters are invariant to preserve useful inference
and the `Class<T>` runtime token. Do not add unrelated covariant wildcards.

```java
// ElasticsearchSearchPredicateFactory and ElasticsearchSearchSortFactory
<T> PredicateFinalStep fromJson(JsonHandler<T> handler, T json);
<T> SortThenStep<SR> fromJson(JsonHandler<T> handler, T json);
// Keep existing fromJson(String) and deprecated fromJson(Gson JsonObject).

// ElasticsearchSearchAggregationFactory
<T> AggregationFinalStep<T> fromJson(JsonHandler<T> handler, T json);
<T> AggregationFinalStep<T> fromJson(String json, JsonHandler<T> resultHandler);
// Keep deprecated one-argument String/Gson-object methods returning Gson objects.

// ElasticsearchSearchProjectionFactory
<T> ProjectionFinalStep<T> source(JsonHandler<T> handler);
<T> ProjectionFinalStep<T> explanation(JsonHandler<T> handler);
<T> ProjectionFinalStep<T> jsonHit(JsonHandler<T> handler);

// ElasticsearchSearchResult
<T> T responseBody(JsonHandler<T> handler);

// ElasticsearchSearchQuery
<T> T explain(JsonHandler<T> handler, Object id);
<T> T explain(JsonHandler<T> handler, String typeName, Object id);

// ElasticsearchIndexSchemaExport
<T> List<T> bodyParts(JsonHandler<T> handler);

// ElasticsearchSearchRequestTransformerContext
<T> void transformBody(JsonHandler<T> handler, java.util.function.UnaryOperator<T> transform);
// Existing path(), path(String), parametersMap(), transformer transform(context)
// remain unchanged; deprecated body() retains a mutable Gson JsonObject.
```

All existing generic relationships remain unchanged. Handler-first explain
overloads are deliberate:
`explain(Object, JsonHandler<T>)` conflicts with released `explain(String,Object)`
when the document id is a String. The chosen forms avoid that ambiguity.

Predicate, sort, aggregation request/result, source, explanation, hit, response,
mapping and schema body-part endpoints require objects even if the supplied
handler is the general value handler. Imported null, array or scalar roots fail
with endpoint context. The aggregation builder retains its output handler until
extraction; it must not infer the handler from the request or aggregation key.
Export runs at the natural endpoint invocation: once per extracted source/hit,
once per raw response call, once per aggregation result, or per schema body part.
There is no eager conversion of every response into every registered library.

Missing optional source/explanation and absent HTTP response bodies return Java
null without invoking `fromJson`; preserve whether each existing endpoint can be
missing. A present non-object on an object result endpoint is an error, not an
absent value. Schema export's returned list is unmodifiable and its elements are
borrowed/read-only. A handler argument is always required and non-null.

Literal `fromJson(null)` remains ambiguous between historical String and Gson
overloads, just as before; removing branch-only Search overloads avoids adding
another ambiguity. New two-argument handler calls reject null object inputs
contextually. `fromJson(String,handler)` is the distinct typed aggregation text
path; do not add competing String input overloads with handler-first erasure.

## Native field types and runtime metadata

Add typed interfaces alongside the released one-parameter options interface,
instead of changing its generic arity:

```java
// ElasticsearchIndexFieldTypeFactory
<T> ElasticsearchNativeIndexFieldTypeTypedMappingStep<T> asNative(JsonHandler<T> handler);

public interface ElasticsearchNativeIndexFieldTypeTypedMappingStep<T> {
    ElasticsearchNativeIndexFieldTypeTypedOptionsStep<?, T> mapping(String json);
    <M> ElasticsearchNativeIndexFieldTypeTypedOptionsStep<?, T>
            mapping(JsonHandler<M> mappingHandler, M json);
}

public interface ElasticsearchNativeIndexFieldTypeTypedOptionsStep<
        S extends ElasticsearchNativeIndexFieldTypeTypedOptionsStep<?, T>, T>
        extends IndexFieldTypeOptionsStep<S, T> {
}

// Preserve this released name/arity, restoring its Gson specialization.
@Deprecated
public interface ElasticsearchNativeIndexFieldTypeOptionsStep<
        S extends ElasticsearchNativeIndexFieldTypeOptionsStep<?>>
        extends ElasticsearchNativeIndexFieldTypeTypedOptionsStep<S, com.google.gson.JsonElement> {
}
```

The deprecated `ElasticsearchNativeIndexFieldTypeMappingStep` may extend the typed
mapping interface specialized to Gson `JsonElement`, overriding `mapping(String)`
with its covariant historical `ElasticsearchNativeIndexFieldTypeOptionsStep<?>`
return. Preserve its one-argument Gson object mapping overload. `asNative()`
retains its released return type and binds `GsonJsonHandlers.value()`. Neither
mapping overload determines the indexing value representation: the handler bound
by `asNative(handler)` does. Mapping input still requires an object.

Typed `asNative(handler)` treats a root array as one JSON field value. Preserve
the released deprecated `asNative()` interpretation: its Gson codec leaves
`canDecodeArrays()` false, so field projections split a root array into multiple
field values. The typed and deprecated codecs must not be compatible because
their array interpretation differs. Nested nulls are retained in a typed array;
a null element split by the legacy route follows the field-level Java-null
convention.

Examples with types visible to callers:

```java
IndexFieldType<tools.jackson.databind.JsonNode> jacksonType = factory
        .asNative(JacksonJsonHandlers.value())
        .mapping("{\"type\":\"object\"}")
        .toIndexFieldType();
IndexFieldType<com.google.gson.JsonElement> gsonType = factory
        .asNative(GsonJsonHandlers.value())
        .mapping(JacksonJsonHandlers.object(), mappingObjectNode)
        .toIndexFieldType();
```

A typed implementation extends
`AbstractElasticsearchIndexFieldTypeOptionsStep<Implementation<T>,T>` and passes
`handler.javaType()` to its constructor, rather than `JsonNode.class` for all
handlers. Its codec is `ElasticsearchJsonHandlerFieldCodec<T>` implementing
`ElasticsearchFieldCodec<T>`. `encode(T)` imports directly or through the
fallback; `decode(JsonNode)` exports through the same bound handler. `decode(Double)`
constructs the standard finite floating node then exports through that handler.
Do not cast a Double or Jackson node to unknown `T`.

The resulting `IndexFieldType<T>`, `IndexFieldReference<T>`, default DSL and
projection converters, field Java-type metadata, and projection type checks all
keep `T`. Existing explicit raw DSL/projection contracts remain String-based;
they operate on internal Jackson nodes and are not intermediate handler routes.
Match/range/exists/sort/projection/terms/range-aggregation support is preserved
where already supported; handler support does not imply Elasticsearch accepts
every JSON shape for every mapping.

Java null encodes to `NullNode.instance` without invoking `toJson`. Missing node
and explicit JSON null decode to Java null without invoking `fromJson`, matching
historical Gson and current Search native-field behavior. Nested nulls remain
null nodes inside a non-null tree. An explicit root null node supplied as `T`
imports as JSON null; its subsequent field projection follows the Java-null
convention. A general handler can represent objects, arrays, scalars and null;
an object-only handler deliberately restricts a particular field's non-null values.

Codec compatibility requires the same codec class, identical handler instance,
and identical stable `javaType()` token, plus existing backend configuration
requirements. Do not declare two arbitrary handlers compatible because their
Java classes or output node types match. No custom configuration fingerprint or
user-supplied equals contract is introduced. Sharing one custom handler instance
is the explicit way to obtain compatible fields across indexes.

## Ownership, transformation and errors

Trusted Jackson import retains its input node; trusted export returns the
internal node. These paths perform no serialization or parsing. Callers keep
inputs stable for the entire Search use period, including indexing/query
execution and retries, and treat returned nodes as borrowed/read-only. Fallback
conversion can incidentally detach values, but the public rule is identical.
Validation traversal is allowed and measured; it is not a defensive tree copy.

`transformBody` operates on the current staged body. For trusted Jackson handlers,
deep-copy that body exactly once before each callback. After the callback,
validate the returned object and install it in staged state without another copy.
The callback may return the supplied object or another valid object. No subsequent
mutation is allowed on either the returned object or any mutable subtree shared
with it. For other handlers, text export builds an independent user representation;
callback success is followed by text import. Do not add a redundant Jackson copy
before fallback export. Failed callback/import/validation leaves the prior staged
body intact. Chained successful transformations see prior staged changes.

The actual request replacement is published only after the outer request
transformer's `transform(context)` returns successfully; an outer failure leaves
the original unchanged. Path-only/parameter-only transformations do not copy or
convert the body. Deprecated `body()` creates one lazy mutable Gson representation,
returns that same representation on repeat calls, and imports it after successful
outer callback completion. Reject mixing deprecated `body()` with typed
`transformBody()` in the same context, avoiding stale live references and hidden
bidirectional synchronization. New API users must choose one editing mechanism.

Handlers receive non-null serialized text containing exactly one strict JSON
root. Imports reject blank text, truncation, trailing roots, wrong object roots,
and null text returned by `toJson`. Parser errors and custom-handler exceptions
become contextual Search exceptions at public operations with the original cause
retained. Do not include unrestricted full payloads in errors. A handler returning
Java null for a present non-null node is an error; nullable endpoint and native
field short-circuits handle Java null explicitly. Custom handlers are responsible
for faithful output; root/type checks do not imply DTO or shape inspection.

## Numeric and ordinary-node policy

Use one token-to-node construction policy for String and incremental byte input.
Do not let ObjectMapper defaults parse floating tokens through double. Integrals
use IntNode/LongNode when exactly representable, otherwise BigIntegerNode. Decimal
tokens use exact BigDecimal/DecimalNode, except negative floating mathematical
zero, which uses standard `DoubleNode(-0.0d)` to preserve the observable sign.
Floating means the source token contains a decimal point or exponent; integer
`-0` can normalize to `0`. Parse/validate the decimal token's range before applying
the zero exception. Supplied finite FloatNode/DoubleNode, including negative zero,
retain their existing value; no conversion can recover precision already lost
by a user's library. Never create a custom signed-zero or numeric node class.

Retain existing parser constraints (including 1000-character numeric input tokens).
This token-length limit applies to text/byte parsing, not an already constructed
direct BigIntegerNode/DecimalNode; larger supplied exact values are not rounded
or silently truncated. Their fallback text conversion can exceed the input cap
and fail explicitly, an observable handler-path difference to document and test.
Supported decimal exponent, resulting BigDecimal scale, and normalized decimal
exponent (`precision - scale - 1`, computed with long arithmetic) lie in
`[-2147483647,2147483647]`; reject `Integer.MIN_VALUE` scale and exponent overflow
consistently. The normalized bound matters: `10e2147483647` has a representable
scale but renders as `1.0E+2147483648`; accepting it would violate the same-policy
round-trip guarantee. A common lexeme-based BigDecimal construction avoids the PoC's
different text/async acceptance of exponent `2147483648`. Validate supplied
DecimalNode scale and normalized exponent under the same policy. This bounds the
supported representation; it does not expand a value into an enormous decimal
string or integer. Integer coercion of a very large decimal
has a separate expansion/overflow guard; exact decimal storage does not expand
it into an enormous integer. Final tests must cover the supported JDK matrix.

Exact numeric equality and schema comparison use integral/decimal mathematical
values and explicit finite floating handling; do not first coerce all numbers
to double. Decimal scale, exponent spelling, numeric node subclass and integer
`-0` spelling are not equality guarantees. Floating negative zero remains visible
in its node, rendering and floating projection even if a logical numeric comparison
treats zeros as equal. Preserve existing typed coercion/truncation semantics unless
a test identifies a required explicit change; no new permissive overflow policy.

Accepted direct Jackson values are ordinary, acyclic JSON graphs of standard
ObjectNode/ArrayNode, StringNode, BooleanNode, NullNode, and finite standard numeric
nodes. Reject MissingNode as supplied data, POJONode/RawValue, binary nodes,
custom/subclassed nodes, nonfinite floating values, and cycles. Missing nodes
used internally to indicate absent fields are not wire data. Sharing a subtree
in an acyclic graph is allowed under the stable-input ownership rule.

Validate external direct nodes on import and transformer replacement before
retaining/installing them. A graph validation walk must not invoke databinding,
raw node serializers, reflective access or `toString` on unknown nodes. Internal
construction creates only approved nodes; transport output checks/cannot recurse
indefinitely on a subsequently corrupted tree, but callers violating the stability
rule do not gain a mutation isolation guarantee. Apply the existing depth/name/
string/number constraints where appropriate. Record validation overhead separately
in direct-boundary benchmarks.

Duplicate object members remain last-value-wins, with encounter-order iteration
consistent with current tree semantics. Object-member null omission is a codec
policy applied recursively; null array slots are preserved. Plain `deepCopy()`
does not implement that policy. String spelling/whitespace and lexical numeric
round trips are intentionally outside the new contract; deterministic candidate
output bytes, escaping, NDJSON framing and signing/replay remain required.

## Client carriers and the narrow provider migration

Keep exactly one internal Jackson tree. Add these native SPI methods while
retaining released Gson methods as deprecated on-demand adapters:

```java
// ElasticsearchRequest
List<ObjectNode> bodyPartsJson();
<T> List<T> bodyParts(JsonHandler<T> handler);
// deprecated List<com.google.gson.JsonObject> bodyParts()

// ElasticsearchRequest.Builder
Builder bodyJson(ObjectNode body);
// deprecated Builder body(com.google.gson.JsonObject body)

// ElasticsearchResponse
ObjectNode bodyJson();
<T> T body(JsonHandler<T> handler);
static ElasticsearchResponse withJsonBody(String hostAndPort, int statusCode,
        String statusMessage, ObjectNode body);
// deprecated Gson constructor and Gson body() remain
```

The native response factory avoids ambiguous overloaded constructors for absent
body `null`. Legacy constructors import once; getters export on invocation and
do not retain a second library tree. Body lists remain unmodifiable; native parts
are stable inputs/borrowed outputs. All four built-in transports, work objects,
logs, retries and signing use native accessors, never deprecated getters.

The unavoidable narrow low-level SPI break is
`ElasticsearchClientFactory.create(..., GsonProvider)` becoming
`create(..., JsonProvider)`. The released provider lives in
`client.common.gson.spi.GsonProvider`; it exposes GsonBuilder suppliers, Gson
objects, TypeToken initialization and old `JsonLogHelper`. Its associated
`client.common.gson.entity.spi` classes expose the old entity/output utilities.
These removed legacy provider/entity helper APIs require explicit major-version
migration guidance. Do not rebuild a reflective Gson schema provider to emulate
them. HTTP configurer/interceptor extension points and their non-JSON lifecycle
contracts remain intact. Deprecated carriers preserve feasible source use, not
binary compatibility for an old factory implementation whose method descriptor
still mentions GsonProvider.

The current branch's `client.common.json.spi.JsonProvider` remains the fixed
Jackson infrastructure holder, not a pluggable user library provider. It supplies
explicit generated-codec services, parser/output services and logging; no public
registry of arbitrary user DTOs. Custom clients migrate response consumption to
`JsonParserSession`, request output to `JsonBody`/`JsonWriteCursor`, and the native
carrier methods. Add an end-to-end custom-client example and migration test.

## Module, dependency and enforcement policy

Retain the branch's `hibernate-search-util-json` artifact and module identity for
the parser, cursor and direct schema codec infrastructure. It is published
(`deploy.skip=false`), but it contains no replacement public Search model and
no Gson dependency. Its approved SPI uses Jackson JsonNode/ObjectNode types:
parser `finish`, body construction and codec encode/decode are explicit
Jackson-native low-level contracts. Keep backend-only accessors in backend
implementation packages; do not create another public navigation system.

Add `tools.jackson.core:jackson-databind:3.2.2` aligned with managed Core 3.2.2,
using central dependency management/BOM and normal transitive publication.
Databind's annotations dependency must be inspected in the actual resolved graph;
the Jackson 3 ecosystem can still use Jackson 2-named annotation artifacts.
Do not ban unrelated `com.fasterxml.jackson` artifacts by prefix. Keep the
REST4 sniffer's independent Jackson 2 Core dependency. Continue banning
Jackson 2 Databind in this JSON stack unless an actual supported dependency
requires a separately reviewed exception.

The backend retains Gson because released convenience signatures require it;
make that explicit in POM/module metadata rather than pretending it is test-only
or optional while public signatures expose it. The generic handler API is neutral
even though the backend artifact has these library dependencies. A separate
optional Gson adapter artifact is unnecessary for this migration.

Update Moditect descriptors generated from the parent POM: exported approved
Jackson-native SPIs and Jackson adapter packages need readable Databind on the
module path, normally `requires transitive tools.jackson.databind` where their
public signatures expose it. Neutral handler application interfaces add no
Jackson references. Retain Gson readability required by deprecated public APIs;
verify actual Jackson/annotation module names from resolved artifacts and test
compiled examples. The default parent rule currently makes third-party requires
non-transitive, so do not assume adding a Maven dependency finishes JPMS work.

Update dependency enforcement alongside adoption, not only at cleanup. Current
Databind bans cover util/json, build/json-processor, backend, REST4/REST5/OpenSearch
modules and AWS, including transitive bans. Replace those with Jackson-major
alignment and exact allowed boundaries. Update compiled/source checks to allow
Jackson in utility/native client SPI and explicit adapters, prohibit it in neutral
application handler signatures, and restrict Gson to enumerated deprecated or
adapter surfaces. Retain negative fixtures and no-reflective-DTO-fallback tests.

Generated codecs use JsonNode/ObjectNode directly, preserving explicit known
model registrations, unknown-type rejection, extra attributes, null policy and
ordering. Processor and generated consumers must cut over together. Distribution,
BOM, module-path examples, docs, Vert.x utility example, benchmark harnesses and
custom-client examples must reference the new approved contracts.

## Required executable acceptance evidence

Before broad propagation, verify generic inference with the signatures above,
native `IndexFieldType<T>`/`IndexFieldReference<T>` metadata, deprecated Gson
source examples, and handler-first explain calls with String ids. Functional
fixtures must cover custom ObjectNode handler invocation, direct node identity,
handler concurrency, runtime-token mismatch, Java/null-node conventions, object
root errors, missing results, exact numeric extremes, FP negative zero, illegal
nodes/cycles, callback failure and cumulative transforms. Typed-native compatibility
must distinguish two differently configured handlers of the same Java type.

Existing transport limits and protections remain mandatory: bounded feeds and
position advancement/reference release; strict UTF-8 and charset transcoding;
original-byte accounting; JDK unlimited/REST 100 MiB defaults; cancellation and
failure cleanup; bounded pending output/scratch; within-scalar suspension;
independent replay/subscription/signing cursors; framing and content-length
behavior; compression ownership; diagnostic truncation and logging guards.
Nodes and signing remain synchronous work. Functional passing and bounded feeding
are not event-loop latency or performance evidence.

No production files were changed for this deliverable. Kernel implementation can
start from the chosen handler, numeric and ordinary-node policy in an isolated
candidate; broader cutover waits for contract fixtures and the early gate.
