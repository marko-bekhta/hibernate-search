# JSON handler migration: frozen performance controls

Date: 2026-10-13. The frozen current primary campaign passed all 29 cases and
its separate allocation campaign passed ten cases. Historical controls passed
five primary and two allocation cases. The timing window is released. Both controls'
selector/setup smoke campaigns passed (11 current and 9 historical cases). Smoke scores are
not acceptance evidence. This document accompanies
[the implementation plan](elasticsearch-json-handlers.md).

## Sources and artifacts

| Control | Frozen revision | Worktree |
| --- | --- | --- |
| Current Search tree, primary | `8e570e5e7cdd3018c5991e5c864064c1769430f2` | `/home/agentuser/hibernate-search-json-handler-baseline` |
| Historical production Gson, secondary | `a853b204cb3dd8d07a49a2779c55bfe25bc024bd` | `/home/agentuser/hibernate-search-json-handler-historical` |

Both worktrees use their checked-in harness and production implementation without
edits. The historical revision already provides Gson benchmark methods using its
real backend provider; no legacy implementation was restored or copied. Its
runner differs only in the default selector, which these commands override.
Historical fixture setup builds Gson trees, while current fixture setup parses
independent text into Search trees. Fixture/tree construction stays outside timing.

Both clean scoped reactor builds passed:

```sh
./mvnw clean install -pl integrationtest/performance/backend/elasticsearch -am \
  -DskipTests -DskipITs -Dno-build-cache
```

Build logs and preserved standalone shaded benchmark JARs are in
`/home/agentuser/json-handler-baseline-evidence/`. The current JAR SHA-256 is
`407786cb0ee100d8664dcb9397aa53c259c6180299819439458c8f73ceb7bbfb`;
the historical JAR SHA-256 is
`cf2ac6ff22977d305eb372aff85ea469448fb6a68a3b9b2e2197c64856d18001`.
JMH forks run these self-contained JARs, not Maven-resolved runtime classpaths.
Later SNAPSHOT installs therefore cannot replace classes in these artifacts.

## Matched workloads

`baseline-fixtures.csv` and `historical-fixtures.csv` match exactly: first-root
UTF-8 length/hash and complete NDJSON length/hash. `FixtureFingerprint.java`
records the check independently of measured methods. Current full-body expected
bytes come from fixture text; historical bytes come from its configured Gson.

| Fixture | Single-root input bytes | Complete NDJSON bytes |
| --- | ---: | ---: |
| small | 44 | 45 |
| unicode | 3,851 | 3,852 |
| scalar-1m | 1,048,587 | 1,048,588 |
| numbers-10000 | 48,902 | 48,903 |
| bulk-10000 | 40 (metadata root only) | 3,257,780 |

Bulk is output-only in this campaign. Every writer drains the complete body to
the same opaque array-consuming sink. Fresh output includes body/provider
construction and its real probe/cache behavior; reused output excludes construction
for every implementation. Historical Gson may retain the encoded first part;
that is real historical behavior, not a candidate memory allowance.

UTF-8 parsing includes creation, complete 8 KiB-capped feeding/reading, EOF,
tree construction and close; Gson additionally uses its decoder/reader. These
are valid-input comparisons and do not imply identical acceptance policies.
Input storage is heap-backed. Numeric consumption includes String parsing plus
none/selected/repeated reads and opaque tree consumption; expected sums are
0/495,000/499,950,000. Accessors use equivalent completed trees.

## Execution protocol

`run-control.sh` in the evidence directory contains exact invocations. Primary
commands use three forks, eight one-second warmups, five one-second measurements,
one JMH thread, fixed 1 GiB heap, G1, `ActiveProcessorCount=2`, and CPUs `0,2`.
The CPUs are distinct physical cores with SMT siblings `0-1` and `2-3`, both
reporting 5.2 GHz maximum frequency. Affinity includes CPUs 0-23 and no cgroup CPU
quota is configured. Pinning is not exclusive reservation. The runner captures
full topology, JVM/GC settings, load/pressure, cgroups, source hashes, artifact
hashes, exact commands, empty production patch and independent fork samples.

| Primary campaign | Current cases | Historical cases |
| --- | ---: | ---: |
| UTF-8 ingress: small/unicode/scalar-1m/numbers-10000 | 4 | 3 (excludes small) |
| String parsing: same four fixtures | 4 | Deferred |
| Numeric consumption: none/selected/repeated | 3 | Deferred |
| Output: small/unicode/scalar-1m/bulk-10000, fresh/reused | 16 (sync/incremental) | 2 (fresh Unicode/scalar Gson writer) |
| Strict/adapting accessors | 2 | Deferred |
| Total | 29 | 5 |

Each primary case has 39 timed seconds plus fork/setup overhead. Allocation
profiling is separate: one fork, five warmups, three measurements, `-prof gc`;
ingress small/scalar-1m/numbers-10000, all numeric read modes, and fresh output
unicode/bulk-10000. Historical allocation narrows to integer ingress and Unicode
fresh output (two cases). Broader historical controls follow the candidate gate,
rather than delaying the primary current baseline. Builds/tests are stopped during the primary and profiling
window. Smoke uses `--quick`, is marked non-gating and validates selectors/setup.

```sh
/home/agentuser/json-handler-baseline-evidence/run-control.sh baseline primary
/home/agentuser/json-handler-baseline-evidence/run-control.sh baseline allocation
/home/agentuser/json-handler-baseline-evidence/run-control.sh historical primary
/home/agentuser/json-handler-baseline-evidence/run-control.sh historical allocation
```

The evidence directory uses distinct `<control>-<mode>-<campaign>` directories,
each containing `jmh.json`, `environment.json`, `source.patch`, `command.txt` and
`console.log`. Preserve these raw results; summary CSVs cannot replace fork data.

## Results and remaining evidence

All current primary metadata checks passed: exact revision, clean worktree,
expected artifact hash, three independent forks, eight warmups, five measurement
samples per fork, and runner `gating=true`. Scores below are ops/s rounded for
orientation; raw JSON and `full-parameter-scores.csv` retain uncertainties, every
fork mean and numeric `reads` parameters omitted by the stock CSV summarizer.

| Input fixture | Complete UTF-8 ingress | Owned-String parsing |
| --- | ---: | ---: |
| small | 3,770,270 | 3,536,941 |
| unicode | 162,465 | 325,539 |
| scalar-1m | 1,341 | 702 |
| numbers-10000 | 4,185 | 4,531 |

Numeric construction plus reads: none 4,512; selected 4,543; repeated 2,808 ops/s.
All input/numeric fork coefficients of variation are below 3.1%.

| Output fixture | Fresh sync | Fresh incremental | Reused sync | Reused incremental |
| --- | ---: | ---: | ---: | ---: |
| small | 1,736,744 | 1,558,506 | 3,638,065 | 3,819,335 |
| unicode | 133,579 | 139,400 | 316,863 | 296,590 |
| scalar-1m | 2,382 | 2,401 | 2,415 | 2,415 |
| bulk-10000 | 204 | 204 | 196 | 202 |

Strict/adapting accessors score 47,937,256 / 26,087,572 ops/s. Several tiny-output
and strict-accessor rows are noisy: fork CV is 16.7% for fresh small incremental,
7.0% fresh small sync, 7.6% reused small incremental, 5.2% reused Unicode
incremental, 5.6% fresh bulk incremental and 26.0% strict accessors. Preserve
those forks and investigate close candidate deltas with targeted interleaved
reruns; these rows alone cannot establish a 5% non-regression bound. Unicode
fresh and scalar output are stable. Recorded load stayed modest; no sustained
CPU pressure identifies the cause of variation.

No candidate performance result or gate passage is claimed. Supported
mixed decimals/large integers, fragment/buffer variants, larger output matrix,
handler boundaries and integrated backend operations remain subsequent checkpoint
workloads. The unsupported extreme-exponent fixture is deliberately excluded.

The host runs OpenJDK 25.0.4.1. Production compilation targets Java 17, which is
not Java 17 runtime verification. No server, network/event-loop latency or
native-image campaign is part of this server-independent baseline. Allocation
scores are bytes allocated per operation, not retained heap or bounded-buffer
proof. The existing structural retention/cleanup correctness checks remain
separate hard gates.

Separate current GC profiles report approximately 1,968 B/op for small UTF-8
ingress, 4,203,390 B/op for scalar-1m, and 1,130,610 B/op for integers. Numeric
construction allocates 1,130,058 B/op across all three read patterns. Fresh output
allocation is 5,584 / 1,672 B/op (sync/incremental) for Unicode and
5,624,097 / 5,615,830 B/op for bulk. These are independent profiler measurements;
their throughput scores are not substituted for primary timing.

## Narrow historical reference

Historical primary fork CVs are below 1.9%. Matched valid-input results are:

| Workload | Current Search tree ops/s | Historical Gson ops/s |
| --- | ---: | ---: |
| UTF-8 Unicode ingress | 162,465 | 104,718 |
| UTF-8 scalar-1m ingress | 1,341 | 1,245 |
| UTF-8 integer ingress | 4,185 | 4,644 |
| Fresh Unicode output, synchronous | 133,579 | 34,210 |
| Fresh scalar-1m output, synchronous | 2,382 | 469 |

The current integer-ingress point estimate is 9.9% below historical Gson
(4,185 ±36 versus 4,644 ±79 ops/s, reported JMH 99.9% errors). This reproduces
the remaining numeric cost, rather than resolving it or claiming a candidate
result. Scalar-ingress variability documented in earlier reports remains context;
one stable fresh control does not establish a universal result. Output compares
the real fresh lifecycle, including historical provider caching, with matched
complete bytes. Broader historical String/read/accessor/output controls are deferred.

Historical separate GC profiles report 985,666 B/op for integer ingress and
90,664 B/op for fresh Unicode output. Current integer ingress allocates about
14.7% more (1,130,610 B/op), while current Unicode streaming allocates less.
These are total allocations, not proof about retained tree/encoded-buffer size.

## Shaded-artifact limitation

The inherited shaded benchmark JARs omit `Multi-Release: true`; the Jackson Core
3.2.2 dependency includes six Java 17/21/23 versions of `FastDoubleSwar` and
`FastIntegerMath`. Preserved manifests and class names are in the evidence
directory. Matching candidate shading maintains this control's configuration,
but may omit newer-JDK dependency implementations used by normal unshaded
deployment. Before claiming integrated deployment performance, corroborate
critical rows with matching normal dependency classpaths or uniformly corrected
shaded controls. Do not change this attribute only for the candidate.
