# Streaming JSON with Vert.x

This standalone example uses the [Hibernate Search JSON module](../../README.md)
with Vert.x Core. It receives UTF-8 JSON incrementally and streams the completed
Jackson tree back, with one output write in flight. It is an application-level HTTP echo
server, independent of Hibernate Search's Elasticsearch transports.

The complete code is in
[`JsonEchoServer.java`](src/main/java/org/hibernate/search/util/json/example/JsonEchoServer.java).
The example POM supplies Vert.x; the JSON module itself has no Vert.x dependency.

## Run it

Use JDK 17 or newer and run these commands from the repository root. Install the
branch's JSON artifact and its parents/dependencies before building the standalone
example, which is intentionally outside the main Maven reactor:

```shell
./mvnw install -pl util/json -am -DskipTests -DskipITs -Dno-build-cache
./mvnw -f util/json/examples/vertx/pom.xml compile exec:java
```

The server listens on `127.0.0.1:8080`. Set a different port with
`-Dexec.args=8081`. Stop the process with Ctrl+C. The POM pins Vert.x 5.1.8;
the example also uses APIs available in Vert.x 4.5.

In another terminal:

```shell
curl -i http://127.0.0.1:8080/ \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data-binary '{"message":"Hello 🌍","values":[1,1.00,6.022e23]}'
# Malformed, empty or trailing input returns HTTP 400:
curl -i http://127.0.0.1:8080/ --data-binary '{"unfinished":'
curl -i http://127.0.0.1:8080/ --data-binary '{} {}'
```

The echo preserves supported numeric values. Numeric spelling and original whitespace can change.
JSON null is a valid root; empty input is rejected. The server expects uncompressed
UTF-8 bodies and applies a 1 MiB parser input cap. It does not implement content-type
negotiation, authentication, compression or Elasticsearch retry/error semantics.

To exercise fragmented uploads and multiple output buffers with a slow reader:

```shell
scratch_dir=$(mktemp -d)
jq -n '{message: ("🌍" * 30000), values: [range(1000)]}' > "$scratch_dir/request.json"
curl --http1.1 --limit-rate 32k \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data-binary @"$scratch_dir/request.json" \
  http://127.0.0.1:8080/ > "$scratch_dir/response.json"
diff <(jq -S . "$scratch_dir/request.json") <(jq -S . "$scratch_dir/response.json")
rm -r -- "$scratch_dir"
```

The last command uses Bash process substitution and `jq`. Requests larger than
1 MiB are rejected. The response does not need to fit in a single output buffer.

## Reading a request or client response

Create a shareable `JsonParsing` factory and one `JsonParserSession` per message.
`Exchange.start()` installs `handler`, `endHandler` and exception/disconnect
handlers before consuming request bytes. Do not install a body-collecting handler
before this handler.

`Exchange.feed()` copies at most 8192 bytes at a time from a Vert.x `Buffer` into
a per-request scratch array using `getBytes(start, end, destination, offset)`.
It loops on `parser.feed(ByteBuffer)` until the offered region is consumed.
This avoids a whole-body byte array and uses public Vert.x APIs without relying
on Netty internals. The same handler pattern applies to an `HttpClientResponse`.
For a client, select handling of status codes, absent bodies, charset and
compression before feeding or completing the parser.

The parser releases caller storage before `feed` returns, so the next slice can
reuse the scratch array. EOF is reported through `endOfInput()` in the end handler;
only this call returns a complete root. UTF-8 characters and tokens split across
network buffers are handled by the session.

Parse failures close the session, return HTTP 400 and discard subsequent request
chunks without retaining them. Disconnects and stream failures close unfinished
parser/cursor state and reset the response. Terminal guards prevent later
callbacks from reusing released state. A production server should additionally
apply its own connection, timeout and request admission policies.

## Writing a response

The output path creates `JsonBodies.of(root)` and opens one cursor. The small-body
probe determines whether `Content-Length` is known; otherwise the response enables
chunked HTTP/1.1 output. HTTP framing belongs to Vert.x, not the JSON cursor.

`Exchange.pump()` fills an 8192-byte `ByteBuffer`, then copies just the written
region into a fresh Vert.x `Buffer` with `appendBytes`. This copy transfers ownership:
the reusable scratch array and the pending asynchronous write cannot alias.

The next chunk is produced only after `response.write(buffer)` succeeds. A new
context turn schedules it, avoiding recursive callbacks and yielding between
output chunks. This serial write strategy needs neither an unbounded producer
queue nor a `drainHandler`: there is at most one application write outstanding.
A producer allowing multiple queued writes must instead stop when
`writeQueueFull()` reports fullness and resume from `drainHandler`.

Cursor completion closes traversal state and calls `response.end()`.
A failed write or peer disconnect closes the cursor and resets the response;
pending callbacks observe the terminal flag. A new response or retry would open
another cursor, never restart a consumed one. The tree must stay unchanged through
all output attempts.

## Execution and memory

Each exchange's callbacks run serially on its Vert.x context; parser sessions and
cursors are never shared between requests or used concurrently. Parsing and cursor
writes execute synchronously in those callbacks. This example is appropriate for
illustrating ownership and flow control; 8 KiB buffers do not guarantee bounded
CPU time on an event loop. Completing a large token or building its tree may still
be expensive.

The full tree and scalar values remain materialized. Scratch copies and pending
output are bounded; this is not a constant-memory transformation of an arbitrarily
large document, and output starts after input EOF. For stricter latency requirements,
choose an explicit worker policy: pause input, transfer owned slices, process one
session serially, and resume on its Vert.x context after each accepted unit of work.
Coordinate cancellation with worker ownership before closing state.

See the official Vert.x documentation for the transport contracts:

- [ReadStream](https://vertx.io/docs/apidocs/io/vertx/core/streams/ReadStream.html): handlers and input demand.
- [WriteStream](https://vertx.io/docs/apidocs/io/vertx/core/streams/WriteStream.html): write completion and queue flow control.
- [Buffer](https://vertx.io/docs/apidocs/io/vertx/core/buffer/Buffer.html): bounded byte transfers.
- [HttpServerResponse](https://vertx.io/docs/apidocs/io/vertx/core/http/HttpServerResponse.html): framing, completion and disconnect handlers.
