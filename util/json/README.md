# Hibernate Search JSON utilities

`hibernate-search-util-json` provides a JSON tree model, typed accessors, incremental
parsing, replayable UTF-8 output and explicit Java/tree codecs. It is independent
of Elasticsearch and HTTP. The implementation uses Jackson 3 Core; callers use
Search types without exposing Jackson types or depending on Databind.

This guide covers the module directly. For Hibernate Search backend integration,
see the [JSON reference chapter](../../documentation/src/main/asciidoc/public/reference/_backend-elasticsearch-json.adoc).
For a complete asynchronous HTTP example, see the [Vert.x streaming example](examples/vertx/README.md).

## Dependency and API map

Use the same version as the other Hibernate Search artifacts in your application:

```xml
<dependency>
    <groupId>org.hibernate.search</groupId>
    <artifactId>hibernate-search-util-json</artifactId>
    <version>${hibernate-search.version}</version>
</dependency>
```

The Java module name is `org.hibernate.search.util.json`.

| Need | API |
| --- | --- |
| Build or inspect a tree | `org.hibernate.search.util.json`: `JsonElement`, `JsonObject`, `JsonArray`, `JsonPrimitive`, `JsonNull` |
| Reusable typed paths | `JsonAccessor` and its object/array/unknown-type accessors |
| Parse an owned String | `JsonParsing.parse(String)` |
| Parse incoming bytes | `JsonParsing.createParser()` and `JsonParserSession` |
| Write or replay UTF-8 JSON | `JsonBodies`, `JsonBody`, `JsonWriteCursor` |
| Convert explicitly supported Java types | `JsonCodec`, `JsonCodecs`, `JsonCodecRegistry` |

The services in this table live in `org.hibernate.search.util.json.spi`.
Classes in `impl` are implementation details.

## Building and reading trees

```java
import org.hibernate.search.util.json.JsonArray;
import org.hibernate.search.util.json.JsonNull;
import org.hibernate.search.util.json.JsonObject;

JsonObject document = new JsonObject();
document.addProperty( "title", "Streaming JSON" );
document.addProperty( "pages", 42 );
JsonArray tags = new JsonArray();
tags.add( "java" );
tags.add( "async" );
document.add( "tags", tags );
document.add( "summary", JsonNull.INSTANCE );

String title = document.get( "title" ).getAsString();
boolean missing = document.get( "author" ) == null;
boolean explicitNull = document.get( "summary" ).isJsonNull();
JsonObject editableCopy = document.deepCopy();
```

Objects and arrays are mutable; primitive and null nodes are immutable.
Objects preserve member insertion order. Replacing a duplicate name replaces its
value in place. Adding Java `null` to an object or array stores JSON null.
`deepCopy()` creates independent containers and can share immutable nodes.

Parsed numbers retain their JSON lexemes, including decimal scale and exponent
spelling, for output. Numeric equality compares exact numeric values and ignores
decimal scale. Numeric getters can narrow, truncate and overflow; use
`getAsBigDecimal()` and Java's exact conversions when your application needs
exact integers. `toString()` is diagnostic rendering and can allocate the whole
encoded document; use the output APIs for streaming.

## Typed paths and singleton arrays

```java
import org.hibernate.search.util.json.spi.JsonAccessor;

JsonAccessor<String> titlePath = JsonAccessor.root().property( "title" ).asString();
String title = titlePath.get( document ).orElse( "Untitled" );
titlePath.set( document, "A new title" );

JsonAccessor<String> nestedValue = JsonAccessor.root()
        .property( "parent" ).unwrapSingletonArrays().asObject()
        .property( "value" ).unwrapSingletonArrays().asString();
```

Accessors are immutable definitions reusable across trees. Typed paths reject
values of another JSON type, including arrays: `asString()` requires a string
rather than converting a number. Typed reads return an empty `Optional` for
missing or JSON-null values. Untyped paths can preserve explicit `JsonNull`.
Accessors navigate completed trees; they do not participate in parser construction.

Opt into `unwrapSingletonArrays()` separately at every location that needs it.
It repeatedly unwraps singleton arrays, treats an empty array as absent on reads,
and rejects arrays with multiple elements. Writes through an adapted parent
preserve its wrappers; setting the selected terminal location replaces that
location, including any terminal wrappers. `asArray()` keeps the array itself.

`getOrCreate` creates raw missing/JSON-null locations, including parents as needed.
An existing array that adapts to absence is not replaced: the supplier is not
called, and terminal `getOrCreate` returns Java null. Traversing such an absent
adapted parent for a write throws `IllegalStateException`.

## Parsing Strings and incoming bytes

For an already materialized String:

```java
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonParsing;

JsonParsing parsing = new JsonParsing();
JsonElement tree = parsing.parse( "{\"title\":\"Streaming JSON\"}" );
```

This call parses characters directly without first encoding the whole String.
Parsing requires strict JSON and one root value: comments, single quotes,
non-finite numbers and trailing roots are rejected. A root can be an object,
array, scalar or JSON null.

For a sequence of UTF-8 transport chunks, keep one parser session alive from
first input through EOF:

```java
import java.nio.ByteBuffer;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;

final class IncomingJson implements AutoCloseable {
    private final JsonParserSession parser;

    IncomingJson(JsonParsing parsing) {
        parser = parsing.createParser();
    }

    void onChunk(ByteBuffer input) {
        while ( input.hasRemaining() ) {
            parser.feed( input );
        }
    }

    JsonElement onEnd() {
        try {
            return parser.endOfInput();
        }
        finally {
            close();
        }
    }

    @Override
    public void close() {
        parser.close();
    }
}
```

The transport must also call `close()` on failure, cancellation or disconnect.
Do not use a try-with-resources block that closes the session immediately after
installing asynchronous handlers.

Each `feed` accepts at most `maxInputChunkSize()` bytes, advances the buffer's
position by that slice on success and processes the available tokens before
returning. Loop until the chunk is consumed. A buffer can be heap-backed, direct
or read-only, and its position/limit select the offered region. The session does
not retain caller storage after the call, including on failure; buffer position
is unspecified after failure. Never mutate input during `feed`.

UTF-8 code points, JSON escapes and tokens can span chunks. Only `endOfInput()`
returns the root, after validating EOF and trailing input. Empty/whitespace input
throws `JsonEmptyInputException`; a transport can choose to interpret this as an
absent HTTP body. The JSON text `null` instead returns `JsonNull.INSTANCE`.

Use `createParser(charset)` when the transport explicitly selects another source
charset. It transcodes incrementally with bounded scratch space. Charset selection,
HTTP body absence, decompression and retry policy belong to the transport.

### Constraints and memory

```java
import org.hibernate.search.util.json.spi.JsonParsingOptions;

JsonParsing capped = new JsonParsing(
        JsonParsingOptions.DEFAULT.withMaxInputBytes( 1024 * 1024 ) );
```

| Default constraint | Value |
| --- | ---: |
| Nesting depth | 1000 |
| Decoded string length | 20,000,000 characters |
| Decoded name length | 50,000 UTF-8 bytes |
| Number token length | 1000, using Core's token-length counting |
| Bytes accepted per feed | 8192 |
| Total input bytes | Unlimited (`0`) |

The full `JsonParsingOptions` constructor configures all six values. The byte cap
counts original source bytes before charset transcoding. For String parsing it
counts the equivalent UTF-8 length. If the transport decompresses first, a parser
cap applies to the decompressed input it receives; compressed wire limits are a
separate transport policy.

Input slices and scratch buffers are bounded. The completed tree, names and scalar
Strings still grow with the document. This is incremental tree construction,
not a token subscription API or a constant-memory parser for arbitrarily large
documents. `feed` and EOF processing are synchronous CPU work; a bounded input
slice does not guarantee an event-loop latency bound.

## Streaming UTF-8 output and replay

```java
import java.nio.ByteBuffer;
import org.hibernate.search.util.json.spi.JsonBodies;
import org.hibernate.search.util.json.spi.JsonBody;
import org.hibernate.search.util.json.spi.JsonWriteCursor;

JsonBody body = JsonBodies.of( document );
ByteBuffer buffer = ByteBuffer.allocate( 8192 );
try ( JsonWriteCursor cursor = body.openCursor() ) {
    while ( !cursor.isComplete() ) {
        buffer.clear();
        cursor.write( buffer );
        buffer.flip();
        // Your synchronous sink must consume the entire remaining region here.
        consumeFully( buffer );
    }
}
```

`consumeFully` above represents your sink. A channel accepting a partial write
must finish the buffer's unread suffix before asking the cursor for more. An
asynchronous sink must finish using emitted storage before you reuse it, or receive
its own copy. The [Vert.x example](examples/vertx/README.md#writing-a-response)
shows that ownership transfer and waits for each write before producing another.

`write` fills only the destination's remaining region, advances its position and
leaves its limit unchanged. Zero capacity makes no progress; a read-only destination
is rejected. The cursor retains traversal state, not the destination buffer, and
can pause within a scalar or escape. Close/cancel it on errors and cancellation;
closing does not close the caller's sink.

`JsonBodies.of(root)` probes up to 8 KiB once to determine a small body's length
without retaining the probe's bytes. `contentLength()` is then stable: a larger
body returns `-1`, even after output or signing. Select appropriate HTTP framing
for unknown lengths. Use `JsonBodies.openCursor(root)` to skip the probe when
only a traversal is needed; `withContentLengthProbe` can wrap a custom body.

`JsonBody.writeTo(OutputStream)` and `openStream()` drain independent cursors with
8 KiB storage for synchronous consumers and signing. `writeTo` neither closes nor
flushes the destination. Close each opened input stream. Open a new cursor or
stream for every retry; their traversal positions are independent.

Keep source trees acyclic and unchanged from body creation through every read,
signing operation and retry. Bodies reference trees without freezing or copying
them. Share factories, accessor definitions and bodies with stable source trees;
use each parser session/cursor/stream from one consumer at a time.

Output is UTF-8, preserves numeric lexemes and object order, and escapes JSON
controls, HTML-sensitive characters and U+2028/U+2029. Valid surrogate pairs are
encoded as UTF-8; isolated surrogates are escaped. It adds no newline or HTTP
framing. NDJSON separators belong to the caller; one parser session accepts one
JSON root, not multiple NDJSON records.

## Explicit Java/tree codecs

```java
import java.util.List;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonCodec;
import org.hibernate.search.util.json.spi.JsonCodecs;

JsonCodec<List<String>> strings = JsonCodecs.list( JsonCodecs.STRING );
JsonElement encoded = strings.encode( List.of( "java", "async" ), false );
List<String> decoded = strings.decode( encoded );
```

`JsonCodecs` supplies scalar, tree, list and string-keyed map codecs. The
`serializeNulls` flag controls omission of null object members in codecs that
construct objects; null array positions remain present. `JsonCodecs.copy` applies
that policy recursively. Scalar codecs use model conversions; use typed accessors
when you need strict JSON-type checks.

Implement `JsonCodec<T>` for application types by explicitly decoding/encoding
known fields. `JsonCodecRegistry` holds explicit class registrations;
`reference(type)` defers lookup for recursive model graphs. Unknown types fail
instead of using reflective mapping. Codecs receive completed trees, independently
of byte parsing and output scheduling.

## Vert.x and other asynchronous transports

The [runnable Vert.x example](examples/vertx/README.md) receives request chunks,
parses them incrementally and streams the completed tree back. A short input
adapter uses the public Vert.x buffer API:

```java
// Inside a Vert.x request/response Buffer handler, with one session and scratch
// array per message; keep the session until endHandler or another terminal event.
for ( int offset = 0; offset < chunk.length(); ) {
    int count = Math.min( scratch.length, chunk.length() - offset );
    chunk.getBytes( offset, offset + count, scratch, 0 );
    ByteBuffer input = ByteBuffer.wrap( scratch, 0, count );
    while ( input.hasRemaining() ) {
        parser.feed( input );
    }
    offset += count;
}
```

The full example supplies EOF, failure and disconnect handlers, bounded copies,
HTTP framing and output flow control. There is no built-in Vert.x Elasticsearch
transport. Applications choose execution and demand policy. When offloading,
pause ingress, transfer owned bytes, serialize work for a session and resume on
the owning context only after consuming them; never queue unbounded chunks or
run overlapping feeds. Synchronous signing/interceptors need their own policy too.

## Building and verification

From the repository root:

```shell
./mvnw install -pl util/json -am -DskipTests -DskipITs -Dno-build-cache
./mvnw test -pl util/json -DskipITs -Dno-build-cache
```

The module's tests cover model/accessor behavior, parser fragmentation and limits,
output correctness/replay, buffer retention and API dependency boundaries.
See the [benchmark guide](../../integrationtest/performance/backend/README.md#server-independent-json-comparisons)
for timing and allocation experiments, separately from correctness checks.
