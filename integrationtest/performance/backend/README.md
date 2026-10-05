# Backend Performance tests

This module is designed to verify throughput of the document creation and indexing.

This module is decoupled from any mapper to allow running performance diagnostics
and find regressions in isolation from the various mappers.

## Build

To build the performance tests:

```
mvn clean install -pl integrationtest/performance/backend/elasticsearch -am -DskipTests -DskipITs
# OR
mvn clean install -pl integrationtest/performance/backend/lucene -am -DskipTests -DskipITs
```

## Run it from command line

```
java -jar integrationtest/performance/backend/elasticsearch/target/benchmarks.jar
# OR
java -jar integrationtest/performance/backend/lucene/target/benchmarks.jar
```

You may pass arguments, which will be interpreted as regexps. Any benchmark matching a regexp will be run.
By default all benchmarks get run, one after the other.

You may set parameters:

```
java -jar integrationtest/performance/backend/elasticsearch/benchmarks.jar \
    -jvmArgsPrepend -Duris=http://es1.mycompany.com \
    -jvmArgsPrepend -Dprotocol=https \
    -i 10 -p batchSize=50
```

* `jvmArgsPrepend`: add additional JVM properties to the forked process which runs the benchmark.
Note that this can be used to override backend properties, such as the Elasticsearch hosts in this example.
* `e`: excludes running all benchmarks matching this regexp pattern.
* `wi`: sets the number of warm-up iterations.
* `w`: sets the minimum time to spend at each warmup iteration.
* `i`: sets the number of measurement iterations.
* `r`: sets the minimum time to spend at each measurement iteration.
* `p`: set testing parameters (`@Param` in the code).

## Run it from your IDE

Within your IDE, run the test `SmokeIT` located in the project you're interested in.

## Run a profiler

Use the built-in profiler integrations, in particular the [AsyncProfiler integration](https://github.com/openjdk/jmh/blob/6d6ce6315dc39d1d3abd0e3ac9eca9c38f767112/jmh-core/src/main/java/org/openjdk/jmh/profile/AsyncProfiler.java)

You will need to [download/install AsyncProfiler](https://github.com/async-profiler/async-profiler?tab=readme-ov-file#download) first.
The following examples assume you set the async profiler home this way:

```shell
ASYNC_PROFILER_HOME=...
```

To profile GC, perfnorm, CPU, wall-clock and memory allocation (on Linux):

```shell
BACKEND=lucene
java -jar integrationtest/performance/backend/$BACKEND/target/benchmarks.jar \
    -prof gc -prof perfnorm \
    -prof="async:rawCommand=alloc,wall;event=cpu;output=jfr;dir=/tmp;libPath=${ASYNC_PROFILER_HOME}/lib/libasyncProfiler.so"
```

This will create directories with JFR recordings (`jfr-cpu.jfr`) in `/tmp`.

To convert the JFR recording to a wall clock flamegraph (most useful if you want to consider I/O):

```
JFR_RECORDING_PATH=...
java -cp $ASYNC_PROFILER_HOME/lib/converter.jar jfr2flame --state runnable,sleeping $JFR_RECORDING_PATH wall.html
```

To convert the JFR recording to a CPU flamegraph (only useful if you want to ignore I/O):

```
JFR_RECORDING_PATH=...
java -cp $ASYNC_PROFILER_HOME/lib/converter.jar jfr2flame --state default $JFR_RECORDING_PATH cpu.html
```

See https://github.com/async-profiler/async-profiler/issues/740#issue-1650739133 for information on why we're using `--state`.

To convert the JFR recording to a memory allocation flamegraph:

```
JFR_RECORDING_PATH=...
java -cp $ASYNC_PROFILER_HOME/lib/converter.jar jfr2flame --alloc --total $JFR_RECORDING_PATH alloc.html
```

# Notes

For best results disable features such as power management, dynamic CPU scaling,
and run it on a dedicated box which has no other significant services running.

In particular the "run it from your IDE" approach is just meant for development of new tests.

## Server-independent JSON comparisons

`JsonComparisonBenchmark` compares complete NDJSON output from the legacy Gson
HTTP provider, direct Jackson Core 3, synchronous cursor drain, incremental cursor
and a benchmark-only worker bridge. No Elasticsearch server is needed. The
Jackson comparison accesses the JSON module's internal implementation on the JMH
classpath; it does not add a public library API or a Jackson production dependency
to the backend.

```shell
./mvnw clean install -pl integrationtest/performance/backend/elasticsearch -am \
  -DskipTests -DskipITs -Dno-build-cache
integrationtest/performance/backend/scripts/run-json-benchmarks.sh \
  --skip-build --out /tmp/json-primary --cores 0,2
```

The runner requires Linux, Bash 4+, `jq`, `taskset`, Java, GNU core utilities and
the Maven wrapper. The Bash result and JFR summarizers also require `jq`. Omit
`--cores` to select two allowed online physical cores with the same reported
core-type/maximum-frequency class and exclude SMT siblings. Inspect its recorded
topology on machines where these hints are unavailable; maximum frequency alone
does not prove a hybrid-core class. An explicit selection must be within process
affinity. Choose at least two physical cores for the worker scenario. Affinity is
inherited by JMH forks and checked in benchmark and worker threads. Pinning is
not an exclusive CPU reservation. The runner does not change governors, turbo,
perf permissions, scheduler settings or any other host controls.

Defaults: one JMH thread, three independent forks, five one-second warmup and
five one-second measurement iterations, 1 GiB fixed heap, G1,
`ActiveProcessorCount` equal to selected logical CPUs. Every comparison uses
these same resources, including two CPUs for worker runs. Fixture construction,
tree conversion and exact full-byte validation run in trial setup. Primary writer
sinks use `Blackhole.consume(byte[])` on every emitted array, plus byte counts
and boundary bytes. `JsonSinkSensitivityBenchmark` repeats the same complete
writes with either sampled-only or opaque observation.
Boundary sampling alone does not prove that every interior store survives JIT
optimization. Compare relative writer results with both observation modes; rerun
affected gate comparisons with consistent opaque consumption if they change
materially. Opaque consumption adds no per-byte checksum, and chunk counts remain
implementation-specific.
No random generation or logging occurs in measured methods. `--quick` is a
one-fork smoke check and cannot establish the gate.

Summarize one or more recorded runs as CSV, including independent fork means,
fork variability and allocation scores when present:

```shell
integrationtest/performance/backend/scripts/summarize-json-results.sh \
  /tmp/json-primary/jmh.json /tmp/json-allocation/jmh.json > /tmp/json-results.csv
```

Fixtures (`--fixtures`): `small`, `mapping`, `unicode`, `nested`, `scalar-1m`,
`scalar-8m`, `bulk-10000`. The additional `numbers-10000` fixture measures
numeric construction/storage (an object containing an array of integers 0–9999).
Shared builders retain both trees outside timing.
Every body part has a final newline. `--lifecycle fresh` includes provider/body
construction and probing for every writer; Jackson and worker use the same
cursor probe as the candidate body. Legacy Gson probes a complete first part
and retains that encoding; the candidate probes at most 8 KiB and discards it.
`--lifecycle reused` excludes those costs for every writer and measures complete
serialization of an already constructed source. Reused incremental cursors also
reuse the caller's 8 KiB destination; synchronous adapters allocate their own.
The worker is a reusable single-thread executor with two circulating 8 KiB arrays
and bounded queues. Each invocation waits for full delivery and task completion;
worker creation belongs to trial setup. It is an experiment, not a production
scheduling proposal.

```shell
# Raw serialization lifecycle, then independent allocation profiling:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-reused --lifecycle reused
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-allocation --profiler gc
# Strict/explicit singleton-array accessors; no body fixture parameters:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-accessors --fixtures '' \
  --pattern 'JsonComparisonBenchmark.*Accessor$'
# Same materialized string input for both parsers:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-parsers --fixtures small,unicode,scalar-1m \
  --pattern 'JsonComparisonBenchmark.(gsonParser|searchParser)$'
# Full UTF-8 ingress: decoder/reader versus bounded async feed and end-of-input:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-utf8 --fixtures small,unicode,scalar-1m \
  --pattern 'JsonUtf8IngressBenchmark.*Parser$'
# Opaque sink sensitivity, sampled and opaque modes in the same campaign:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-sink-sensitivity --fixtures small,unicode,scalar-1m,bulk-10000 \
  --pattern 'JsonSinkSensitivityBenchmark.(gson|synchronousCursor|incrementalCursor)$'
# Numeric-rich parsing: conversion/storage costs moved into construction:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-numbers --fixtures numbers-10000 \
  --pattern '(JsonComparisonBenchmark|JsonUtf8IngressBenchmark).(gsonParser|searchParser)$'
# Portable stack sampling fallback; diagnostic timing is separate:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-stack --fixtures scalar-1m \
  --pattern 'JsonComparisonBenchmark.synchronousCursor$' --profiler stack
# Existing async-profiler installation, without automatic downloads/sysctl:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-cpu --profiler \
  "async:libPath=${ASYNC_PROFILER_HOME}/lib/libasyncProfiler.so;event=cpu;output=jfr;dir=/tmp/json-jfr"
```

The runner saves exact commands, Java/GC/heap settings, source/JAR SHA-256,
revision and dirty patch, topology/frequencies/governor/turbo, allowed affinity,
cgroup quotas/memory/cpuset and before/after load/CPU pressure. It refuses an
existing output directory. Retain raw JSON with per-fork samples. Run profiling
separately from primary timing and record unavailable controls. Do not run builds
or tests alongside timing. A repeatable regression above 5% triggers investigation;
it is not statistical proof and is never a hard assertion in a correctness test.

Independent byte/retention/backpressure/cancellation checks and a controlled
100 microsecond pause per worker chunk (actual waits depend on the scheduler):

```shell
taskset -c 0,2 java -Djson.benchmark.cores=0,2 \
  -cp integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  org.hibernate.search.integrationtest.performance.backend.elasticsearch.JsonSlowConsumerHarness
java -cp integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  org.hibernate.search.integrationtest.performance.backend.elasticsearch.JsonBufferRetentionProbe
```

The harness verifies complete bytes at capacities 1/2/5/8192 and cursor
cancellation release independently of throughput. Encoded cursor state is at
most six bytes; the worker owns two 8 KiB arrays, including queued/in-flight
storage. Source trees, generators, destination buffers and diagnostic capture
are accounted separately. The slow harness deliberately captures a full body
for correctness; production and JMH output do not. Lone-surrogate output policy
is tested separately: Gson replaces with `?`, whereas new output uses `\\uXXXX`.
No byte-equivalence claim covers that intentional policy difference.

`--skip-build` rejects a JAR older than JSON/backend/benchmark Java sources.
The recorded patch includes untracked benchmark source files as well as tracked
changes. Factory-only benchmarks use affinity-only setup so they do not initialize
factories before measuring. Cold single-shot factory context (three samples are
not a dependable startup confidence interval):

```shell
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-factories-cold --cores 0,2 --cold --fixtures '' \
  --pattern '(JsonBaselineBenchmark.initializeProvider|JsonComparisonBenchmark.initializeParser)$'
```

Parser selectors compare the retained String convenience API with the same
already materialized input, not transport ingress. The optimized convenience
path uses Core's character parser and the same tree builder. It validates raw
UTF-16, counts UTF-8 when a byte limit is configured, retains decoded UTF-8 name
limits and rejects broken escaped name surrogates. It creates no secondary
whole-body String or byte array. Network parsing still consumes bounded byte
slices. Do not infer network throughput or large-token event-loop latency from
these String API numbers. Numeric nodes retain exact lexemes and cache an
in-range integral value; decimal/overflow narrowing still uses BigDecimal.
Caching adds fixed tree storage, which is separate from retained encoded output.

`JsonUtf8IngressBenchmark` starts with the same caller-owned UTF-8 bytes for both
parsers. It includes creation, decoding/feed, complete consumption, root tree
construction and close in each invocation. Gson uses an `InputStreamReader`;
Search feeds a `ByteBuffer` repeatedly until all bytes are accepted, then calls
`endOfInput`. Both returned trees are observed by JMH and checked for complete
content in setup. It measures synchronous completion of the async SPI, without
a transport, network fragmentation or an event-loop latency guarantee. Parser
benchmarks use only the first fixture part, as the parser accepts a single JSON
root; output benchmarks drain the complete NDJSON body. The output lifecycle
parameter does not alter these parser operations.

### Follow-up isolation benchmarks

`JsonScalarConsumptionBenchmark` starts with the same owned String for all three
methods. Its Jackson-only method advances through every token and calls
`getString()` for property names and scalar values, consuming each returned
String opaquely. This forces lazy string completion and assembly. It deliberately
omits Search's raw UTF-16 validation, single-root tree checks, name-byte checks
and tree allocation; it isolates upstream work on valid single-root fixtures,
not an interchangeable parser. Core token/depth/length constraints match the
Search defaults. Setup checks the complete expected scalar outside timing.

```shell
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-scalar-isolation --cores 0,2 --warmup 8 \
  --fixtures scalar-1m --pattern 'JsonScalarConsumptionBenchmark.*'
```

`JsonNumericConsumptionBenchmark` constructs the complete 10,000-number tree
before reading it. `reads=none` observes the tree without numeric access;
`selected` reads every hundredth value once (100 reads); `repeated` reads every
value ten times (100,000 reads). Every operation consumes the tree opaquely and
returns the sum. Setup independently checks sums of 0, 495,000 and 499,950,000.
Construction experiments must include these cases, and full UTF-8 construction,
when assessing eager caches versus conversion on demand. Getter-only results
cannot answer that tradeoff.

```shell
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-numeric-consumption --cores 0,2 --fixtures numbers-10000 \
  --pattern 'JsonNumericConsumptionBenchmark.*'
# Select a subset with repeatable additional JMH parameter arguments:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-numeric-selected --cores 0,2 --fixtures numbers-10000 \
  --pattern 'JsonNumericConsumptionBenchmark.*' --param reads=none,selected
# Matched maximum input read/feed fragments; default feedSize remains 8192:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-fragments --cores 0,2 --fixtures unicode,scalar-1m \
  --pattern 'JsonUtf8IngressBenchmark.*Parser$' --param feedSize=64,1024,8192
```

Fragmentation caps both the Gson InputStream read length and Search's offered
ByteBuffer region. Search also retains its configured maximum internal feed
size of 8192. Both paths parse through EOF, return complete trees and verify full
bytes in trial setup for every chunk parameter. This measures CPU throughput
under controlled in-memory fragmentation, not network arrival timing or an
event-loop latency distribution. One-byte and escaped-name boundaries remain
covered by parser correctness tests; their cost need not be repeated in every
large-scalar throughput campaign.

The scalar-consumption fixture also accepts `numbers-10000` for upstream numeric
lexeme completion. `jacksonUtf8Tokens` completes the same scalars through Core's
public async parser with 8192-byte slices and explicit EOF. Setup verifies every
scalar against the fixture through both character and UTF-8 token paths. This
UTF-8 baseline omits Search's separate canonical UTF-8 validator, ownership
handoff clearing, tree construction and the escaped-name workaround (fixture
names are ASCII). It is a diagnostic baseline, not a replacement SPI or a
strictness-equivalent end-to-end tree parser.

`JsonStringUtf8Benchmark` compares complete parsing of an already materialized
String through Gson, the current Search character parser, standard bounded
UTF-8 encoding with a String-backed CharBuffer plus the Search async parser, and
one experiment with fixed array-backed character staging. Encoder/session/buffer
construction, all encoding/feeds/EOF/close and tree construction are timed;
fixtures and factories are shared outside timing. Every complete tree is observed
opaquely, and trial setup independently checks every fixture member and decoded
value plus numeric lexeme/escaping invariants. Gson comparisons cover valid input;
they do not imply matching acceptance policies.

Its focused fixtures are `scalar-1m` (one large ASCII token), `unicode-1m` (large
Unicode and escaped value), `small` (typical query) and `fields-1m` (a similarly
large document containing 18,000 short fields). These fixture names belong to
this benchmark; do not pass them to unrelated benchmark classes.
The wrapped helper retains a fixed 8192-byte output scratch; staging adds a fixed
4096-char scratch. Neither creates a complete encoded input body. The original
String, token Strings and tree remain materialized. Production parsing remains
synchronous; the async parser here does not impose an executor.

```shell
java -cp integrationtest/performance/backend/elasticsearch/target/benchmarks.jar \
  org.hibernate.search.integrationtest.performance.backend.elasticsearch.JsonStringUtf8Verification
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-string-primary --cores 0,2 --forks 3 --warmup 8 --iterations 5 \
  --fixtures scalar-1m,unicode-1m,small,fields-1m \
  --pattern 'JsonStringUtf8Benchmark.(gsonString|search.*String)$'
# Separate encoding-only diagnostics; not complete-tree competitors:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-string-encoding --cores 0,2 --warmup 8 \
  --fixtures scalar-1m,unicode-1m --pattern 'JsonStringUtf8Benchmark.diagnostic.*'
# Separate GC allocation profile, explicitly diagnostic:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-string-allocation --cores 0,2 --forks 1 --warmup 5 --iterations 3 \
  --fixtures scalar-1m,unicode-1m,small,fields-1m \
  --pattern 'JsonStringUtf8Benchmark.*' --profiler gc
```

The standalone verification checks fixed/tiny buffer boundaries, supplementary
pairs, malformed raw UTF-16, split JSON escapes, exact byte/token/name/depth
limits, EOF/trailing input, complete tree/lexeme equality and session cleanup.
Four-byte encoder output is the minimum capacity that can hold a supplementary
code point; tests also cap async feeds at one byte. The helpers are experiments;
no automatic size dispatch is implied by these scores. See the
[owned-String experiment report](../../../design/elasticsearch-async-json-string-utf8.md).

`JsonNumericUtf8DiagnosticBenchmark` isolates upstream numeric token completion
through public Core async ByteBuffer, async byte-array, and synchronous UTF-8
parsers. `fixture=integers` uses exactly the 10,000-integer root above; `mixed`
uses negative/decimal/exponent/overflow values. The diagnostic mixed fixture
omits `-0` because Core normalizes it; the complete-tree `numbers-mixed-10000`
fixture includes `-0` and independently checks lexical output. Diagnostic
`work=tokens` consumes all numeric Strings, `numbers` additionally constructs and
observes validated cached numbers, and `tree` constructs the known fixture shape
with Search primitives. Setup verifies all lexemes and tree values through each
input path, and constructs validated numbers in number mode. These paths omit
Search canonical validation, ownership handoff, escaped-name workaround and the
general single-root builder; their deltas are not exact CPU-cost fractions or
strictness-equivalent parser comparisons. Synchronous bytes are diagnostic only.
`treeFromOwnedLexemes` excludes token/String production and builds a new tree
from already owned immutable Strings; use `work=tree` to avoid duplicate rows.

The ingress benchmark now accepts `bufferKind=heap,direct,readonly` (default
`heap`). Direct/read-only input storage is prepared outside timing; each operation
starts with a new view and includes feeds, EOF/tree completion and close. Gson
still reads the identical original bytes. Its `numbers-mixed-10000` root is 82,512
bytes, cycling `-0`, `-42`, `0.125`, `1.00`, `6.022e23`, `-7E-9`, an overflowing
integer and a huge exponent. It is a construction guard, not a production mix.

```shell
# Complete-tree primary; every setup verifies full bytes/lexemes before timing:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-numeric-heap --cores 0,2 --warmup 8 \
  --fixtures numbers-10000,numbers-mixed-10000 \
  --pattern 'JsonUtf8IngressBenchmark.*Parser$' --param feedSize=8192 --param bufferKind=heap
# Same upstream scalar-completion work through different feeders, diagnostic:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-numeric-tokens --cores 0,2 --warmup 8 --fixtures '' \
  --param fixture=integers --param work=tokens \
  --pattern 'JsonNumericUtf8DiagnosticBenchmark.(async.*|synchronousUtf8)$'
# Separate allocation decomposition, not primary timing:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-numeric-allocation --cores 0,2 --forks 1 --warmup 5 --iterations 3 \
  --fixtures '' --param fixture=integers --param work=tokens,numbers,tree \
  --pattern 'JsonNumericUtf8DiagnosticBenchmark.async.*' --profiler gc
# Separate JFR profile; leave binary recordings outside the repository:
integrationtest/performance/backend/scripts/run-json-benchmarks.sh --skip-build \
  --out /tmp/json-numeric-jfr --cores 0,2 --forks 1 --warmup 8 \
  --fixtures numbers-10000 --pattern 'JsonUtf8IngressBenchmark.searchParser$' \
  --profiler 'jfr:dir=/tmp/json-numeric-recordings;configName=profile;stackDepth=64'
jfr print --json --events jdk.ExecutionSample,jdk.NativeMethodSample --stack-depth 64 \
  /path/to/profile.jfr > /tmp/json-numeric-samples.json
integrationtest/performance/backend/scripts/summarize-json-jfr.sh \
  /tmp/json-numeric-samples.json > /tmp/json-numeric-stacks.txt
```

The summarizer retains top/inclusive counts, total/selected/empty/truncated sample
counts and complete unique stacks; it does not infer exact CPU fractions.
The [numeric UTF-8 report](../../../design/elasticsearch-async-json-numeric-utf8.md)
records the retained heap-access optimization, rejected probes, matched controls,
allocation and the still-open numeric regression.
