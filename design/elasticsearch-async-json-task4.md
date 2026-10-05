# Task 4 checkpoint: reproducible JSON comparisons

Current follow-up: [bounded encoding and parser tradeoffs](elasticsearch-async-json-task4-followup.md).
The original tables below describe checkpoint `ee3b0a5644`; the follow-up resolves
measured Unicode output, improves numeric construction, fixes inherited parser
benchmark setup duplication, and records the remaining parser gates.

This checkpoint adds reusable server-independent performance infrastructure and
measured, bounded optimizations to the Search cursor. Task 5 codecs/backend
migration and Task 6 transport integration have not started.

**The migration gate is incomplete:** the 1 MiB String convenience parser remains
about 46% slower than Gson in the matched three-fork run and controlled
interleaved reruns. Numeric-rich parsing also regresses, and Unicode output has
unresolved fork variability. This is an honest performance checkpoint, not permission to
start broad migration.

## Reproduction and controls

The [performance README](../integrationtest/performance/backend/README.md#server-independent-json-comparisons)
documents build/run commands, selectors, fixture parameters and profiling.
[`run-json-benchmarks.sh`](../integrationtest/performance/backend/scripts/run-json-benchmarks.sh)
uses Bash and `jq`
for argument validation, topology/cgroup inspection, affinity, exact commands,
source/artifact hashes, environment capture and raw results. It is informed by
[the pinned Hibernate Validator runner](https://raw.githubusercontent.com/hibernate/hibernate-validator/79761614611bd6878bca0cbef2771e7e46bb765f/performance/scripts/run-benchmarks.sh).
No downloaded executable, sudo, sysctl, governor, turbo or scheduler mutation is
used. Portable JMH stack sampling replaces privileged native profiling; its
limitations are explicit below.

All primary comparisons use CPU affinity `0,2`, one JMH thread, three independent
forks, five one-second warmup and five one-second measurement iterations, fixed
1 GiB heap, G1 and `-XX:ActiveProcessorCount=2`. Java is Red Hat OpenJDK 25.0.4.1;
main sources target Java 17. The machine is an Intel i9-12900KS hybrid CPU.
Selected CPUs have distinct physical-core IDs, SMT siblings `0-1` and `2-3`,
3.4 GHz reported base and 5.2 GHz maximum frequency, and the performance governor.
These SMT-capable resources are consistent with performance cores; sysfs
`core_type` is unavailable, so classification uses recorded topology/frequency
hints rather than a claimed CPUID check. The siblings are excluded from the
selected set. All online/allowed CPUs, including the different single-thread
frequency class, are recorded. One NUMA node is reported. CPU quota is unlimited;
reported cgroup memory maximum is 74,999,996,416 bytes.

The same two CPUs are available to every writer; worker runs need a producer and
consumer. This is affinity, not exclusive reservation. Sibling activity, host
load and dynamic frequency/turbo can still affect results. Before/after load,
CPU pressure and cgroup CPU statistics, readable governor/turbo and exact JVM
settings are saved in each environment JSON. Do not compare these numbers with
the earlier unpinned Task 1 baseline or claim a result for other hardware/JVMs.

## Measurement correctness and lifecycles

[`JsonBenchmarkFixtures`](../integrationtest/performance/backend/elasticsearch/src/main/java/org/hibernate/search/integrationtest/performance/backend/elasticsearch/JsonBenchmarkFixtures.java)
now supplies the original baseline and candidate comparisons. Setup constructs
both trees and complete expected bytes outside timing. Every output comparison
independently validates exact complete bytes through Gson, Jackson 3, synchronous
cursor, incremental cursor and the bounded worker before measuring. All parts
use identical NDJSON framing with the final newline. The original Unicode
fixture includes valid supplementary characters and escaped controls. Lone
surrogates are asserted separately because legacy Gson's UTF-8 writer replaces
with `?`, whereas new output preserves a Unicode escape. No byte-equivalence
claim includes that intentional policy exception.

The primary `fresh` lifecycle includes construction and preliminary serialization
for every method. Legacy Gson serializes and retains the whole first part; the
candidate serializes at most 8 KiB and discards the probe. Jackson and worker
comparisons use the same candidate cursor probe, even though they subsequently
serialize through Jackson. The separate `reused` lifecycle excludes construction
and probing for both compared implementations and drains a complete already
constructed body. A new cursor is still opened for each invocation. No candidate
is compared without its probe against a baseline including constructor work.

NDJSON fixture sizes are 45/165/3,852/347/1,048,588/8,388,620/3,257,780
bytes for small/mapping/Unicode/nested/1 MiB/8 MiB/bulk, respectively. Bulk
contains 20,000 parts (10,000 metadata/document pairs). Parser input is only the
first JSON part, with no NDJSON newline.

The original timed sink observes byte count and first/last bytes of each write,
and returns a result to JMH. These observations alone do not prove that every
interior store survives optimization. A separate reusable
`JsonSinkSensitivityBenchmark` repeats all five complete operations with sampled
and opaque `Blackhole.consume(byte[])` observation, without per-byte hashing.
Its final comparison is recorded below. It does not hash every byte or log inside timing. Output chunking
is implementation-specific; exact full-byte checking belongs to trial setup.
Incremental output reuses an 8 KiB caller destination, synchronous output allocates
its adapter destination, and the worker reuses its executor/queues across requests.
Each worker invocation waits for the complete drain and producer completion.
The benchmark-only worker adds no production dependency or mandatory thread hop.

## Before/after optimization

The initial pinned three-fork comparison confirmed material regressions:

| Fixture | Gson ops/s | Initial cursor ops/s | Initial cursor vs Gson |
| --- | ---: | ---: | ---: |
| small | 1,086,786 | 516,959 | -52.4% |
| scalar-1m | 477.6 | 137.0 | -71.3% |
| bulk-10000 | 112.1 | 36.9 | -67.1% |

[Initial raw results](elasticsearch-async-json-task4/before/jmh.json),
[environment](elasticsearch-async-json-task4/before/environment.json) and
[command](elasticsearch-async-json-task4/before/command.txt) retain the baseline
and candidate in the same process/CPU configuration. A separate
[stack profile](elasticsearch-async-json-task4/profile-before/console.log)
attributed the scalar work to `Utf8JsonWriteCursor.write`, `prepareScalar` and
`prepare`. Stack sampling is coarse and partly filtered; it is supporting
localization evidence, not an exact CPU cost decomposition.

The cursor now copies contiguous safe ASCII directly into the caller's available
region, and retains offsets when that region fills. Heap/sliced destinations use
their backing array; direct destinations use their own `put` calls. Escapes,
non-ASCII and tiny buffers still follow the existing incremental state machine.
No token/body encoding is materialized. Pending encoded state remains at most
six bytes. The length probe uses a 1 KiB scratch buffer while keeping its 8 KiB
total budget; a synchronous adapter with a known small length sizes its scratch
to that length, at most 8 KiB. Unknown/large bodies still use an 8 KiB destination.
Signing streams keep their existing 8 KiB buffer. These are implementation choices,
with no API, dependency, escaping, framing, ownership or length-policy change.

The [single-fork optimized smoke](elasticsearch-async-json-task4/smoke-optimized/jmh.json)
was used only to decide whether to proceed to the full campaigns. It does not
establish the performance gate.

## Independent correctness, retention and slow consumers

[`JsonSlowConsumerHarness`](../integrationtest/performance/backend/elasticsearch/src/main/java/org/hibernate/search/integrationtest/performance/backend/elasticsearch/JsonSlowConsumerHarness.java)
asserts complete bytes at capacities 1/2/5/8192, zero capacity, six-byte cursor
retention and cancellation release. It separately asserts the worker's two
owned 8 KiB arrays, complete slow-consumer delivery, propagation of consumer
failure, cancellation and executor termination. The bounded queue circulates
those arrays; queued/in-flight bridge storage cannot exceed 16 KiB. Jackson's
own generator buffer, source trees, caller destinations and diagnostic full-body
capture are separate. The final harness also checks delivered array identities
(at most two arrays, each 8 KiB) and zero retained encoded capacity after normal
and cancelled close. Production and timed output never accumulate that capture.
The existing [`JsonBufferRetentionProbe`](../integrationtest/performance/backend/elasticsearch/src/main/java/org/hibernate/search/integrationtest/performance/backend/elasticsearch/JsonBufferRetentionProbe.java)
retains baseline inspection for comparison.

[Final correctness/slow-consumer output](elasticsearch-async-json-task4/final-correctness-and-slow-consumer.log)
records one diagnostic sample per fixture with a requested 100 microsecond pause
per worker chunk. Measured waits depend on the scheduler; these are completion
latencies, not a p99 distribution or event-loop latency guarantee. ThreadMXBean
records managed Java-thread CPU (excluding native GC/compiler threads), worker
serialization CPU, and worker busy wall time
(including producer blocking) and worker CPU divided by total request wall time.
The cold diagnostic samples include compilation/interference and should not be
used as primary throughput evidence. The occupied worker spends much of large
scalar slow-consumer time waiting for bounded storage, which is the intended
backpressure behavior.

## Final campaigns and gate

The final fresh-output campaign uses three independent forks and the controls
above. Values are operations per second, each operation delivering the complete
NDJSON body. Raw iteration samples, JMH 99.9% intervals and independent fork
means are retained; the threshold is an investigation trigger, not proof.

| Fixture | Gson | Jackson 3 | Sync cursor | Incremental cursor | Worker | Sync vs Gson |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| small | 1,131,539 | 2,394,884 | 1,759,079 | 1,807,401 | 213,214 | +55.5% |
| mapping | 627,792 | 1,251,069 | 849,754 | 856,915 | 190,634 | +35.4% |
| unicode | 32,743 | 54,488 | 32,802 | 33,214 | 43,028 | +0.2% |
| nested | 243,775 | 412,012 | 263,762 | 261,736 | 141,211 | +8.2% |
| scalar-1m | 467.1 | 2,205.5 | 2,444.3 | 2,437.5 | 1,799.6 | +423.3% |
| scalar-8m | 54.5 | 305.0 | 314.9 | 312.7 | 239.7 | +477.4% |
| bulk-10000 | 111.9 | 270.5 | 208.1 | 212.9 | 246.0 | +86.0% |

[Final raw writer matrix](elasticsearch-async-json-task4/final-writers/jmh.json),
[exact command](elasticsearch-async-json-task4/final-writers/command.txt) and
[environment/source identity](elasticsearch-async-json-task4/final-writers/environment.json)
include baseline and candidates under the same pinning. That sampled campaign estimated Unicode effectively
tied: Gson 32,743 ±722 and synchronous cursor 32,802 ±2,357 ops/s (JMH 99.9%
interval widths). No precise Unicode speedup is claimed. The earlier complete
[primary campaign](elasticsearch-async-json-task4/primary-fresh/jmh.json) had a
+2.5% cursor Unicode estimate; neither run demonstrates a repeatable >5% output
regression in those two sampled campaigns; the later opaque results below reopen
the Unicode decision. The direct generator is faster on several workloads, especially
Unicode. The worker is much slower for tiny requests, even with two available
physical cores; its hop/coordination overhead is a tradeoff, and it is not a
proposed mandatory execution policy.

The independent [reused-body comparison](elasticsearch-async-json-task4/primary-reused/jmh.json)
excluded both constructors/probes. Sync cursor/Gson ratios were 1.95× small,
1.35× Unicode, 1.66× nested, 1.35× 1 MiB scalar and 1.81× bulk. Its bulk cursor
fork means were 225.2/181.9/180.1 ops/s, illustrating interference/compilation
variation; do not treat an aggregate speedup as an exact prediction. Production
output algorithms were unchanged by the later parser/accessor optimizations.

### Allocation and slow consumers

Separate [three-fork GC profiling](elasticsearch-async-json-task4/allocation/jmh.json)
measured normalized allocation per complete fresh output operation:

| Fixture | Gson B/op | Sync cursor B/op | Incremental B/op | Jackson B/op | Worker B/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| small | 5,800 | 2,037 | 1,928 | 1,936 | 2,196 |
| unicode | 90,664 | 5,608 | 1,676 | 1,840 | 2,079 |
| scalar-1m | 3,250,138 | 9,939 | 1,675 | 1,757 | 9,200 |
| bulk-10000 | 41,608,618 | 5,624,101 | 5,615,834 | 5,615,797 | 5,638,284 |

These are diagnostic allocation measurements, not primary timing. Objects/frames
allocated while traversing many bulk parts still accumulate allocation even
though pending encoded storage is bounded. Allocation includes the benchmark
sink/bridge; it excludes fixture construction and retained source trees.

The [legacy retention probe](elasticsearch-async-json-task4/final-baseline-retention.log)
shows 8,388,620 pending bytes immediately after constructing the 8 MiB scalar
provider, using 8,389,632 bytes of page capacity. A zero-accepting sink leaves it
unchanged. The new cursor owns at most six encoded bytes; synchronous/caller
buffers are at most 8 KiB and the bridge owns 16 KiB across queued/in-flight
chunks. Those categories are accounted separately.

Final slow-consumer diagnostic samples for 1 MiB/8 MiB/bulk were
21.5/161.4/65.7 ms wall time, 3.0/17.1/16.7 ms managed Java-thread CPU, and
1.6/9.7/13.3 ms worker serialization CPU. Worker CPU/request wall ratios were
7.4%/6.0%/20.3%, with the producer occupying a thread while bounded storage was
unavailable. These single cold samples do not establish a latency distribution,
full process CPU cost, saturation limit or event-loop guarantee. The full
[harness log](elasticsearch-async-json-task4/final-correctness-and-slow-consumer.log)
contains every fixture and independent assertions.

### Accessors and parser investigation

The first matched accessor run showed strict/adapted regressions of about
21%/13%. The numeric representation now retains a validated lexeme and computes
an in-range integral value once with checked negative accumulation. Reads reuse
that value; decimal and out-of-range narrowing still uses BigDecimal. Float and
double reads still parse the original lexeme, preserving signed zero. Immutable
validated numeric wrappers can be shared within a primitive, eliminating a
second validation/wrapper. Validation uses a JSON grammar scan instead of a
regex matcher; it still rejects truncated exponents accepted by Core's character
parser at EOF. Independent BigDecimal expectations cover signed limits,
overflow, decimals/exponents, byte/short/int narrowing and String-number coercion.

This moves conversion work/storage to construction: numeric nodes have extra
fixed fields. The parser/allocation comparisons include that cost; a separate
numeric-rich fixture comparison is recorded below. It is not retained encoded
output and should not be presented as a free optimization.

[Final accessor timing](elasticsearch-async-json-task4/accessors-final/jmh.json)
uses three forks with eight one-second warmups and five measurements:

| Chain | Gson ops/s ± JMH error | Search ops/s ± JMH error | Search vs Gson |
| --- | ---: | ---: | ---: |
| strict plain object | 46,637,298 ±5,216,755 | 54,512,154 ±688,849 | +16.9% |
| explicitly adapted singleton arrays | 23,272,438 ±964,419 | 26,180,432 ±927,389 | +12.5% |

Search strict fork means were 53.69/54.97/54.87 million ops/s and adapted
25.03/26.82/26.69 million. Adaptation is requested at every intermediate and
terminal selected path; the expected result is independently checked against
legacy automatic adaptation. The separate
[GC profile](elasticsearch-async-json-task4/accessor-allocation-final/jmh.json)
with only three-second warmup showed adapted Search allocation of
48/336/48 B/op across forks (mean 144 ±242 B/op), with a slow allocating fork.
Do not claim universally zero/stable adapted-chain allocation or use diagnostic
profile timing as the primary throughput result. Strict allocation was 32 B/op
for both implementations in that profile.

The String convenience API now uses Core's character parser for already owned
materialized input, avoiding re-encoding UTF-8. It validates raw UTF-16, counts
original UTF-8 bytes when a cap is selected, and keeps decoded UTF-8 name limits,
string/number/depth constraints, root-null, trailing/truncation and surrogate
policies. It uses the same token-to-tree builder, no accessor navigation, no
reflection, and no secondary serialized whole-input String. The async UTF-8 SPI
is still separately measured below.

[Final String parser timing](elasticsearch-async-json-task4/parsers-final/jmh.json):

| Fixture | Gson ops/s ± JMH error | Search ops/s ± JMH error | Search vs Gson |
| --- | ---: | ---: | ---: |
| small | 3,160,476 ±56,180 | 3,352,189 ±62,282 | +6.1% |
| unicode | 187,333 ±4,471 | 312,664 ±12,229 | +66.9% |
| scalar-1m | 1,297.6 ±12.8 | 701.6 ±2.8 | -45.9% |

The first String run (re-encoding then async parsing) was slower across all three
fixtures. The optimized small/Unicode cases recover, but the large scalar does
not. Three additional independent interleaved Search/Gson fork pairs confirm the
large-scalar regression: 702.1/1318.4, 693.5/1188.5 and 695.0/1311.9 ops/s,
respectively (roughly 42–47% slower). These one-fork files are corroboration
across three independent pairs; no individual one-fork run establishes the gate.
Early metadata flags did not enforce fork/iteration counts; the runner now does. Per-fork/iteration samples remain in the
individual `scalar-interleaved-*` raw files. The second Gson fork is slower than
its others, yet even that pair exceeds the investigation threshold by a wide
margin. This is not an uncertainty-only result.

Separate [String parser allocation](elasticsearch-async-json-task4/read-allocation-final/jmh.json)
was 3,640/15,880/5,211,273 B/op for Gson and 1,688/6,360/4,064,871 B/op for Search
(small/Unicode/1 MiB). Lower allocation does not imply higher throughput. The
[stack profile](elasticsearch-async-json-task4/parser-stack-final/console.log)
localizes the large scalar work to `_finishString2`, char-array copying,
TextBuffer coalescing and `getString`, with raw UTF-16 validation also present.
Its visible runnable-stack percentages are coarse filtered samples, not exact
CPU fractions. Upstream long-string completion/copying remains an unresolved
bottleneck; no constraint, Unicode policy, buffering bound or completion check
was relaxed to improve these numbers.

### Opaque sink sensitivity and UTF-8 ingress

[Sink sensitivity](elasticsearch-async-json-task4/sink-sensitivity/jmh.json)
repeated all five writers over small/Unicode/1 MiB/bulk fixtures with the same
lifecycle/pinning in sampled and opaque modes (three forks, five warmups and
measurements). Most cursor relative results persist, but estimates change
materially in some cases: bulk sync/Gson is 2.08× sampled versus 1.83× opaque,
and incremental/Gson is 2.17× versus 1.83×. The sampled Gson bulk group was slower;
Jackson's scalar estimate also changes in the opposite direction from an expected
opaque-sink penalty. These data do not establish interior-store elimination as
the cause. They do show that boundary sampling is insufficient grounds for a
confident exact speedup claim.

Primary writer methods now always consume emitted arrays opaquely. A separate
[consistent opaque primary rerun](elasticsearch-async-json-task4/primary-opaque/jmh.json)
repeats all five methods on the four affected representative fixtures:

| Fixture | Gson | Jackson 3 | Sync cursor | Incremental cursor | Worker | Sync vs Gson |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| small | 1,158,348 | 2,446,382 | 1,757,110 | 1,802,098 | 213,350 | +51.7% |
| unicode | 32,198 | 53,316 | 29,915 | 32,001 | 41,280 | -7.1% |
| scalar-1m | 474.7 | 2,073.6 | 2,483.5 | 2,472.8 | 1,814.4 | +423.2% |
| bulk-10000 | 114.2 | 263.8 | 214.1 | 212.9 | 246.3 | +87.5% |

Large scalar and bulk gains persist with opaque consumption. Unicode requires
further investigation: the latest sync cursor is 29,915 ±437 versus Gson
32,198 ±2,084 ops/s (-7.1%). Cursor fork means are stable around 29.7–30.0k in
that campaign, while Gson has 33.7/33.4/29.5k. Three additional interleaved
cursor/Gson pairs are 29,887/33,723 (-11.4%), 33,994/33,742 (+0.7%) and
34,164/33,304 (+2.6%). Together with earlier +2.5%/+0.2% and sensitivity results,
this suggests distinct performance clusters rather than a trustworthy resolved
Unicode verdict. Pinning does not eliminate compilation/frequency/interference
variation. The [Unicode stack diagnostic](elasticsearch-async-json-task4/unicode-stack/console.log)
shows `prepareScalar` and `write` in visible runnable samples; half is filtered.
It cannot identify the cause of the clusters. The Unicode gate remains open;
no output-gate completion is claimed from the earlier sampled matrices.

The [UTF-8 ingress comparison](elasticsearch-async-json-task4/utf8-ingress/jmh.json)
starts with identical caller-owned bytes, including reader/decoder or bounded
feed/end-of-input, tree construction and close. Setup verifies complete content
against the original input. Returned trees are observed by JMH.

| Fixture | Gson ops/s ± JMH error | Search ops/s ± JMH error | Search vs Gson |
| --- | ---: | ---: | ---: |
| small | 1,145,901 ±10,174 | 3,451,064 ±112,057 | +201.2% |
| unicode | 105,049 ±1,764 | 142,174 ±2,417 | +35.3% |
| scalar-1m | 1,228.6 ±5.4 | 1,183.8 ±55.4 | -3.6% |

The scalar estimate is uncertain near the investigation threshold; this is no
proof of a universal ≤5% bound. It also shows why String convenience numbers
cannot be substituted for actual UTF-8 ingress. No network fragmentation,
transport, saturation or event-loop latency claim is made.

The numeric-rich fixture is a 48,902-byte single JSON root containing integers
0–9999 (48,903 NDJSON bytes). It measures construction costs shifted by the
integral cache. [Matched numeric-rich timing](elasticsearch-async-json-task4/numeric-parsers/jmh.json):

| Input | Gson ops/s ± JMH error | Search ops/s ± JMH error | Search vs Gson |
| --- | ---: | ---: | ---: |
| owned String | 4,730.1 ±38.9 | 4,195.2 ±29.9 | -11.3% |
| UTF-8 bytes | 4,660.3 ±47.1 | 3,323.9 ±43.0 | -28.7% |

Both numeric regressions are stable across all three primary forks. The
[numeric UTF-8 stack diagnostic](elasticsearch-async-json-task4/numeric-utf8-stack/console.log)
contains Core's `_startValueExpectComma`, session token draining, numeric
construction/grammar scanning, `getString` and TextBuffer work. This implicates
both parsing and model construction; it does not attribute the entire regression
to the integral cache. A follow-up must assess that tradeoff rather than judging
the getter-only speedup in isolation.

Separate [UTF-8 allocation profiling](elasticsearch-async-json-task4/utf8-allocation/jmh.json)
measured Gson/Search B/op of 12,176/1,960 small, 24,528/6,667 Unicode,
5,277,158/4,203,383 scalar and 985,666/1,130,602 numeric-rich. Numeric String
[allocation profiling](elasticsearch-async-json-task4/numeric-string-allocation/jmh.json)
was 971,978/1,130,058 B/op. Thus the numeric-rich Search tree construction also
allocates about 158–160 KiB more per 10,000 numeric nodes, consistent with extra
fixed numeric fields (an inference, not a measured object-layout decomposition).
These profiles remain separate from primary timing. UTF-8 Unicode diagnostic
allocation/timing had wide uncertainty, which is retained in raw data.


### Factory context and gate decision

[Warm factory construction](elasticsearch-async-json-task4/factories-final/jmh.json)
was 59,208 ±570 Gson providers/s and 2,261,577 ±8,644 JsonParsing factories/s.
[Cold single-shot samples](elasticsearch-async-json-task4/factories-cold/jmh.json)
were 30.9/24.0/32.6 ms for Gson and 16.6/11.7/11.8 ms for JsonParsing. These
first-constructor samples exclude JVM/JMH startup and have enormous three-sample
intervals. They compare different work: Gson registers existing backend codecs;
JsonParsing initializes only the parser factory, with no Task 5 Search codecs.
They cannot predict replacement-codec startup or justify a broad migration.
Affinity-only setup avoids warming either factory before the cold invocation.

**Task 4's exit condition is not met.** Writer regressions were investigated and
bounded optimizations were validated; strict and explicitly adapted accessor
primary timing recovered. The material large-String and numeric-rich parser regressions remain, and
Unicode output variability prevents a resolved verdict.
This checkpoint commits the tools, evidence and focused fixes so follow-up work
can reproduce the remaining gate. Task 5/6 remain untouched. A follow-up must
resolve the long-string/numeric parser tradeoffs and Unicode variability with correctness and bounded ownership
preserved, or record a justified architectural decision before broad migration.


## Verification and limitations

The [evidence index](elasticsearch-async-json-task4/README.md) links raw JSON,
commands, environments, dirty-source snapshots and consoles. The generated
[summary CSV](elasticsearch-async-json-task4/summary.csv) retains fork means and
JMH uncertainty instead of flattening measurements into a speedup claim.
Early runs captured source hashes but omitted untracked benchmark files from
their dirty patch; later runs capture their full diff too. Artifact hashes are
recorded for every run. Benchmark-only additions change the final JAR hash;
production source hashes identify unchanged writer/read algorithms across those
campaigns. No machine-specific binary JAR or profiler recording is committed.
A stale-JAR smoke run after a failed build was discarded, and the runner now
rejects a JAR older than Java source files.

The focused clean reactor and final JSON-module rerun exercised all 95 JSON
model/accessor/parser/output/API/cleanup/retention cases and eight backend framing
cases, with zero failures/errors/skips. Compilation with `-Werror`,
forbidden APIs, publication/dependency enforcement, packaging/Moditect and sources
passed for changed production modules. The performance package build passed Checkstyle, including the two additional
JMH benchmark classes. Earlier style failures were corrected before measurement. The
independent performance harness then passed all assertions. The [clean build log](elasticsearch-async-json-task4/final-verification.log) and
[full JSON rerun log](elasticsearch-async-json-task4/final-json-tests.log), and
[final opaque/numeric benchmark build](elasticsearch-async-json-task4/final-opaque-build.log) preserve
verification. Exact commands and raw run indexes are linked in the evidence index.

The incremental primary destination is an 8 KiB heap buffer. Direct/sliced/tiny
buffers are correctness-tested; direct-buffer throughput is not claimed.

No live HTTP, Elasticsearch/OpenSearch server, actual signing interceptor, full
reactor, Java 17 runtime, native image, or Task 5 codec comparison was run. The
factory/parser comparisons are limited to the existing implemented components;
they cannot predict replacement-codec startup cost. No exclusive core reservation
or universal performance claim is made.
