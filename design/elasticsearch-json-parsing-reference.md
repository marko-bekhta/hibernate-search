# Elasticsearch JSON parsing reference

**Current decision: keep the pre-experiment character parser for already supplied
Strings.** The user accepted this choice after the bounded UTF-8 experiment.
The experiment and its fixtures remain committed as reference; its encoders are
benchmark alternatives. The async UTF-8 SPI already exists, but live Elasticsearch
transports still use Gson. Tasks 5 and 6 have not started.

This guide brings together the [Task 4 checkpoint](elasticsearch-async-json-task4.md),
[corrected follow-up](elasticsearch-async-json-task4-followup.md) and
[owned-String experiment](elasticsearch-async-json-string-utf8.md), and
[numeric UTF-8 follow-up](elasticsearch-async-json-numeric-utf8.md). Acceptance of
the String-path tradeoff does **not** close Task 4: the latest heap-integer UTF-8
campaign remains 13.2% behind Gson after a supported improvement, and scalar
UTF-8 results still vary materially.

## Choose according to the input you already have

| Input/use case | Route | Result and status |
| --- | --- | --- |
| A JSON query, fixture or configuration already supplied as `String` | `JsonParsing.parse(json)` uses Jackson Core's character parser | Complete Search tree; retained convenience path |
| Incoming UTF-8 buffers, possibly arriving over time | A new `JsonParserSession` from `createParser()`, repeated `feed`, then `endOfInput` | Complete Search tree after EOF; SPI available, live transport integration pending |
| Existing Elasticsearch transport processing | Existing Gson code | Complete Gson tree; unchanged by the experiment |
| Investigating upstream token/string completion | Minimal Jackson consumed-token benchmark | Tokens and completed scalar Strings, without a tree; diagnostic only |
| Testing String-to-UTF-8 alternatives | Benchmark-only wrapped or staged standard encoder feeding the async session | Complete Search tree; neither alternative adopted |

The factories are shareable; each parser session has one consumer and belongs to
one input/response. Search keeps strict validation, limits and numeric lexemes,
including `-0`, decimal/exponent spelling and huge exponents. Gson timings below
compare valid inputs; they do not imply identical acceptance policies.

### Already supplied JSON String

Use the existing convenience API directly. There is no need to encode this input
into a new complete byte array first.

```java
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonParsing;

String json = "{\"query\":{\"term\":{\"name\":\"test\"}},\"size\":10}";
JsonElement query = new JsonParsing().parse( json );
```

For repeated calls, retain and share the `JsonParsing` factory. Parsing remains
synchronous. It validates raw UTF-16 and, when a byte cap is configured, counts
the original String's UTF-8 length without constructing an encoded body.

### Caller-owned UTF-8 chunks

This synchronous illustrative helper uses the actual SPI. It does not implement
a transport or imply that a response's chunks must be accumulated into a list.

```java
import java.nio.ByteBuffer;
import org.hibernate.search.util.json.JsonElement;
import org.hibernate.search.util.json.spi.JsonParserSession;
import org.hibernate.search.util.json.spi.JsonParsing;

static JsonElement parseUtf8(JsonParsing parsing, Iterable<ByteBuffer> chunks) {
    try ( JsonParserSession session = parsing.createParser() ) {
        for ( ByteBuffer chunk : chunks ) {
            while ( chunk.hasRemaining() ) {
                session.feed( chunk );
            }
        }
        return session.endOfInput();
    }
}
```

`feed` can consume only a bounded slice and advances the buffer's position, so
the loop is required. Do not mutate storage during a feed; process its remaining
bytes before reusing or releasing the buffer. Ownership returns after each feed,
including failure, when the buffer position is unspecified. A result is returned
only at EOF, after rejecting empty,
truncated or trailing input. The helper closes on success or exception.

A future transport can create the session once, call those same feed operations
from successive chunk callbacks, call `endOfInput` on completion, and call
`close` on error or cancellation. Keep that session alive across callbacks;
closing a try-with-resources block immediately after registering callbacks would
close it prematurely.

Here, **async means resumable when more input is needed**. It creates no worker or
thread offload and promises no fixed event-loop latency. Completing a large token
can still take significant CPU time. Decoded field Strings and the complete tree
remain materialized; bounded ingress is not a constant-memory tree parser.

### Benchmark-only String-to-UTF-8 experiment

The experiment compares this conceptual flow, not a new public parsing method:

```text
Already owned String
  -> CharBuffer view, OR reusable 4096-char staging scratch
  -> standard UTF-8 CharsetEncoder with malformed-input reporting
  -> reusable 8192-byte scratch
  -> session.feed while bytes remain
  -> encoder completion/flush, parser endOfInput, complete Search tree, close
```

Staging uses bounded `String.getChars` windows and preserves a high surrogate
across windows. Neither alternative uses a whole-input `getBytes`/`toCharArray`,
a second complete String body or mandatory offload. The
[experiment helper](../integrationtest/performance/backend/elasticsearch/src/main/java/org/hibernate/search/integrationtest/performance/backend/elasticsearch/JsonStringUtf8Encoding.java)
keeps all encoding, session/buffer construction, feeds, EOF and tree building
inside measurement. Scratch is fixed, while original input, token storage and
tree memory still depend on the document.

## Fixture names and sizes

Sizes below describe one JSON root, excluding the NDJSON newline used by output
benchmarks. In particular, the two Unicode workloads are substantially different.

| Fixture | JSON input size | Content/shape |
| --- | ---: | --- |
| `small` | 44 UTF-8 bytes / 44 UTF-16 units | Nested term query shown above |
| Earlier `unicode` | **3,851 UTF-8 bytes / 2,571 UTF-16 units** | One value repeating `é漢😀`, newline, quote and backslash 256 times; JSON escapes included |
| `scalar-1m` | 1,048,587 bytes / UTF-16 units | One 1 MiB ASCII value |
| `numbers-10000` | 48,902 bytes / UTF-16 units | One array of integers 0–9999 in `values` |
| `numbers-mixed-10000` | 82,512 bytes / UTF-16 units | One 10,000-value array cycling negative/decimal/exponent/overflow lexemes, including `-0` and a huge exponent |
| Experiment `unicode-1m` | **1,500,011 UTF-8 bytes / 1,000,011 UTF-16 units** | The Unicode/escape pattern repeated 100,000 times in one value |
| Experiment `fields-1m` | 1,140,891 bytes / UTF-16 units | 18,000 short property names and 48-character String values |

A large document with many short values is not equivalent to one large token.
Nor does document length identify whether String-to-UTF-8 encoding is helpful.

## Reading the measurements

Tables show complete operations/second unless explicitly labelled token
diagnostics. `±` is the reported JMH 99.9% error interval. Compare implementations
within the same row/campaign; do not divide scores from different input forms,
harnesses or campaigns to infer a speedup or CPU-cost fraction.

These primary campaigns used three forks, one JMH thread, CPUs 0 and 2 on distinct
physical cores, 1 GiB fixed heap, G1 and `ActiveProcessorCount=2`, on OpenJDK
25.0.4.1 with production compilation targeting Java 17. Checkpoint campaigns used
five one-second warmups/five measurements; corrected follow-up and String
experiment campaigns used eight warmups/five measurements. Affinity is not an
exclusive CPU reservation. Raw results include fork samples, source/artifact
identity, commands and environment. Profiles and smoke runs are separate.

### Previous complete-tree parsing: supplied String

The [checkpoint String campaign](elasticsearch-async-json-task4/parsers-final/jmh.json)
compared the existing character route against Gson on the smaller fixtures:

| Supplied String fixture | Gson ops/s | Search character ops/s |
| --- | ---: | ---: |
| `small`, 44 units | 3,160,476 ±56,180 | 3,352,189 ±62,282 |
| Earlier `unicode`, 2,571 units | 187,333 ±4,471 | 312,664 ±12,229 |

The later [corrected scalar campaign](elasticsearch-async-json-task4/followup-scalar-final/jmh.json)
measured the 1 MiB String at **1,293 ±5 Gson versus 692 ±4 Search**. The new
standalone String experiment below measured **1,277 ±71 versus 514 ±4** despite
unchanged production parser source. The harnesses have different setup lifecycles;
no cause of this absolute difference has been established. Both retain a material
large-scalar character-path cost. The user accepts retaining that path.

### Previous complete-tree parsing: caller-owned UTF-8

Here Gson includes reader/decoder work; Search includes bounded feeds, UTF-8
validation, EOF, tree building and close. Input bytes are already supplied, so
String encoding is not timed. Corrected follow-up rows cap offered/read fragments
at 8192 bytes.

| UTF-8 fixture | Campaign | Gson ops/s | Search async ops/s |
| --- | --- | ---: | ---: |
| `small`, 44 bytes | [Historical checkpoint](elasticsearch-async-json-task4/utf8-ingress/jmh.json) | 1,145,901 ±10,174 | 3,451,064 ±112,057 |
| Earlier `unicode`, 3,851 bytes | [Corrected follow-up](elasticsearch-async-json-task4/followup-utf8-final/jmh.json) | 102,362 ±1,059 | 137,410 ±653 |
| `scalar-1m`, 1,048,587 bytes | [Corrected run A](elasticsearch-async-json-task4/followup-utf8-final/jmh.json) | 1,248 ±19 | 1,273 ±19 |
| `scalar-1m`, same bytes | [Corrected run B](elasticsearch-async-json-task4/followup-utf8-scalar-repeat/jmh.json) | 1,224 ±28 | 1,114 ±5 |
| `numbers-10000`, 48,902 bytes | [Corrected numeric follow-up](elasticsearch-async-json-task4/followup-numeric-final/jmh.json) | 4,616 ±99 | 3,621 ±105 |

The historical ingress state had a duplicated annotated setup; that small-input
row is context, not a corrected gate result. The follow-up states invoke setup
once. Corrected scalar runs A/B use **identical source and artifact**, yet Search
moves from **+2.0% to -9.0%** versus Gson. The slower run has tightly clustered
Search forks; this variability remains unresolved. In that corrected follow-up,
numeric UTF-8 was **21.6% behind Gson**, with the regression present in every
final Search fork. The later numeric campaign below measures the retained change. None of
these in-memory measurements establishes network or event-loop latency.

### Current numeric UTF-8 heap follow-up

The existing Core ByteBuffer parser now reads accessible heap slices through their
array and offset, preserving canonical validation, bounded feeds, handoff clearing,
strict numeric lexemes/cache and the escaped-name workaround. Direct/read-only
storage retains Core's getters; the character String route is unchanged.
The [focused report](elasticsearch-async-json-numeric-utf8.md) documents rejected
builder/validator experiments, direct fork variability, allocation and exact scope.

| Complete UTF-8 tree, 8192-byte heap feeds | Gson ops/s | Search ops/s | Campaign |
| --- | ---: | ---: | --- |
| `numbers-10000` | 4,696 ±17 | 4,077 ±110 | [Retained heap-access primary](elasticsearch-async-json-numeric-utf8/heap-access-primary/jmh.json) |
| `numbers-mixed-10000` | 3,176 ±88 | 3,123 ±20 | [Same primary](elasticsearch-async-json-numeric-utf8/heap-access-primary/jmh.json) |
| Earlier `unicode`, 3,851 bytes | 108,621 ±5,982 | 155,809 ±9,138 | [Separate final guard](elasticsearch-async-json-numeric-utf8/unicode-control/jmh.json) |

Matched original-code integer Search was 3,624 ±39 with the final ingress harness,
so heap access improves it 12.5%; the retained result still loses 13.2% to Gson.
Mixed intervals overlap. Separate GC allocation is 1,130,610 versus 985,666 B/op
for integer Search/Gson; node storage is unchanged. Minimal token/number/tree
allocation diagnostics are not identical-work competitors or CPU-cost fractions.
This follow-up does not resolve scalar UTF-8 variability or complete Task 4.
Historical rows above retain their original campaign identities.

### Complete-tree String-to-UTF-8 experiment

All four methods in the [same experiment campaign](elasticsearch-async-json-string-utf8/primary/jmh.json)
start with String and observe complete trees. Encoder and scratch construction,
encoding, feeding, EOF and close are included for both encoded alternatives.
There is no pre-encoded competitor in this table.

| Supplied String fixture | Gson ops/s | Character ops/s | Wrapped encoding + async ops/s | Staged encoding + async ops/s |
| --- | ---: | ---: | ---: | ---: |
| `scalar-1m` | 1,276.6 ±71.5 | 513.7 ±3.8 | 298.9 ±1.3 | 1,216.3 ±23.6 |
| **Large `unicode-1m`** | 292.2 ±7.8 | 643.3 ±5.1 | 181.9 ±6.5 | 218.1 ±7.5 |
| `small` | 3,187,916 ±53,778 | 3,270,509 ±49,063 | 1,208,721 ±6,018 | 812,156 ±10,904 |
| `fields-1m` | 292.5 ±4.9 | 348.4 ±3.1 | 197.5 ±2.4 | 384.9 ±3.3 |

Staging improves the large ASCII case 136.8% over character parsing, but regresses
large Unicode 66.1% and small input 75.2%. The ASCII staged/Gson intervals overlap;
the 4.7% lower staged estimate is not a universal 5% bound. Many-fields staging
improves throughput 10.5%, while a [separate allocation profile](elasticsearch-async-json-string-utf8/allocation/jmh.json)
allocates 43.6% more than character parsing. Small staging allocates 18,432 B/op
versus character 1,688 B/op. These results support the accepted default and do
not justify automatic dispatch based on input length.

Encoding-only diagnostics are not complete-tree competitors: the separate profile
measured ASCII wrapped/staged encoding at 447/23,864 ops/s, with roughly
8.4/16.6 kB allocation per operation across payload sizes. This corroborates the
focused staging optimization without assigning exact additive parsing costs.
See the [experiment report](elasticsearch-async-json-string-utf8.md) and
[evidence index](elasticsearch-async-json-string-utf8/README.md) for allocation,
boundary/limit checks and all fork means.

### Numeric String parsing plus reads

The [corrected numeric campaign](elasticsearch-async-json-task4/followup-numeric-final/jmh.json)
constructs a complete 10,000-integer tree before the read workload. These are not
getter-only benchmarks or the same operation as tree construction alone.

| Reads after String parsing | Gson ops/s | Search character + cached numbers ops/s |
| --- | ---: | ---: |
| None; observe the tree | 4,685 ±39 | 4,465 ±32 |
| Every hundredth value once; 100 reads | 4,671 ±29 | 4,473 ±31 |
| Every value ten times; 100,000 reads | 1,305 ±8 | 2,793 ±12 |

The implementation retains immutable eager numeric results while combining grammar
validation and integer accumulation. No/selected-read estimates are close to the
5% investigation threshold in this campaign; repeated reads benefit from the
cache. The separate on-demand experiment saved construction allocation but lost
that repeated-read benefit and did not resolve numeric UTF-8 parsing. Its full
tradeoff and historical harness limitations are in the [follow-up report](elasticsearch-async-json-task4-followup.md).

### Minimal Jackson consumed-token diagnostics

These benchmarks force `getString()` for names/scalars and observe completed
values, rather than advancing lazy tokens without completing them. They build
**no tree** and omit Search's extra raw UTF-16/canonical UTF-8 validation, decoded
name-byte checks, strict single-root tree builder and ownership handoff clearing.
The public Core async diagnostic also omits the escaped-name workaround. They
are upstream diagnostics on valid fixtures, not
alternative production parsers with the same contract.

| Original input | Fixture | Core consumed-token ops/s | Campaign |
| --- | --- | ---: | --- |
| String | 1 MiB ASCII scalar | 708 ±4 | [Corrected scalar](elasticsearch-async-json-task4/followup-scalar-final/jmh.json) |
| UTF-8 bytes | 1 MiB ASCII scalar | 1,539 ±15 | [Corrected scalar](elasticsearch-async-json-task4/followup-scalar-final/jmh.json) |
| String | 10,000 integers | 7,496 ±31 | [Corrected numeric tokens](elasticsearch-async-json-task4/followup-numeric-token-final/jmh.json) |
| UTF-8 bytes | 10,000 integers | 5,748 ±113 | [Corrected numeric tokens](elasticsearch-async-json-task4/followup-numeric-token-final/jmh.json) |

The scalar character diagnostic stays close to the complete Search character
score in its own campaign, corroborating upstream long-string completion work.
It does not measure a precise percentage of parser cost. UTF-8 token diagnostics
also exclude tree and strictness work; their rates cannot replace complete-tree
UTF-8 measurements or String-input comparisons.

## Current scope

Retain the character String convenience, strict Search-owned tree semantics,
combined eager numeric construction and bounded UTF-8 session API. Preserve the
benchmark alternatives as evidence. The accepted String-path choice does not
approve all performance regressions: numeric UTF-8 and scalar UTF-8 variability
remain open in the [masterplan](elasticsearch-async-json.md). Existing transports
remain Gson, and this reference adds no codec migration, transport integration,
worker policy or codec implementation. The new numeric rows come from the linked
focused campaigns; the historical parsing/read/encoding rows were not rerun.
