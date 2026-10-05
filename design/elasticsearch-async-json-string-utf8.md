# Task 4 experiment: owned String through bounded UTF-8

This focused experiment continues the [Task 4 follow-up](elasticsearch-async-json-task4-followup.md)
at `d84fa56c1b`. Jackson Core remains 3.2.2, the tree is Search-owned, and existing
production transports remain on Gson. Tasks 5 and 6 have not started.

**Recommendation: retain the current character convenience parser.** Fixed
array-backed staging substantially improves the standard String-backed encoder
on the large ASCII fixture, but the complete encoded async operation regresses
on small and Unicode-rich Strings. An input-length threshold is not justified:
the long single ASCII token and the similarly large many-short-fields document
exercise different costs. Both encoding helpers remain benchmark experiments;
no production parser, validation policy or limit is changed.

## Compared work

`JsonStringUtf8Benchmark` starts every complete-tree operation with the same
already materialized String. Gson parses a complete Gson tree; Search's current
character path parses a complete Search tree; the original candidate wraps the
String in a CharBuffer and incrementally encodes UTF-8 into an 8192-byte buffer;
the focused optimization stages at most 4096 UTF-16 units through `String.getChars`
and uses the same standard UTF-8 encoder and async session. All trees are consumed
opaquely by Blackhole. Session/encoder/buffer construction, all encode and feed
work, explicit encoder completion/flush, parser EOF, tree construction and close
are inside timing. Factory and fixture construction are outside timing for all
implementations. There is no mandatory thread hop.

No competitor pre-encodes input during setup, calls whole-body `String.getBytes`
or `toCharArray`, or creates another full input String. `getBytes` appears only
in the standalone correctness checks for independently counting the short test
inputs. Encoding-only diagnostics observe emitted byte arrays opaquely and
return the complete byte count; they omit parsing/tree work and are not same-work
competitors or exact additive cost decompositions. No prebuilt-byte lower-bound
score is mixed into the String comparison.

Setup independently constructs all expected object members and decoded values,
checks complete Search equality and diagnostic serialization, and checks every
Gson member/value recursively. It also separately checks exact `-0`, decimal
trailing zeros/exponent spelling, integers beyond binary64 precision, huge
exponents, escaped supplementary property names and escaped lone-surrogate JSON
semantics. These are valid-input comparisons; Gson's acceptance policy differs
from Search's strict contract.

| Fixture | Input UTF-16 units | UTF-8 bytes | Shape |
| --- | ---: | ---: | --- |
| `scalar-1m` | 1,048,587 | 1,048,587 | One 1 MiB ASCII value |
| `unicode-1m` | 1,000,011 | 1,500,011 | One value repeating é, 漢, 😀, newline, quote and backslash; JSON escapes included |
| `small` | 44 | 44 | Typical nested term query and size |
| `fields-1m` | 1,140,891 | 1,140,891 | 18,000 short names and 48-character values |

## Controls and primary results

All complete-tree competitors use the same benchmark artifact and the committed
runner: CPUs 0 and 2 on distinct reported physical cores, one JMH thread, three
independent forks, eight one-second warmups and five one-second measurements.
The environment permits CPUs 0–23 and has no cgroup CPU quota. OpenJDK 25.0.4.1,
Java 17 production compilation, fixed 1 GiB heap, G1 and `ActiveProcessorCount=2`
match the previous checkpoint. The runner records exact commands, source patches
and hashes, artifact identity, topology/cpuset/cgroup, governor/turbo/frequency
hints, load/pressure and in-fork affinity. Pinning is not exclusive reservation;
no host settings changed. No builds or tests ran during timed campaigns.

The standalone benchmark state has one annotated setup with no inherited setup
or worker. Generated JMH code invokes it exactly once in each harness.
The [evidence index](elasticsearch-async-json-string-utf8/README.md) retains all
raw samples, commands, environments, source patches, console output and hashes.

| Fixture | Gson | Search character | Wrapped encoding + async | Staged encoding + async | Staged vs character |
| --- | ---: | ---: | ---: | ---: | ---: |
| `scalar-1m` | 1,276.6 ±71.5 | 513.7 ±3.8 | 298.9 ±1.3 | 1,216.3 ±23.6 | +136.8% |
| `unicode-1m` | 292.2 ±7.8 | 643.3 ±5.1 | 181.9 ±6.5 | 218.1 ±7.5 | -66.1% |
| `small` | 3,187,916 ±53,778 | 3,270,509 ±49,063 | 1,208,721 ±6,018 | 812,156 ±10,904 | -75.2% |
| `fields-1m` | 292.5 ±4.9 | 348.4 ±3.1 | 197.5 ±2.4 | 384.9 ±3.3 | +10.5% |

These are complete operations/s with JMH 99.9% intervals. Gson ASCII fork means
are 1,230/1,368/1,232, versus staged Search
1,205/1,199/1,245; their aggregate intervals overlap. The staged estimate is
4.7% below Gson, but this does not establish a universal 5% bound. Every staged
Unicode fork is below every current-character fork (209/222/223 versus
645/639/646); small staged forks are 807k/815k/815k versus character
3.333m/3.234m/3.245m. Those regressions determine the production decision without
requiring an additional interleaved campaign to adjudicate ASCII equality.

The current-character ASCII estimate here is 514 ops/s, compared with 692 in the
previous scalar-consumption harness on unchanged production source. This
standalone state verifies both candidates and has a different setup lifecycle.
The matched comparisons above use one artifact and lifecycle; absolute results
across harnesses/campaigns are not conflated. No cause is assigned to that
cross-harness difference without direct evidence.

The earlier Task 4 scalar UTF-8 campaigns varied from +2% to -9% relative to
Gson on an unchanged artifact. The current experiment starts with String and
includes encoding, so those byte-input scores are contextual evidence rather
than direct competitors. This report does not resolve that separate variability
or the remaining ~22% numeric-rich UTF-8 parsing gap.

## Measured character staging

The first controlled smoke used the historical `CharBuffer.wrap(String)` loop,
with complete final encode and explicit flush. ASCII encoding alone reached only
438 ops/s, while complete encoded async parsing reached 324 ops/s. That justified
one focused optimization: expose a fixed array-backed CharBuffer to the standard
encoder by copying bounded String windows. The follow-up smoke measured ASCII
encoding alone at 22,738 ops/s, versus 386 ops/s with wrapping on the same artifact.
Complete staged ASCII parsing reached 1,192 ops/s. Both smokes are one-fork,
one-warmup/one-measurement non-gating diagnostics.

The optimization still uses the JDK standard encoder with malformed/unmappable
input reporting. `compact()` retains an unconsumed high surrogate at a staging
boundary, and the next `getChars` window appends its low surrogate. End-of-input
is true only when all original characters have been offered; overflow is drained
before retry, then flush is completed. The async session keeps canonical UTF-8
validation, bounded feed ownership, original UTF-8 byte limits, decoded name
limits, token/depth constraints, strict single-root EOF and exact numeric lexemes.
There is no custom UTF-8 encoder or omitted validation to gain throughput.

The separate one-fork GC profile uses five one-second warmups and three
one-second measurements on the same artifact/resources. Its encoding-only
diagnostic throughput is:

| Fixture | Wrapped encoding only ops/s | Staged encoding only ops/s |
| --- | ---: | ---: |
| `scalar-1m` | 447 | 23,864 |
| `unicode-1m` | 475 | 942 |
| `small` | 1,888,496 | 1,113,556 |
| `fields-1m` | 404 | 21,776 |

Wrapped encoding allocation is 8,416–8,455 B/op; staged encoding allocation
is 16,416–16,655 B/op, across these input sizes. Unicode encoding-only timing
has a wide wrapped interval in this short profile; no statistically precise
Unicode encoding speedup is claimed. ASCII staging improvement is corroborated
by both controlled smokes and this separate diagnostic.

Complete-tree normalized allocation from that separate profile is:

| Fixture | Gson B/op | Search character B/op | Wrapped + async B/op | Staged + async B/op |
| --- | ---: | ---: | ---: | ---: |
| `scalar-1m` | 5,211,221 | 4,064,875 | 4,211,758 | 4,227,118 |
| `unicode-1m` | 6,121,953 | 4,812,195 | 4,831,147 | 4,842,761 |
| `small` | 3,640 | 1,688 | 10,376 | 18,432 |
| `fields-1m` | 3,906,568 | 4,532,954 | 6,494,179 | 6,510,144 |

Staged small parsing allocates about 10.9 times the current character path.
Many-fields staging improves primary throughput 10.5% but allocates 43.6% more
than character parsing. These tradeoffs reinforce retaining the current default.

These allocation scores count the complete operation, including decoded Strings,
Jackson token storage and the tree, rather than retained encoded-body storage.
Allocation profiles run separately from primary throughput. Their throughput is
not substituted for the primary estimates.

## Bounded storage and verification

The wrapped candidate allocates one 8192-byte output buffer and a CharBuffer view
of the original String. The staged candidate adds one fixed 4096-char buffer
(8192 bytes of UTF-16 payload). Those scratch capacities are independent of input
length. Neither candidate accumulates encoded input. The original String remains
materialized and owned by the caller; decoded names/values, parser token state
and the completed tree still scale with input. This is not a constant-memory
claim for complete parsing.

`JsonStringUtf8Verification` is a reusable standalone check executable from the
benchmark JAR. It checks 465 scenarios with byte capacities 4/5/7/16/8192 and
char capacities 2/3/4096, moving multibyte characters/pairs over boundaries, splitting
JSON escapes and property-name surrogate escapes, malformed raw high/low
surrogates including staging boundaries, trailing/empty/truncated documents,
exact/below UTF-8 byte limits, one-byte parser feeds and depth/string/name/number
constraints. The minimum encoder output capacity is four bytes so a supplementary
code point can progress. Feed instrumentation checks every offered buffer's fixed
capacity and total byte count; source inspection confirms bounded `getChars`
windows. Failure paths close the session, and production cleanup tests run in the
focused module suite. Exact tree and lexeme checks cover both helpers.

The focused JSON module `clean verify` passed all 102 tests, including existing
cleanup and fragmentation tests, plus Checkstyle, forbidden APIs and packaging.
The clean reactor benchmark build and final module benchmark package passed
style, forbidden APIs and JMH generation. The standalone check passed all 465
scenarios. Generated harness inspection confirms exactly one `val.setup()` in
each of the six methods. See the linked verification/build logs in the evidence
index. No transport integration tests were needed for these experiment-only
changes; existing production transport code is untouched.

## Reproduction

```shell
./mvnw clean install -pl integrationtest/performance/backend/elasticsearch -am \
  -DskipTests -DskipITs -Dno-build-cache
java -cp integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  org.hibernate.search.integrationtest.performance.backend.elasticsearch.JsonStringUtf8Verification
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-string-primary --cores 0,2 --forks 3 --warmup 8 --iterations 5 \
  --fixtures scalar-1m,unicode-1m,small,fields-1m \
  --pattern 'JsonStringUtf8Benchmark.(gsonString|search.*String)$'
```

Use `JsonStringUtf8Benchmark.diagnostic.*` for encoding-only diagnostics and a
separate `--profiler gc` campaign for allocation. Keep them outside the primary
comparison. There is no production replacement or automatic dispatch in this
commit. Task 4 remains open for the convenience-path architectural tradeoff,
numeric UTF-8 regression and byte-input scalar variability.
