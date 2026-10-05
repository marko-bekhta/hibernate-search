# Task 4 follow-up: bounded encoding and parser tradeoffs

This continues the [Task 4 checkpoint](elasticsearch-async-json-task4.md) at
`ee3b0a5644`. It retains Jackson Core 3.2.2, Search-owned trees, strict validation,
immutable numbers and bounded caller/adapter storage. Production transports still
use Gson; Tasks 5 and 6 have not started.

**The measured Unicode output regression is resolved. Task 4's complete exit
condition remains open:** numeric-rich UTF-8 parsing remains slower than Gson,
the large owned-String convenience path still needs an architectural decision,
and scalar UTF-8 throughput varies materially between corrected runs.
A construction optimization and an explicitly rejected alternative are measured
below. No throughput threshold, validation rule, input limit or ownership rule
was weakened.

## Controls and interpretation

All primary comparisons use the committed runner, CPUs 0 and 2 (distinct physical
cores, excluding their SMT siblings), one JMH thread, three independent forks,
eight one-second warmups and five one-second measurements unless stated otherwise.
The current environment permits CPUs 0–23, has no cgroup CPU quota, and uses
OpenJDK 25.0.4.1 with production compilation targeting Java 17. Heap is fixed at
1 GiB, G1, `ActiveProcessorCount=2`. The runner records topology, cpuset/cgroup,
governor/turbo/frequency hints, pressure/load, in-fork and worker affinity, exact
commands, source patches/hashes and artifact hashes. No host controls were changed.
Pinning is not exclusive CPU reservation. Builds, tests and campaigns ran serially.

The first parser experiment runs inherited an existing ingress-harness lifecycle
bug: JMH generated two calls to an annotated override of an annotated inherited
`setup()`. Both completed verification, but the second overwrote an idle worker.
The correction inherits the parent annotation without annotating the override;
generated code now has exactly one setup call, and inherited teardown closes that
worker. Writer states are unaffected. Earlier parser raw runs remain experimental
corroboration with this limitation; corrected-harness primary controls below
establish the final parser verdict. The runner's `gating` field checks iteration,
fork and profiler controls, not the absence of every harness defect.

Correctness checks run outside timing. Writers consume emitted arrays opaquely,
match fresh/reused constructor and probe lifecycles, include NDJSON newlines and
complete delivery. Parser comparisons construct and observe complete trees;
token baselines force scalar completion. Profiles remain diagnostic. All raw
iteration samples and JMH 99.9% intervals are retained in the
[evidence index](elasticsearch-async-json-task4/README.md).

## Direct Unicode and escape output

`Utf8JsonWriteCursor` keeps its existing ASCII loop. A second heap-buffer loop
writes complete Unicode and escape units directly into caller storage using local
source and destination offsets. It saves offsets and stage on yield. At fewer
than six available bytes, with direct buffers, or outside content stages, the
existing scratch path handles the next unit. This conservative bulk bound avoids
partial direct stores; all available output capacity is still drained by the
fallback. No encoded string, name or body is allocated. Pending encoded state
remains six bytes, plus traversal/source references.

The local-offset technique was informed by
[Core 3.2.2 UTF8JsonGenerator](https://github.com/FasterXML/jackson-core/blob/jackson-core-3.2.2/src/main/java/tools/jackson/core/json/UTF8JsonGenerator.java).
There is no dependency on generator internals. Short escapes, HTML escapes,
U+2028/U+2029, supplementary pairs and lone-surrogate escaping retain the same
policy. Heap, sliced, direct, tiny, zero and read-only buffers, cancellation and
caller ownership are preserved.

Throughput is complete NDJSON operations/s:

| Lifecycle/run | Gson | Sync cursor | Incremental cursor | Direct Jackson |
| --- | ---: | ---: | ---: | ---: |
| Fresh before | 33,207 ±268 | 32,930 ±2,149 | 31,200 ±2,427 | 53,458 ±2,471 |
| Fresh after | 33,560 ±377 | 135,364 ±4,983 | 143,540 ±533 | 151,863 ±12,967 |
| Reused after | 49,705 ±374 | 301,226 ±2,158 | 297,698 ±3,472 | 271,226 ±7,781 |

The before-run sync fork means were 34,341/34,261/30,187, and incremental
34,029/29,402/30,170. Longer warmup alone did not remove the old clusters.
After-run sync means were 139,032/129,033/138,026; incremental
143,272/143,749/143,601. Every new fork exceeds the old clusters by a wide margin.
This resolves the measured >5% Unicode regression relative to Gson; it does not
promise an exact universal speedup or eliminate all fork variation.

Fresh Jackson also improves because its matched constructor/probe uses the new
cursor. The generator code itself did not change. The reused comparison excludes
that probe and shows the new cursor ahead of Jackson on this fixture. This
lifecycle distinction is essential when selecting a synchronous implementation.

The separate compilation diagnostic uses JMH `comp` and `PrintCompilation`.
It records the new loop's C2 compilation during warmup and tier transitions in
other cursor methods. It does **not** establish why the old forks split into
30k/34k clusters; those forks had no corresponding compilation trace. No cause
is assigned from coarse stack samples or compilation logs alone.

## Numeric construction versus conversion on demand

Validated numeric construction now accumulates an in-range integer while it
checks JSON integer grammar, rather than scanning grammar and then accumulating
again in the constructor. Overflow continues grammar validation; decimals and
exponents retain BigDecimal narrowing. Lexemes, `-0`, arbitrary exponent spelling,
exact equality/hash and explicit String-number coercion remain intact. Float and
double reads use the lexeme, preserving signed zero. The private cached-result
constructor is reached only after complete validation; no unsafe public trusted
factory was added. All fields remain final.

A separate experimental patch removed the eager cache and converted with
`Long.parseLong`, falling back to BigDecimal, at each read. It remained immutable
and retained strict grammar validation, but is **not** the final implementation.
Its exact source patch and artifact identity are retained with its runs.

The reusable numeric benchmark builds a complete 10,000-integer tree before
reading it. `none` observes the tree; `selected` reads every hundredth value once
(100 reads); `repeated` reads all values ten times (100,000 reads). Both trees are
consumed opaquely, and independent expected sums are checked in setup. UTF-8 rows
include decoder/feed, EOF, tree construction and close at 8192-byte fragments.

| Variant | Input/read pattern | Gson ops/s | Search ops/s | Search vs Gson |
| --- | --- | ---: | ---: | ---: |
| Before | String, none | 4,707 ±42 | 4,145 ±51 | -11.9% |
| Before | String, selected | 4,650 ±21 | 4,193 ±24 | -9.8% |
| Before | String, repeated | 1,324 ±3 | 2,664 ±20 | +101.2% |
| Before | UTF-8, none | 4,616 ±27 | 3,153 ±170 | -31.7% |
| Combined eager | String, none | 4,672 ±32 | 4,481 ±30 | -4.1% |
| Combined eager | String, selected | 4,652 ±27 | 4,469 ±16 | -3.9% |
| Combined eager | String, repeated | 1,304 ±13 | 2,789 ±16 | +113.9% |
| Combined eager | UTF-8, none | 4,546 ±237 | 3,565 ±124 | -21.6% |
| On demand | String, none | 4,658 ±81 | 4,950 ±34 | +6.3% |
| On demand | String, selected | 4,598 ±169 | 4,928 ±60 | +7.2% |
| On demand | String, repeated | 1,319 ±7 | 1,195 ±349 | -9.4%, uncertain |
| On demand | UTF-8, none | 4,594 ±52 | 3,824 ±89 | -16.8% |

Combining the passes improves Search String none/selected/repeated by
8.1%/6.6%/4.7% over the before campaign. UTF-8 improves 13.1% by aggregate
estimate, with fork variability retained. Combined String fork means are stable:
none 4,498/4,477/4,469; selected 4,473/4,455/4,479; repeated 2,789/2,793/2,786.
The String no/selected-read estimates fall below the investigation threshold in
this campaign; this is not proof of a universal 5% bound.

Conversion on demand improves construction a further ~10% for owned String and
~7% for UTF-8, but its repeated-read estimate is ~57% below combined eager, with
large fork variability. Its interval does not establish a repeated-read
regression relative to Gson; the loss relative to the eager cache is clear.
This integer-rich experiment also does not establish decimal/coercion throughput;
the on-demand exception probe introduces additional work for those conversions.

The final implementation retains combined eager construction. It preserves the
existing accessor improvement and repeated-read benefit while reducing validation
work. On demand does not resolve the numeric UTF-8 regression and would discard
that benefit. The cache tradeoff is real, but it does not explain all of the
remaining UTF-8 gap. Numeric UTF-8 remains an open gate item.

## Fully consumed upstream token baselines

`JsonScalarConsumptionBenchmark` is a committed, reusable upstream reproducer.
All methods start with the same valid root fixture. The character Core baseline
calls `getString()` for every property name and scalar and consumes those Strings
opaquely. Its UTF-8 method feeds 8192-byte slices, drains through NOT_AVAILABLE,
signals EOF and drains to completion. Setup checks complete scalar values through
both paths and checks the complete Search tree against the source fixture.

Core constraints match the Search defaults. The minimal baseline deliberately
omits Search's raw UTF-16/canonical UTF-8 validation, decoded name-byte checks,
single-root tree builder and ownership handoff clearing. The public Core async
parser also omits the module's escaped-name workaround; these fixture names are
ASCII. These are valid-input token/scalar diagnostics, not replacement parsers
with identical acceptance and ownership contracts. String and UTF-8 columns
represent different input work and must not be conflated.

| Fixture/input | Gson complete tree | Search complete tree | Core consumed tokens |
| --- | ---: | ---: | ---: |
| 1 MiB scalar, owned String | 1,275 ±75 | 686 ±16 | 704 ±3 |
| 1 MiB scalar, UTF-8 | measured separately below | measured separately below | 1,538 ±10 |
| 10,000 integers, owned String | 4,672 ±32 | 4,481 ±30 | 7,484 ±48 |
| 10,000 integers, UTF-8 | 4,546 ±237 | 3,565 ±124 | 5,669 ±178 |

The numeric full-tree rows are the separate combined-eager campaign, not a
simultaneously interleaved decomposition. Those minimal token baselines omit work
and observe a different result shape. They identify remaining upstream and model
work; their ratios are not exact CPU cost fractions.

The owned-String scalar reproduces the material regression even without the
Search tree builder: Core's 704 ops/s is close to Search's 686, and both are well
below Gson. Fully completing strings, rather than merely advancing lazy tokens,
is essential. This corroborates the original `_finishString2`, TextBuffer
assembly/coalescing and character-copy stack localization. It provides evidence
against treating the local tree builder or raw UTF-16 scan as the main cause of
this large-scalar String gap. The separate stack diagnostic remains coarse and
filtered, not a percentage cost decomposition.

**Architectural decision:** retain the bounded Core character convenience path
for already owned String input and retain the async UTF-8 path for network input.
Keep strict validation and materialized trees. The committed reproducer is the
basis for an upstream long-string investigation; introduce no local custom
String parser, full raw-body copy or mandatory offload to improve this score.
The known convenience-path cost remains explicit. Accepting that tradeoff for a
broad migration, or obtaining an upstream fix, is still a gate decision; this
report does not silently mark it resolved.

## Corrected-harness primary parser controls

The single-setup reruns corroborate the combined-eager decision. All use three
forks/eight warmups/five measurements, complete observation and the same pinning.

| Final input/read pattern | Gson ops/s | Search ops/s | Search vs Gson |
| --- | ---: | ---: | ---: |
| Numeric String, none | 4,685 ±39 | 4,465 ±32 | -4.7% |
| Numeric String, selected | 4,671 ±29 | 4,473 ±31 | -4.2% |
| Numeric String, repeated | 1,305 ±8 | 2,793 ±12 | +114.0% |
| Numeric UTF-8, 8192 | 4,616 ±99 | 3,621 ±105 | -21.6% |
| 1 MiB scalar, owned String | 1,293 ±5 | 692 ±4 | -46.5% |
| Unicode UTF-8, 8192 | 102,362 ±1,059 | 137,410 ±653 | +34.2% |
| 1 MiB scalar UTF-8, 8192 | 1,248 ±19 | 1,273 ±19 | +2.0% |

Corrected fully consumed Core baselines are 708 ±4 String scalar, 1,539 ±15 UTF-8
scalar, 7,496 ±31 String integers and 5,748 ±113 UTF-8 integers. The same
owned-String large-scalar bottleneck persists after fixing setup. The numeric
UTF-8 regression is present in every final Search fork (3,607/3,742/3,515),
versus Gson 4,669/4,689/4,492; setup duplication does not explain it.

The final separate eager allocation profile is 1,130,058 B/op versus 1,050,057
for the on-demand experiment: about 80 kB (78 KiB) per 10,000 nodes saved by removing
cache fields. Both still exceed the checkpoint's ~972 KiB Gson String result.
This is total allocation for tree construction, not retained encoded output.
The final cache has the same fields as before; merging scans saves work, not
fixed node storage. Object-layout attribution is an inference from these
normalized allocations, not a measured layout decomposition.

The separate final stack profiles again expose `_finishString2`/TextBuffer work
for owned String, and Core tokenization, session draining, `JsonNumber.jsonNumber`,
`fromParser`, UTF-8 validation and `getString`/TextBuffer work for integers.
Many samples are filtered. Visible percentages are not exact cost fractions,
and no single component is assigned the complete numeric regression.

## Fragmentation and bounded execution

The reusable ingress benchmark caps both InputStream reads and offered
ByteBuffer regions. Search retains its additional 8192-byte internal feed cap.
Every parameter verifies complete Search/Gson bytes before timing and parses
through EOF. The earlier matched campaign is retained as exploratory context;
its states had the duplicated setup limitation described above.

| Fixture | Maximum fragment | Gson ops/s | Search ops/s |
| --- | ---: | ---: | ---: |
| Unicode | 64 | 81,414 ±966 | 103,442 ±7,503 |
| Unicode | 1024 | 99,656 ±880 | 125,548 ±322 |
| Unicode | 8192 | 103,336 ±919 | 135,776 ±2,536 |
| 1 MiB scalar | 64 | 823 ±6 | 861 ±81 |
| 1 MiB scalar | 1024 | 1,194 ±18 | 1,185 ±69 |
| 1 MiB scalar | 8192 | 1,207 ±9 | 1,101 ±37 |

The corrected 8192 controls above recover the scalar estimate, so the earlier
8 KiB loss is not silently discarded or used to establish a resolved universal
bound. Different initial setup, compilation, time and environment can affect
estimates; these data do not identify a cause. Independent corrected
corroboration is retained separately: Search 1,114 ±5 versus Gson 1,224 ±28
(-9.0%), again with three forks/eight warmups. Search fork means are tightly
clustered in this slower run. Thus the optimistic corrected +2.0% result does
not resolve scalar UTF-8 variability. This remains an open gate item, alongside
numeric UTF-8 and large owned-String parsing. The final source/artifact identities
match across these two corrected runs. A separate compilation diagnostic is
retained; it is not primary timing or proof of the cause.

The corrected tiny-feed smoke checks 1/2/3/5/7-byte fragments through complete
UTF-8 values and JSON escapes, with complete result validation for both parsers.
It is a one-fork correctness/harness smoke, not throughput gating. Existing parser
split-point tests cover escaped names/surrogates and invalid boundary sequences.

These are in-memory completion/throughput experiments. They model neither
network arrival timing nor event-loop p99 latency. Core can still do substantial
work when a large token completes. Retain bounded slices and allow scheduling
between them; any offload/fairness policy belongs to a future transport adapter.

## Synchronous generator option and scope

Retain the internal reflection-free synchronous Jackson generator and bounded
worker experiment. A transport permitting synchronous streaming can evaluate
that generator without whole-body copies. The old small/mapping/nested results
favor it in several cases, while the new reused Unicode result favors the cursor.
Choose according to complete transport/lifecycle measurements, not a blanket
implementation preference. A worker remains optional adapter policy; its tiny-body
hop/coordination cost in the checkpoint is substantial. This work switches no
production transport, execution policy, default SPI implementation or dependency.

Final opaque ASCII controls preserve cursor advantages: small Gson
1,122,115 ±13,034 versus cursor 1,661,600 ±17,126 (+48.1%), and 1 MiB ASCII
Gson 476.5 ±4.6 versus cursor 2,398.1 ±10.4 (+403.3%). These controls investigate
the unchanged fast path without repeating an unrelated full writer matrix.


## Verification and remaining gate

Final production sources passed 102 JSON model/accessor/parser/output/API-boundary,
cleanup and retention tests with no failures, errors or skips. The initial clean
scoped reactor also passed eight backend framing cases. Changed modules passed
compilation targeting Java 17 with `-Werror`, forbidden APIs, Checkstyle,
publication/dependency enforcement and packaging/Moditect checks on JDK 25.
The corrected benchmark package passed Checkstyle, generated-source lifecycle
inspection and all setup content assertions in primary and tiny-feed runs.
The final full-byte/slow-consumer harness passed capacities 1/2/5/8192,
cancellation, ownership, surrogate-policy, worker failure/termination and six-byte
cursor/two-array worker retention assertions. Malformed additional JMH parameters
are rejected before creating an output directory.

The scalar UTF-8 compilation diagnostic records C2 compilation of
`Utf8Validator.accept`, session draining and TextBuffer completion during warmup.
Its variable throughput is diagnostic, not a primary control. The trace does not
establish why identical-source scalar campaigns differ. Exact commands, full
profile/compilation text, raw JMH JSON, fork means, source patches and artifact
identity are retained in the evidence index; no binary artifacts are committed.

**Task 4 remains incomplete.** Measured Unicode output recovered, and the unchanged
ASCII path still substantially exceeds Gson. Combined numeric construction puts
owned-String no/selected-read estimates near the 5% investigation threshold and
preserves the eager repeated-read/accessor benefit. Numeric-rich UTF-8 still has a
material ~22% regression. Large owned-String completion reproduces an upstream
Core bottleneck. Scalar UTF-8 has contradictory corrected three-fork results,
including a material slower cluster. These require further resolution or an
explicitly accepted architectural tradeoff before broad migration. Keep Jackson 3
Core, bounded streaming, strict limits, lexical semantics and immutable numbers;
Jackson 2 fallback remains future documentation only. Tasks 5 and 6 are untouched.

No live HTTP/backend server, actual signing interceptor, saturation/event-loop
latency distribution, full reactor, Java 17 runtime, native image or replacement
Task 5 codec comparison was exercised. Direct-buffer output is correctness-tested;
its throughput is not claimed. Pinning does not prove exclusive CPU reservation,
and these results do not promise universal throughput bounds.
