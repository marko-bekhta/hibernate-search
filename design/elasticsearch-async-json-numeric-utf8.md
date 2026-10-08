# Numeric-rich UTF-8 parsing follow-up

**Subsequent decision, 2026-10-08:** the user accepted the documented remaining
performance tradeoffs provisionally for migration. Task 4's investigation gate
is complete by that decision, not by eliminating the gaps below. This report
retains its original experimental conclusions. See the [masterplan](elasticsearch-async-json.md)
and the [internal-route audit](elasticsearch-json-internal-path-audit.md) for the
current migration requirements.

The retained change adds heap-array byte access to the existing Jackson 3.2.2
ByteBuffer parser subclass. On the matched 10,000-integer workload, Search improves
from **3,624 ±39 to 4,077 ±110 operations/s (+12.5%)**. Complete Search parsing
still trails Gson's **4,696 ±17 by 13.2%**. Numeric UTF-8 remains an open gate;
this investigation does not complete Task 4 or accept the remaining regression.

The character parser for already supplied Strings, immutable numeric caches,
strict number grammar, canonical UTF-8 validation, limits, lexical representations,
output cursor and live Gson transports retain their existing implementation.
Tasks 5 and 6 have not started. See the [parsing reference](elasticsearch-json-parsing-reference.md)
and [masterplan](elasticsearch-async-json.md).

## Retained implementation

Core's `NonBlockingByteBufferJsonParser` implements its three protected byte-reading
hooks with absolute `ByteBuffer.get`; its async byte-array sibling indexes an
array. The shared tokenizer checks its input boundaries and constraints in both
implementations. The existing Search subclass now caches an accessible heap
array and its slice offset at `feedInput`, and uses those hooks to index the same
bounded input region. Direct and read-only buffers use Core's original getters.
There is no new lexer, numeric factory, encoded copy, scratch array or worker.

The session still validates canonical UTF-8 first, caps every feed, drains through
`NOT_AVAILABLE`, advances caller position and feeds the empty handoff buffer.
That handoff clears both Core's buffer and the added array reference before return.
Mixed buffer kinds remain supported within one session. The escaped supplementary
property-name workaround is unchanged. The hooks deliberately depend on the
pinned Core implementation; changing Core requires the existing split/constraint
checks and new mixed-buffer ownership checks.

Inspection of source and bytecode found no second number wrapper or grammar pass
in `JsonPrimitive`: validated `JsonNumber` instances are retained directly.
`getString()` is called once per numeric token. Strict grammar and checked integral
accumulation already share one pass. No redundant per-number scan was removed here.

## Controls and complete-tree results

Primary timings use the committed runner, CPUs **0 and 2**, distinct physical
cores excluding their SMT siblings, verified in forks/workers. They use three
forks, eight one-second warmups, five one-second measurements, one JMH thread,
fixed 1 GiB heap, G1, `ActiveProcessorCount=2`, OpenJDK 25.0.4.1 and production
compilation targeting Java 17. Builds, tests and timings ran serially. No host
settings were changed. Pinning does not reserve CPUs exclusively. Raw environment,
commands, source patches/hashes, artifact hashes, all samples and fork means are
in the [evidence index](elasticsearch-async-json-numeric-utf8/README.md).

All full-tree rows include construction, input handling, validation, feeds capped
at 8192 bytes, explicit EOF, tree construction and close. JMH observes the entire
tree opaquely. Setup verifies complete serialized content/lexemes independently
against the fixture for both parsers. Gson includes reader/decoder work; valid-input
comparisons do not imply identical acceptance policies.

| Campaign / input | Gson ops/s | Search ops/s | Search versus Gson |
| --- | ---: | ---: | ---: |
| Initial original-code heap integers | 4,655 ±19 | 3,595 ±51 | -22.8% |
| Retained heap access, integers | 4,696 ±17 | 4,077 ±110 | -13.2% |
| Retained heap access, mixed numbers | 3,176 ±88 | 3,123 ±20 | -1.7%; intervals overlap |
| Retained heap access, Unicode guard | 108,621 ±5,982 | 155,809 ±9,138 | +43.4% |

Integers are the existing 48,902-byte root with `values` containing 0–9999.
`numbers-mixed-10000` is an 82,512-byte root cycling `-0`, `-42`, `0.125`, `1.00`,
`6.022e23`, `-7E-9`, `9223372036854775808` and `1e2147483648`. This mixture checks
negative, decimal, exponent, overflow and huge-exponent construction; it is not
an estimate of their frequency in production. The Unicode guard is the earlier
3,851-byte fixture, not the 1 MiB scalar or large String experiment.

After adding `bufferKind=heap,direct,readonly` to the ingress harness, a matched
original-code control established **3,624 ±39** for heap and **3,844 ±156** for
direct. Its production code differs from the retained change only in the heap-access hooks;
its full-tree ingress harness matches the retained primary. Thus the +12.5% improvement uses this
control, rather than the initial older harness. The retained heap fork means are
3,942/4,177/4,112, all above original 3,613/3,592/3,668. Gson and mixed-number
fork variation is retained in the raw data.

The isolated direct after-control is **3,739 ±199 (-2.7%)**, with overlapping
intervals. Original direct forks are 3,970/3,648/3,914 and retained forks
3,620/3,607/3,992. Both versions contain faster and slower forks. These controls
do not establish a new >5% direct regression or a universal non-regression bound;
they do not identify the source of the variation. A combined-candidate read-only
control was 3,634 ±62 versus original 3,565 ±37; typed frames were also present
in that after-control, so it is not an isolated heap-access estimate.

## Upstream and allocation decomposition

The reusable `JsonNumericUtf8DiagnosticBenchmark` uses identical known valid
numeric fixtures, explicit EOF and complete scalar `getString()` consumption.
It compares public async ByteBuffer, public async byte-array, and standard
synchronous UTF-8 Core parsing. It omits Search's canonical validator, ownership
handoff, escaped-name workaround and general single-root builder. Its optional
`tree` builds the known fixture shape directly. These are diagnostics, **not**
strictness-equivalent competitors or additive CPU-cost measurements. Sync UTF-8
is a localization control, not a proposed blocking network path.

Three-fork/eight-warmup token controls use the same final artifact:

| Core UTF-8 consumed-token path | Ops/s |
| --- | ---: |
| Async ByteBuffer | 5,873 ±139 |
| Async byte-array | 6,702 ±59 |
| Standard synchronous bytes | 7,137 ±65 |

The byte-array estimate is 14.1% above ByteBuffer; synchronous is 6.5% above
byte-array. Together with the isolated complete-tree improvement, this supports
heap byte access as a real opportunity. It does not establish an unavoidable
upstream floor or assign the whole remaining tree gap to Core. The initial
one-fork decomposition also measures validated-number and simple-tree work;
its number-mode setup verified token lexemes, while the final harness additionally
constructs/validates those numbers during setup. Final profiles use the corrected
harness. Core diagnostics omit `-0` from their mixed fixture because Core normalizes
it; the independent complete-tree mixed fixture explicitly includes it.

Separate one-fork GC profiles (five warmups/three measurements) show allocation,
not retained heap or primary throughput:

| Complete UTF-8 tree / fixture | Gson B/op | Search B/op |
| --- | ---: | ---: |
| Integers | 985,666 | 1,130,610 |
| Mixed numbers | 1,019,475 | 1,100,891 |

| Async diagnostic / integers | Byte-array B/op | ByteBuffer B/op |
| --- | ---: | ---: |
| Completed lexeme tokens | 480,673 | 481,401 |
| Tokens plus validated cached numbers, each observed | 800,673 | 801,066 |
| Tokens plus known-shape Search tree | 1,129,857 | 1,130,250 |

The byte-array number mode adds exactly about 320 kB for 10,000 `JsonNumber`
instances; tree mode adds about 329 kB for primitives and array growth. Building
a Search tree from already owned lexemes allocates **649,185 B/op** for both
integer and mixed fixtures, excluding input/token/String creation. These measured
allocation deltas are consistent with the model's wrappers and backing arrays;
they are not a formal object-layout or retained-memory measurement.

The complete Search allocation is close to the diagnostic tree's allocation.
Canonical validation, ownership and general construction bookkeeping therefore
are not a large extra allocation source on this fixture. They can still cost CPU.
The heap shortcut changes byte-reading work, not per-number storage. The earlier
[on-demand cache tradeoff](elasticsearch-async-json-task4-followup.md) remains
rejected: its allocation saving did not eliminate the UTF-8 gap and lost repeated
long-read throughput. This investigation changes no numeric representation or
getter, and does not rerun or generalize those historical read-pattern scores.

## Profiling and rejected alternatives

A separate JMH JFR `profile` recording with asynchronous non-safepoint samples
captures 482 execution/native samples, 479 from the benchmark worker, no empty or
truncated selected stacks. Its heap-access production sources match the retained
implementation; its earlier ingress harness predates the optional buffer-kind
branch. The committed [text stacks](elasticsearch-async-json-numeric-utf8/heap-access-jfr/stacks.txt),
recording summary/hash and extraction commands reproduce the analysis without
committing a binary recording. The reusable `summarize-json-jfr.sh` reports both
top and inclusive counts and complete unique stacks.

Visible inclusive counts include Core tokenization 141, numeric grammar/cache
construction 85, `getString` 87, tree insertion 102 and canonical validation 23.
Counts overlap and reflect sampled/inlined attribution, not exact CPU fractions.
TextBuffer/String compression, primitive allocation and ArrayList insertion also
appear. The evidence supports several contributors; it does not assign the
complete residual regression to the cache, validator, builder or tokenizer.

Two additional candidates were removed:

- Typed object/array frame references avoided repeated container type checks.
  A one-fork combined probe reached 4,190, but isolated three-fork heap throughput
  was 3,670 ±34 versus matched original 3,624 ±39 (+1.3%, intervals overlap).
  Direct/read-only controls did not establish a consistent advantage. Keep the
  existing simpler builder.
- Bulk ASCII-run skipping in the canonical validator reached 4,186 in a short
  combined probe versus 4,190 without it. It did not show a useful numeric gain.
  Keep the existing validator and all its strict checks.

The combined primary reached 3,993 ±72 for heap integers and its direct control
was 3,654 ±41. Those are experimental comparisons, not retained-code results.
The isolated heap-access primary and matched original controls determine the
retained choice. Optimistic one-fork probes are not substituted for primary data.

## Verification and next step

The retained heap-only sources passed **105 JSON tests** with no failures/errors/
skips, including all split points and heap/direct/sliced/read-only cases. New cases
cover long-ASCII prefixes before noncanonical UTF-8 and alternating buffer kinds
with caller storage poisoned after each feed. The latter verifies clearing both
Core's buffer and the cached array while escaped supplementary names, `-0`,
overflow, decimal/exponent spelling and huge exponents cross feeds. The existing
backend framing suite passed eight cases. Tiny mixed-number 1/3/7-byte feed smoke
passed complete content checks; its throughput is not gating evidence.

Clean JSON module install passed Java 17 compilation, `-Werror`, forbidden APIs,
Checkstyle, dependency/publication enforcement and modular packaging on JDK 25.
The initial clean scoped benchmark reactor and final clean benchmark package
passed; generated JMH states call setup once. The final artifact is identical
across heap/direct primary, token controls, allocation and Unicode guard runs.
Exact commands and logs are preserved. No live backend/server, transport switch,
full reactor, Java 17 runtime, native image or network/event-loop latency campaign
was exercised. Scalar UTF-8 variability was not reopened.

**Task 4 remains incomplete.** The supported heap shortcut reduces the integer
regression, and the mixed numeric guard stays near Gson, but complete heap
integer parsing still loses 13.2%. Next, use the committed public-Core feeder
reproducer for upstream ByteBuffer/async number-token optimization and investigate
the remaining full-tree cost with the same complete-tree controls. Preserve the
strict scans and immutable eager cache; no trusted-token factory or new local
parser architecture is justified by these results. Broader migration still needs
resolution or an explicitly accepted tradeoff, and separate scalar UTF-8 work.
