# JSON handler migration: performance evidence

Status: integrated ordinary-classpath confirmation completed, 2026-10-14.
The covered isolated v3 kernel comparison remains cleared. Actual production parser,
writer and numeric-read routes meet the matched investigation thresholds, and allocation is stable
or lower. The bounded accessor correction improves adapting reads, with residual
throughput and allocation tradeoffs documented below. Full backend
indexing/search throughput remains separate. The [frozen controls](elasticsearch-json-handlers-baseline.md)
and [implementation plan](elasticsearch-json-handlers.md) define the comparisons.

The user's current acceptance priorities are neutral user JSON APIs, trusted built-in
Jackson support, asynchronous tree parsing and Vert.x compatibility. A repeated
throughput loss over 5% triggers investigation; it is not an automatic release
blocker. The focused accessor control below reports residual tradeoffs rather
than starting an indefinite optimization campaign. Its matched current baseline is
the Search-owned tree at `8e570e5e7c`, not the historical Gson implementation.
Earlier experimental gate decisions remain dated evidence of those checkpoints.

## Provenance and workload review

The candidate is in `/home/agentuser/hibernate-search-json-handler-candidate/experiments/json-handler-kernel`.
Its immutable shaded JAR is preserved at
`/home/agentuser/json-handler-baseline-evidence/candidate-kernel-benchmarks.jar`,
SHA-256 `78023aef8904958d52b35bd68dd5f5f489c16083c7335774065ed8e326cfff35`.
The verification manifest records nineteen passing unit-test invocations and
27 nonforked setup/complete-consumption smoke cases. Smoke scores are not
acceptance evidence. Runtime/test sources, POM, generated harness and frozen
dependency hashes were checked before timing; the README changed after compilation.

The candidate pins the byte-identical current utility SPI JAR under task-specific
coordinates, SHA-256
`e478bccb84a3692cbcf5ab5664c4e322f7ba2dada64673b3943525a955bf3a71`.
This avoids the historical build's overwrite of mutable local SNAPSHOT artifacts.
Only type-independent body/cursor/options/adapters are reused; measured trees
are Jackson nodes without conversion to Search trees.

Candidate first-root and full-NDJSON fixture lengths/hashes match both frozen
controls exactly. Reviewed measured operations retain creation/feed/EOF/tree/close
for UTF-8 ingress, separate owned-String parsing, construction plus numeric
none/selected/repeated reads, complete opaque-array output, fresh whole-body
8 KiB probing, and reused-buffer lifecycles. The idle worker and setup verification
remain outside timing. Candidate navigation accessors are stand-ins and excluded
from the production gate.

Primary candidate coverage is the same 27 parser/numeric/output cases from the
current control. Settings match: three independent forks, eight one-second
warmups, five one-second measurements, one thread, 1 GiB fixed G1 heap,
`ActiveProcessorCount=2`, OpenJDK 25.0.4.1 and verified CPU affinity `0,2`.
Allocation profiling runs separately with one fork/five warmups/three measurements.
The existing runner was adapted only for the frozen candidate artifact and
experimental source/POM/provenance hashes; its copy and commands are preserved
in `/home/agentuser/json-handler-baseline-evidence/`.

## V1 results and acceptance status

All 27 candidate primary and ten separate allocation cases passed their execution
checks. Fourteen additional matched primary cases and four numeric-output GC
cases completed. Each primary case has three independent forks/eight warmups/five
measurement iterations. Targeted repeats confirm distinct output regressions;
successful execution and input gains do not establish performance acceptance.
The timing window is released for kernel-only correction. Optional handler timing
is deferred until this gate passes.

The six-byte encoded cursor bound excludes derived unencoded number Strings and
any BigDecimal String cache retained in the source value. Per-tree cycle tracking
also belongs to measured allocation. These costs cannot be inferred from tree
construction profiles alone. Source/runtime retention and tiny-destination
correctness remain independent hard requirements.

| Initial primary workload | Current control ops/s | Candidate ops/s | Candidate delta |
| --- | ---: | ---: | ---: |
| UTF-8 small | 3,770,270 | 3,809,713 | +1.0% |
| UTF-8 Unicode | 162,465 | 154,330 | -5.01%; see repeats |
| UTF-8 scalar-1m | 1,341 | 1,349 | +0.6% |
| UTF-8 integers | 4,185 | 4,604 | +10.0% |
| Integer construction/no reads | 4,512 | 5,299 | +17.4% |
| Integer construction/selected reads | 4,543 | 5,304 | +16.8% |
| Integer construction/repeated reads | 2,808 | 3,392 | +20.8% |

Candidate integer UTF-8 ingress is near the historical Gson reference
(4,604 ±79 versus 4,644 ±79 ops/s). Unicode ingress is at the investigation
threshold (154,330 ±3,040 versus current 162,465 ±1,791); neither this initial
point estimate nor earlier unrelated campaigns substitutes for the interleaved
evidence below.

Fresh candidate Unicode/scalar output stays within 2% of the current control.
Small incremental output is 1.0% lower, but small synchronous output is 13.3%
lower against a noisy control and needs repetition. Bulk output is materially
lower: incremental 179.5 versus 204.1 ops/s (-12.0%), synchronous 175.0 versus
204.3 (-14.3%). These distinct common-path losses keep the early gate unresolved;
integer input improvements do not average them away. The repeats and allocation
profiles below ground the next kernel-only correction.

| Candidate initial output ops/s (delta versus current) | Fresh sync | Fresh incremental | Reused sync | Reused incremental |
| --- | ---: | ---: | ---: | ---: |
| small | 1,506,481 (-13.3%) | 1,542,525 (-1.0%) | 3,306,946 (-9.1%) | 3,263,026 (-14.6%) |
| unicode | 130,851 (-2.0%) | 138,614 (-0.6%) | 310,414 (-2.0%) | 294,065 (-0.9%) |
| scalar-1m | 2,381 (-0.0%) | 2,406 (+0.2%) | 2,381 (-1.4%) | 2,403 (-0.5%) |
| bulk-10000 | 175 (-14.3%) | 180 (-12.0%) | 179 (-8.9%) | 179 (-11.6%) |

Owned-String parsing is small +3.1%, scalar-1m +0.06%, integers +17.4%; initial
Unicode is -9.1%. The targeted Unicode String repeat is 324,460 current versus
323,452 candidate (-0.3%), so the initial loss did not repeat. Preserve both
batches as variability evidence rather than selecting the favorable batch.

## Repeats and numeric-output controls

| Matched primary follow-up | Current ops/s | Candidate ops/s | Delta |
| --- | ---: | ---: | ---: |
| Fresh bulk synchronous repeat | 204.7 | 183.2 | -10.5% |
| Reused small synchronous repeat | 3,617,838 | 3,220,664 | -11.0% |
| Fresh integer-array incremental output | 3,528 | 3,236 | -8.3% |
| Fresh integer-array synchronous output | 3,387 | 3,056 | -9.7% |

Bulk repeat fork CV is 3.6% current/3.2% candidate; small repeat is 0.7%/3.6%.
These losses recur across separate campaigns and exceed the investigation
threshold. They keep the gate open independently of Unicode uncertainty.

UTF-8 Unicode used current/candidate/candidate/current ordering, each three forks.
The six-fork means are 162,936 current versus 154,295 candidate (-5.3%), with
fork CV 2.2%/4.3%; paired campaign deltas are -3.5% and -7.1%. This is not a clean
5% non-regression result. A corrected-artifact campaign must revisit it; neither
averaging nor the String repeat establishes its cause.

## Separate allocation evidence

| Operation | Current B/op | Candidate B/op | Observation |
| --- | ---: | ---: | --- |
| Integer UTF-8 ingress | 1,130,610 | 810,402 | -28.3% |
| Integer construction plus reads | 1,130,058 | 809,898 | -28.3%; all read patterns |
| Fresh Unicode incremental output | 1,672 | 2,312 | +38.3%; fixed per-operation growth |
| Fresh Unicode synchronous output | 5,584 | 6,240 | +11.7%; fixed per-operation growth |
| Fresh bulk incremental output | 5,615,830 | 13,235,549 | +135.7% |
| Fresh bulk synchronous output | 5,624,097 | 13,243,814 | +135.5% |
| Fresh integer-array incremental output | 1,786 | 571,691 | About 570 kB added per 10,000 values |
| Fresh integer-array synchronous output | 10,050 | 579,955 | About 570 kB added per 10,000 values |

Small UTF-8 ingress is 1,968 versus 1,944 B/op; scalar-1m is effectively unchanged
(4,203,390 versus 4,203,398). Profiles are independent one-fork diagnostic runs;
their throughput is not used as primary acceptance data. B/op is total allocation,
not retained heap or a formal object-layout decomposition.

Source inspection identifies two correction targets: a lazily allocated cycle
IdentityHashMap for each part cursor, and primitive numeric rendering through
`numberValue().toString()`. The extra integer-array allocation isolates generated
numeric text in a single-part body, while bulk also pays per-part tracking costs.
Reuse of bounded traversal/formatting state across sequential parts and avoiding
primitive scalar Strings are plausible kernel-only remedies. Their performance
and correctness require a new artifact, tests and matched measurements; this
report does not claim those fixes already work. Keep large BigInteger/Decimal
derived-character storage and any number-owned String cache explicitly accounted.

Raw results, command/environment metadata, full-parameter CSV, immutable JARs,
runtime/generated-source archives and helper hashes are preserved under
`/home/agentuser/json-handler-baseline-evidence/`. Candidate primary rows use
`candidate-primary-*`, profiles `candidate-allocation-*`, interleaved controls
`{baseline,candidate}-repeat-*`, and numeric writers
`{baseline,candidate}-numeric-output-{primary,allocation}`. The v1 artifact and
those directories must remain immutable when a corrected version is evaluated.

## V2 correction and matched measurements

V1 evidence above remains immutable. V2 SHA-256 is
`0f0f016d8dab9c9f125b60ce46d11e9e700f1392675f5098328efe7e5856c9b0`,
preserved as `candidate-v2-kernel-benchmarks.jar` with versioned verification and
source archives in the evidence directory. Runtime/generated-source and pinned
SPI hashes were checked before timing; fixtures still match the frozen control.
Thirty-one tests passed and twenty complete-output setup cases passed; those
smoke scores are not acceptance data.

V2 adds a lazy fixed twenty-character **unencoded** primitive-number buffer,
shared across sequential parts within one traversal. It replaces shallow-tree
cycle maps with ancestor identity checks; deep paths retain hashing. Workspaces
are independent across cursors and source references are released on completion,
failure and cancellation. BigInteger/Decimal generated Strings remain explicitly
accounted. These changes affect output; parser and benchmark workload sources
are unchanged.

Matched v2 campaigns completed 26 primary cases and twelve separate GC cases;
settings and affinity match the original protocol. The table uses adjacent
synchronous controls, rather than selecting older favorable means:

| Synchronous output | Adjacent current ops/s | V2 ops/s | Delta |
| --- | ---: | ---: | ---: |
| Fresh small | 1,644,524 | 1,546,195 | -6.0% |
| Fresh bulk | 196.1 | 183.9 | -6.2% |
| Reused small | 3,685,617 | 3,201,699 | -13.1% |
| Reused bulk | 202.5 | 184.1 | -9.1% |
| Fresh numeric array, incremental | 3,265 | 3,349 | +2.6% |
| Fresh numeric array, synchronous | 3,492 | 3,447 | -1.3% |

V2 incremental small/bulk output also remains below the original control in
several rows; those use older controls and retain their uncertainty in raw JSON.
The adjacent synchronous losses are sufficient to keep the gate unresolved.
Numeric output's earlier repeated loss is recovered in this matched campaign.

| Fresh output allocation | Adjacent current B/op | V2 B/op |
| --- | ---: | ---: |
| Small incremental | 1,928 | 2,168 |
| Small synchronous | 2,032 | 2,288 |
| Bulk incremental | 5,615,835 | 6,177,453 |
| Bulk synchronous | 5,624,099 | 6,185,716 |
| Numeric array incremental | 1,786 | 2,010 |
| Numeric array synchronous | 10,050 | 10,347 |

The roughly 570 kB numeric-string growth disappears; remaining numeric/small
growth is fixed per traversal. Bulk growth is about 10%, greatly reduced from
135.5%, but per-part traversal storage remains measurable. Proposed allocation
investigation and repeated throughput losses are not silently waived.

Relevant parsing checks completed afterward. UTF-8 small/scalar/integers are
+3.2%/+1.1%/+9.1% against frozen current; Unicode is -1.9% against an adjacent
control (154,115 versus 157,056). Owned-String small/integers are +5.7%/+15.1%,
scalar -0.8%, and Unicode +7.4% against an adjacent control (327,445 versus
304,952). Previous Unicode losses therefore do not repeat consistently; retain
all versions/batches as variability evidence. No repeatable parsing regression
is established by the v2 checks.

Raw v2 directories are `candidate-v2-*` and `baseline-v2-*`; helper/source/JAR
hashes and full-parameter summaries are preserved. The timing window is released
for another focused correction. V2 already uses exact class comparisons; the earlier
enum/virtual-dispatch hypothesis is withdrawn. Per-part cursor/frame costs and
additional primitive-rendering state work are investigated below.
No broad migration or optional handler performance acceptance is claimed.

## V2 diagnostic attribution

Four serial matched JFR profiles compare current/v2 synchronous reused output for
small and bulk payloads. They use the frozen artifacts, affinity `0,2`, one fork,
eight two-second warmups and ten two-second measurements with JFR `profile` and
64-frame stacks. Every runner marks these profiles non-gating. Around 1,950
benchmark-thread CPU stacks and 5,800 allocation samples were captured per case.
Profiled throughput does not replace the three-fork primary results.

| Top-frame CPU samples | Current small | V2 small | Current bulk | V2 bulk |
| --- | ---: | ---: | ---: | ---: |
| Total benchmark-thread stacks | 1,948 | 1,957 | 1,968 | 1,961 |
| Cursor prepare | 162 | 252 | 132 | 174 |
| Cursor prepareFrame | 246 | 235 | 181 | 159 |
| Cursor writeIntegralRun | 0 | 53 | 0 | 47 |
| Cursor enterContainer | 0 | 19 | 0 | 7 |

These diagnostic counts identify work worth isolating; they do not establish
exact CPU fractions or prove a method causes the throughput difference. JIT
inlining and sampling locations also affect attribution. Traversal/string copying
dominates both implementations; cycle detection does not appear to dominate v2.
Number formatting and the added integral-state branches are visible on the small
path. A controlled one-axis change should distinguish that state work from
per-part traversal construction before another primary campaign.

Bulk allocation samples place about 52–54% of sampled weight in `Object[]`,
with stacks originating from each part's `ArrayDeque` construction. Frames,
deque objects, cursors and `LinkedHashMap` iterators are also substantial sampled
sites in both controls. This supports bounded private per-body traversal reuse as
an allocation investigation, with independent replay cursors and explicit source
reference cleanup preserved. The roughly 560 kB measured bulk increase is
consistent with larger cursor/frame storage, but sampled weights are not exact
class B/op, retained heap, or an object-layout proof. Small-case sampled class
weights vary markedly and should not be used to decompose its 256 B/op increase.

Recordings and full stacks are in
`/home/agentuser/json-handler-baseline-evidence/{baseline,candidate-v2}-attribution-{small,bulk-10000}-reused/`.
Each directory preserves `recordings/.../profile.jfr`, `samples.json`,
`cpu-stacks.txt`, `cpu-top-lines.txt`, `attribution.txt` and runner metadata.
Commands, the allocation/stack summarizer and SHA-256 manifest are preserved as
`run-v2-attribution-profiles.sh`, `summarize-attribution.py` and
`v2-attribution-sha256.txt`. No host build/test ran alongside profiling; observed
host CPU pressure remained low. The profiling window was released to the kernel
agent for controlled attribution and correctness verification.

## V3a single-change linked-stack control

V3a changes only `Utf8JacksonWriteCursor`: its existing `Frame.parent` chain
becomes the traversal stack, replacing `ArrayDeque` with a top reference/depth
counter. Numeric state, frame construction and per-part cursor lifetime remain
unchanged. Thirty-one invariant tests and three complete-output setup cases pass.
The frozen JAR is `candidate-v3a-kernel-benchmarks.jar`, SHA-256
`8a45a289f0541b7180107d31c9726429f81c1076fb5b84359c0e3bcd53f70f7e`.
Manifest/source comparison confirms one changed runtime source; workload sources
are unchanged. Versioned runners hash each variant's archived sources.

Six adjacent reused synchronous output cases completed with three forks/eight
warmups/five measurements, followed by six separate one-fork GC profiles. This
is attribution evidence for one change, not final performance acceptance.

| Reused synchronous output | Current ops/s | V2 ops/s | V3a ops/s | V3a vs current |
| --- | ---: | ---: | ---: | ---: |
| Small | 3,636,329 | 3,232,812 | 3,528,462 | -3.0% |
| Bulk | 198.9 | 176.7 | 181.3 | -8.8% |

Small improves 9.1% over v2, but v3a fork CV is 5.9% versus current 2.1%/v2
0.5%; its favorable point estimate needs repetition. Bulk improves only 2.6%
over v2 and retains a material loss; fork CVs are 4.2%/3.6%/1.4% respectively.
The traversal change therefore does not resolve the output gate.

| Reused synchronous allocation | Current B/op | V2 B/op | V3a B/op |
| --- | ---: | ---: | ---: |
| Small | 528 | 648 | 568 |
| Bulk | 5,608,367 | 6,168,454 | 3,928,452 |

Deque removal saves about 2.24 MB per bulk operation versus v2 while leaving
throughput below current. This separates a substantial allocation cost from
remaining traversal/formatting execution costs. The independent numeric-state
control should start from frozen v2 to distinguish the axes before combining
any supported correction. No extra numeric Strings or encoded scratch are
introduced by v3a.

Raw directories are `{baseline,candidate-v2,candidate-v3a}-v3a-axis-{primary,allocation}-{small,bulk-10000}`
under the evidence directory, with `checkpoint2-v3a-summary.csv`, immutable source
archive/verification/JAR and `v3a-axis-sha256.txt`. All expected fork/iteration
counts, successful exits and artifact hashes were verified. Timing is released
to the kernel agent; no build/test ran alongside it.

## V3b independent primitive-formatting control

V3b starts from frozen v2, preserving deque traversal and per-part cursor lifetime.
Only the writer runtime source changes: queue the primitive value, format directly
into the caller's writable backing array when a conservative width bound fits,
and retain the twenty-character fallback for tiny/direct destinations. Primitive
state polling is removed from `prepare`. No numeric Strings/cache or additional
encoded scratch are introduced. Forty-one tests and three full reused synchronous
output setup cases pass. Frozen JAR SHA-256 is
`8cf09d94946875d344740f5549aea16faac4bd3173970034bab8c5def7d14908`.

Nine adjacent primary rows and nine separate GC rows completed using the same
protocol as v3a. The numeric control improves the remaining output losses in this
campaign, independently of the linked stack:

| Reused synchronous output | Current ops/s | V2 ops/s | V3b ops/s | V3b vs current |
| --- | ---: | ---: | ---: | ---: |
| Small | 3,580,009 | 3,278,750 | 3,523,588 | -1.6% |
| Bulk | 203.9 | 183.4 | 201.2 | -1.3% |
| Integer array | 3,914 | 4,055 | 5,076 | +29.7% |

V3b fork CVs are 2.0%/2.9%/2.9% for small/bulk/integers; current CVs are
3.7%/1.8%/0.2%. V3b improves small/bulk means 7.5%/9.7% over v2. These are
single-change attribution results, not combined-artifact or integrated acceptance.

| Reused synchronous allocation | Current B/op | V2 B/op | V3b B/op |
| --- | ---: | ---: | ---: |
| Small | 528 | 648 | 600 |
| Bulk | 5,608,367 | 6,168,451 | 6,328,449 |
| Integer array | 8,610 | 8,722 | 8,730 |

Bulk allocation remains 12.8% above current and adds about 160 kB versus v2,
consistent with an additional primitive field across 20,000 part cursors; this is
not a formal layout decomposition. The independently measured linked-stack
allocation reduction and numeric-path throughput recovery justify evaluating a
combined correction. Their gains cannot be assumed additive: the combined
artifact needs invariant verification, matched failed-row repeats, relevant
parsing checks and memory review before the early gate can be accepted.

Evidence uses `*-v3b-axis-*`, `checkpoint2-v3b-summary.csv` and
`v3b-axis-sha256.txt`, with immutable v3b JAR/manifest/source archive. Each expected
fork/iteration count, successful exit and artifact hash was verified. The first
v3b small primary row's runner referenced an absent archive manifest path; it
still hashed the verified runtime/workload sources and JAR. Its
`provenance-supplement.json` records the manifest verified before timing; subsequent
rows use the corrected path. Original metadata remains preserved. The timing
window is released to the kernel agent for a justified combined correction.

## Combined v3 confirmation

V3 combines the independently measured linked-stack and caller-array primitive
formatting changes. Runtime source comparison confirms only the writer changes
from v2; parser/workload sources are unchanged. Forty-one kernel tests, twenty
complete fresh/reused synchronous/incremental output setups and seventy-four
boundary tests pass. Frozen kernel JAR SHA-256 is
`7ffb35f1b34ee42f4b388cc96c4c69a8e001df2291348071a2a378d7c1d40fbc`.
The immutable JAR, source archive and verification are preserved in the evidence
directory; source/runtime hashes were verified before timing. Candidate v3 fixture
CSV matches current/historical controls byte-for-byte.

Thirty-three primary cases and eighteen separate GC cases completed. Primary
settings remain three forks/eight one-second warmups/five one-second measurements.
Matched current controls cover small/bulk fresh and reused writer paths, integer
output and Unicode parsing. Large-string output/scalar ingress sanity checks use
the original current controls. Small synchronous output and Unicode UTF-8 parsing
have reverse-order repeats, retaining six forks per implementation for those rows.
Each expected fork/iteration count, successful exit and artifact hash was verified.

| Matched output | Current ops/s | V3 ops/s | Delta |
| --- | ---: | ---: | ---: |
| Fresh small incremental | 1,724,035 | 1,785,501 | +3.6% |
| Fresh small synchronous, six-fork mean | 1,694,174 | 1,663,908 | -1.8% |
| Fresh bulk incremental | 206.6 | 203.2 | -1.6% |
| Fresh bulk synchronous | 204.7 | 202.3 | -1.2% |
| Reused small incremental | 3,819,674 | 3,771,930 | -1.3% |
| Reused small synchronous, six-fork mean | 3,585,426 | 3,569,160 | -0.5% |
| Reused bulk incremental | 196.7 | 204.5 | +4.0% |
| Reused bulk synchronous | 206.3 | 203.6 | -1.3% |
| Reused integer-array synchronous | 4,027 | 5,353 | +32.9% |

Fresh small synchronous pair deltas are -2.9%/-0.7%; reused pair deltas are
-0.03%/-0.9%. Small throughput remains variable: six-fork CV is 1.1% current/
5.8% v3 for fresh and 2.6%/4.4% reused. These repeats recover the earlier material
loss under the investigation convention; they do not prove a statistical upper
bound of 5% across deployments. No repeated loss over 5% remains on covered
corrected-kernel output rows.

Fresh Unicode output is 139,062 incremental/132,511 synchronous ops/s, within 1%
of the original current means. Scalar output is 2,434/2,420, improving 1.4–1.6%.
Unicode UTF-8 parsing has pair deltas -3.9%/-0.9%; six-fork means are 163,594
current/159,720 v3 (-2.4%), fork CV 2.2%/3.0%. Unicode String parsing is
316,737/321,374 (+1.5%). Scalar UTF-8 ingress is 1,331 ops/s, -0.7% against
the original current mean. Unchanged integer
parser/construction sources retain the prior v2 evidence rather than claiming
new v3 timings for them. Preserve the earlier v1 Unicode threshold uncertainty
and all batches as variability history.

| Matched allocation | Current B/op | V3 B/op |
| --- | ---: | ---: |
| Fresh small incremental / synchronous | 1,928 / 2,048 | 1,872 / 1,992 |
| Fresh Unicode incremental / synchronous | 1,672 / 5,584 | 1,560 / 5,472 |
| Fresh bulk incremental / synchronous | 5,615,832 / 5,624,094 | 4,091,984 / 4,100,248 |
| Reused small synchronous | 528 | 488 |
| Reused bulk synchronous | 5,608,368 | 4,088,448 |
| Reused integer-array synchronous | 8,610 | 8,618 |

Covered allocation no longer breaches the proposed 10% investigation threshold;
bulk decreases about 27%. The encoded six-byte cursor bound and fixed twenty-
character primitive fallback are covered by invariants including sliced storage,
primitive extremes, tiny/direct buffers, cycles, replay independence and failure/
cancellation cleanup. BigInteger/Decimal derived Strings and source-owned String
caches remain explicit costs; these integer fixtures do not remove that limitation.
Total B/op is not retained heap.

For historical context, v3 fresh Unicode/scalar synchronous output is about
132,511/2,420 ops/s versus historical Gson 34,210/469. V3 Unicode UTF-8 six-fork
mean is 159,720 versus historical 104,718. Those references preserve the older
production implementation and its different strictness, and are not substitutes
for matched current controls or integrated acceptance.

Evidence is `*v3*confirmation*`, `checkpoint2-v3-summary.csv`,
`candidate-v3-fixtures.csv` and `v3-confirmation-sha256.txt`; all prior versions
remain immutable. The timing window is released. The covered isolated kernel
gate supports proceeding to separate handler costs and limited integrated
verification; accessors, codecs, actual clients and deployment measurements below
still need evidence before broad migration acceptance.

## Focused v3 handler costs

The frozen boundary v3 JAR is
`candidate-v3-boundaries-benchmarks.jar`, SHA-256
`a4d096f7325cbf78f6cab571a69c9f1fc8d6b5d61188d4424e36b51f2d80b833`.
Its source archive/list and all measured-source hashes match the preserved
provenance. Eighteen primary cases use three forks/eight warmups/five measurements,
followed by eighteen separate one-fork/five-warmup/three-measurement GC cases.
Affinity, JVM/heap/GC and shading match the kernel campaign. Every expected
fork/iteration count, successful exit and artifact/source hash was verified.

This is boundary overhead, not a full-backend comparison. Small and 1 MiB-string
fixture texts hash identically to the frozen kernel controls. User trees/field
bindings are prepared outside timing; operations return a completed tree to the
same Blackhole. Trusted transfer includes full standard-node/cycle validation
and returns the borrowed tree. Gson import includes handler serialization and
kernel parsing; export includes validation, kernel serialization/full text
materialization and Gson parsing. Transformation includes request construction,
outer staging, callback and validation; Jackson copies once before the callback,
while Gson performs text export/import. These are the actual logical boundary
operations, without substituting a no-op for a consuming operation.

| Operation | Small Jackson ns/op | Small Gson ns/op | 1 MiB Jackson ns/op | 1 MiB Gson ms/op |
| --- | ---: | ---: | ---: | ---: |
| Import | 58.6 | 518 | 23.2 | 2.10 |
| Export | 57.9 | 1,074 | 22.7 | 1.71 |
| Transform | 303 | 1,967 | 128 | 3.86 |

Costs are reciprocal primary mean throughput, not latency distributions. Primary
fork CVs are 0.3–3.1%. The large fixture is a shallow object with one immutable
String: validation visits its nodes, and deep copy copies containers while sharing
immutable text. It therefore does not require scanning/copying a million
characters on the trusted path. The small fixture has more containers; these
numbers are specific to tree shape and should not be extrapolated to a large tree
of small values or expressed as backend speedup ratios.

| Allocation | Small Jackson B/op | Small Gson B/op | 1 MiB Jackson B/op | 1 MiB Gson B/op |
| --- | ---: | ---: | ---: | ---: |
| Import | 528 | 2,352 | 416 | 8,259,641 |
| Export | 528 | 6,264 | 416 | 10,456,517 |
| Transform | 2,360 | 9,408 | 1,616 | 18,716,983 |

Pure pre-copy costs are 57.8 ns/664 B for small and 14.1 ns/208 B for the shallow
large-string tree. The transform measurement includes other scope/validation and
mutation work; component costs are not assumed additive. Direct validation still
allocates transient traversal state in this experiment. Fallback allocation
includes real conversion buffers/Strings/trees; it is not the normal streaming
writer's encoded-scratch bound or retained heap.

Native value bridge checks use `[9007199254740993,null,{"nested":null}]`, with
the exact fixture hash preserved. Jackson/Gson indexing costs are 45.2/401 ns
and 472/1,944 B/op; projection costs are 45.4/824 ns and 472/5,584 B/op.
Both complete the same value conversion; these are not Elasticsearch indexing
rates. Setup checks exact value equality and nested-null preservation. Root-null
short-circuits, other numeric shapes and custom handlers retain correctness
evidence but are not claimed as timed here.

Raw evidence is `candidate-v3-handler-{primary,allocation}-{object,native,copy}`,
with `candidate-v3-handler-fixtures.csv`, `checkpoint2-v3-handler-summary.csv`,
`v3-handler-focused-sha256.txt` and the immutable-source runner/commands. The
timing window is released directly to both source agents and the root coordinator.
No build/test ran alongside measurement. Functional slice timing, actual native
field schema/codecs/clients and real server performance remain integrated work.

## Integrated production harness readiness — 2026-10-14

The main performance module now compiles against the installed native utility,
backend and client artifacts. The harness preserves the matched fixture text,
opaque full-output consumption, fresh whole-body construction/probe timing,
reused-body timing, feed sizes and numeric read sums. `ElasticsearchJsonBody`
now delegates to production `JsonBodies.ndjson`; actual migrated backend accessors
replace the removed utility accessors. Their new measurements can be production
accessor evidence; the earlier experimental accessor stand-in scores remain excluded.

Single-module `clean package` passes with server-dependent ITs skipped. Untimed
verification checks all five recorded fixture hashes, complete output, all three
numeric read sums and both accessors. Ordinary-classpath JMH setup/consumption
smokes pass 64 combinations (20 output, 15 ingress, three numeric, 12 String,
two accessor and 12 upstream numeric diagnostics); 465 bounded String scenarios
also pass. A forked one-row runner smoke passes with `gating=false`. These are
correctness/provenance checks; the accepted scope and unresolved timing gate are
reported separately below.

Frozen evidence is `/home/agentuser/json-handler-baseline-evidence/integrated-production`:
`verification.json`, `source.tar.gz`, `classpath-provenance.json`, `classpath.txt`,
`fixtures.csv`, `runtime-classes.txt`, build/smoke logs and raw smoke JSON.
The shaded artifact SHA-256 is
`8b0284a9f69ba5a996576570849b093e65641a42c96f88d729b92d0f2fda5a32`;
the ordinary benchmark artifact SHA-256 is
`1c2aa2cb99a0ae5201fd31cb5e3b3376b9ce119adf8b3c292cec3bbf3b8d2142`.
All 78 ordinary dependency JARs are copied outside Maven snapshot storage and
hashed. Runtime class provenance proves the production parser/cursor and backend
body adapter come from these frozen native JARs, and Core's `FastDoubleSwar`
loads its `META-INF/versions/23` class on the current JDK. Shaded and ordinary
classpath scores must remain separate.

A matched ordinary-classpath current control is frozen separately in
`integrated-baseline-runtime`. Its 75 JARs are reconstructed from original baseline
worktree artifacts and unchanged dependency JARs. Every base runtime class and
versioned helper class matches the corresponding class bytes in the immutable
`baseline-benchmarks.jar`; no class mismatches remain. New Databind/annotations
libraries and a class-free JUnit aggregator are omitted. The original baseline,
historical Gson and all v1/v2/v3 artifacts/results remain untouched. Its complete-output/accessor setup smoke and all primary forks subsequently pass;
benchmark class bytes and ordinary dependency hashes remain unchanged.

The historical `numbers-mixed-10000` bytes still contain unsupported exponent
`1e2147483648` and are verified to fail. A separately named
`numbers-mixed-supported-10000` replaces only that token with `1e1000` and records
its own input/output fixture hashes. It verifies mathematical values and complete
framing rather than original number spelling; it is not pooled with any old
mixed-fixture score. The five matched primary fixtures retain exact byte checks.

The runner accepts `--classpath FILE`, hashes each frozen dependency and records
its launch command. Backend source hashes are now included; storing that inventory
in `source-hashes.json` fixes the command-argument limit encountered when the full
backend inventory was passed to `jq` as one argument. Source/fixture changes after
artifact freeze require a new versioned build, not in-place replacement.

The integrated campaign followed this sequence after documentation, module-path
and local transport correctness builds finished and all agents stopped heavy work:

1. Verify the reconstructed baseline setup and fork launch. Run adjacent baseline
   and integrated small/bulk/integer-array reused synchronous output first, with
   three forks, eight one-second warmups, five one-second measurements, CPUs `0,2`,
   fixed 1 GiB G1 heap and full opaque consumption. Add separate representative GC
   profiles. This targets the previous architectural failure sites in about five
   minutes plus setup; short smokes are not acceptance data.
2. Expand to the matched 33 primary rows: four UTF-8 ingress, four String parse,
   three numeric read modes, 20 complete output rows across five fixtures and both
   lifecycles/writers, and two actual accessors. One implementation requires about
   22 timed minutes; a fresh ordinary-classpath pair requires about 45 minutes plus
   fork/setup overhead. Allocation profiling remains a separate representative
   campaign, initially one fork/five warmups/three measurements as in checkpoint 1;
   repeat any material allocation failure with three forks.
3. Investigate repeated throughput losses over 5% with reversed/interleaved matched
   controls before acceptance. Verify expected fork/iteration counts, full results,
   artifact/source/fixture hashes, runtime class loading and load/pressure metadata.
   Treat tree allocation separately from bounded encoded scratch. Unsupported
   numeric shapes remain rejection tests; retain structural buffer/cleanup hard gates.

These server-independent routes exercise actual integrated parser, NDJSON and
accessor APIs. They do not establish complete indexing/search throughput, handler
costs in real queries, schema-codec throughput or external Elasticsearch behavior.
Those remain separate integration measurements. The completed campaign and remaining
accessor gate follow; the timing window is released to the source agents.

## Integrated ordinary-classpath results

All 33 matched primary pairs complete with three forks, eight one-second warmups,
five one-second measurements, one thread, 1 GiB G1 heap and CPUs `0,2`. Each expected
fork/iteration count, successful exit, frozen classpath hash and source/fixture
hash is verified. The production thin JARs and baseline worktree JARs are measured
on ordinary classpaths with Multi-Release loading; these results are not pooled
with earlier shaded v3 scores. Fifteen separate allocation pairs use one fork,
five warmups and three measurements. No source/build/test work runs concurrently.
Host load is nonzero; CPU-pressure observations and per-fork data are retained,
and affected accessor rows are repeated in reverse order.

| Actual production route | Current control ops/s | Integrated ops/s | Delta |
| --- | ---: | ---: | ---: |
| UTF-8 Unicode ingress | 160,229 | 161,439 | +0.75% |
| UTF-8 1 MiB scalar ingress | 1,321 | 1,350 | +2.2% |
| UTF-8 integer-array ingress | 4,004 | 4,620 | +15.4% |
| String 1 MiB scalar parse | 704 | 682 | -3.1%, wider candidate interval |
| Numeric construction / no reads | 4,545 | 5,327 | +17.2% |
| Numeric selected reads | 4,522 | 5,292 | +17.0% |
| Numeric repeated reads | 2,800 | 3,405 | +21.6% |
| Reused small synchronous output | 3,382,405 | 3,684,154 | +8.9% |
| Fresh bulk synchronous output | 207.5 | 200.5 | -3.4% |
| Reused bulk synchronous output | 189.0 | 195.3 | +3.3% |
| Reused integer-array synchronous output | 4,024 | 5,465 | +35.8% |

All 31 parser/writer/numeric primary rows stay within the investigation threshold;
the largest loss is fresh bulk synchronous output at -3.36%, with broad baseline
interval (207.5 ±10.3 versus 200.5 ±3.8 ops/s, JMH 99.9% errors). A separate matched
Unicode 1 MiB character-String corroboration is 653.2 versus 649.5 ops/s (-0.57%),
closing the previous isolated v3 Unicode-String concern on ordinary classpaths.
The full row table is `primary-summary.csv`; no statistically stronger claim is
made for small near-parity differences.

| Allocation route | Current B/op | Integrated B/op | Delta |
| --- | ---: | ---: | ---: |
| UTF-8 integer-array ingress | 1,130,610 | 810,402 | -28.3% |
| Numeric construction / selected / repeated reads | about 1,130,058 | about 809,898 | -28.3% |
| Fresh small synchronous output | 2,048 | 1,992 | -2.7% |
| Reused small synchronous output | 528 | 488 | -7.6% |
| Fresh bulk synchronous output | 5,624,096 | 4,100,247 | -27.1% |
| Reused bulk synchronous output | 5,608,368 | 4,088,455 | -27.1% |
| Fresh integer-array synchronous output | 10,050 | 10,138 | +0.87% |
| Reused integer-array synchronous output | 8,610 | 8,618 | +0.09% |

The other representative allocation rows are near parity or lower; no material
allocation gate fails. This measures whole-operation allocation, separately from
the fixed encoded scratch, primitive character workspace, source tree storage and
large-number rendering described above. `allocation-summary.csv` retains all 15
pairs and errors. Existing production cleanup/buffer tests remain the structural
hard-gate evidence.

Actual accessor timing is the remaining investigation:

| Accessor | Initial delta | Reverse-order repeat ops/s, current → integrated | Repeat delta |
| --- | ---: | ---: | ---: |
| Strict nested numeric | -16.7%, large candidate fork variation | 54,793,475 → 51,984,859 | -5.13% |
| Singleton-array adapting numeric | -12.5%, tight interval | 26,550,862 → 23,131,585 | -12.88% |

The strict initial candidate forks are 32.6/50.7/54.9 million ops/s; the repeat
stabilizes at 51.7/51.7/52.6 million. Report both campaigns rather than discard the
outlier. Six-fork aggregate deltas are -11.0% strict and -12.7% adapting; the
stable reverse-order adapting loss establishes a real unresolved concern.
These are actual migrated accessors, not the earlier experimental stand-ins.

Read-only source comparison suggests two possible costs: all new `JsonElementTypes`
share one anonymous factory class with Predicate/Function dispatch, while the
control uses specialized implementations; numeric reads also repeat generic
scalar validation after accessor type/array checks. This is a hypothesis, not
CPU attribution. Profile or measure one axis before correction, preserving strict
type selection, null handling, explicit singleton-array adaptation, exact numeric
storage and sparse-decimal narrowing guards. Preserve the frozen integrated v1
artifacts; rebuild any correction into a separately versioned candidate.

Matched ordinary-classpath JFR profiles of the frozen control and integrated v1
now support a dispatch control. Worker execution/native samples put the old
specialized numeric getter at the top of 363/487 strict and 356/493 adapting
stacks. The migrated shared conversion `Function.apply` appears at the top of
197/490 strict and 258/490 adapting stacks; strict also records 197 String-equality
top frames. These sampled, heavily inlined stacks do not assign exact CPU fractions
or prove dispatch is the only cause. Both versions' sampled allocations are
`Optional`. Each profile uses one fork, eight warmups and five one-second
measurements and is diagnostic evidence only. Raw recordings, JSON samples,
summaries and hashes are under `accessor-correction/profile-{baseline,v1}` and
`accessor-correction/profile-provenance.json`. The one-axis control below replaces
the shared type factory with distinct type implementations while keeping the same
conversion methods and type-selection rules.

Raw evidence is `targeted-*`, `expanded-*`, `allocation-*`, `accessor-repeat-*`
and `unicode-string-*` under `integrated-production`, with `results-summary.json`,
`primary-summary.csv`, `allocation-summary.csv`, `evidence-sha256.txt` and
whole-tree/source/dependency provenance. The timing window is explicitly released
to root, scope and handler agents. The focused accessor correction is reported below;
its throughput investigation threshold does not itself determine release acceptance.
Historical Gson scores retain their separate dated,
shaded-control interpretation and are not relabeled as ordinary-classpath results.

## Bounded integrated accessor correction

The dispatch control replaces the shared captured Predicate/Function type factory
with distinct type classes. Numeric reads still call the same scalar conversion
helpers; their source is byte-identical. Clean backend installation passes 23
existing focused checks, and two new through-accessor tests pass for truncation,
64-bit wrapping, extreme decimal scales, strict types, nulls and singleton arrays.
The frozen backend JAR SHA-256 is
`df043f422093075bcc8db753174bc2af770d2b37a54d63b5aaa55b25b19b8395`.

`integrated-production-dispatch` contains a separate frozen ordinary classpath.
Its benchmark JAR and other 77 dependencies are byte-identical to integrated v1;
the backend class comparison changes only `JsonElementTypes` and its nested
classes. All 78 dependency hashes were verified after timing. Thus the actual
accessor workload, fixtures, setup verification and timing boundaries are unchanged.
The runner's shaded-JAR freshness check now applies only to shaded runs; supplied
frozen classpaths are independently checked and hashed.

Adjacent Search-tree baseline, frozen v1 and dispatch-control primary runs use
three forks, eight one-second warmups and five one-second measurements, ordinary
classpath, the same JDK/heap/affinity as above. Errors below are JMH's reported
99.9% intervals; none of the primary forks were discarded.

| Actual accessor | Search-tree baseline, million ops/s | Integrated v1 | Dispatch control | Control vs baseline | Control vs v1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Strict nested numeric | 55.243 ±0.216 | 50.254 ±2.176 | 50.920 ±5.544 | -7.83% | +1.32% |
| Singleton-array adapting numeric | 26.565 ±0.442 | 23.298 ±0.465 | 24.532 ±0.186 | -7.65% | +5.30% |

The adapting improvement is consistent across control forks (24.405/24.675/24.516
million ops/s), but a residual loss remains relative to the old Search-owned tree.
Strict control forks are 43.890/54.779/54.091 million; the wide interval does not
establish a strict-read improvement or a precise remaining loss. The two faster
forks are close to baseline, and the slow fork remains part of the reported result.
This is one bounded dispatch control, not a claim that all accessor costs are solved.

Separate reverse-order GC runs use one fork, five warmups and three measurements.
Strict reads allocate 32/32/48 B/op for baseline/v1/control; adapting reads allocate
64/48/48 B/op. The strict control therefore has a measured 16 B/op increase in this
diagnostic fork. Optional allocation and escape analysis are JIT-sensitive; these
one-fork allocation results do not establish a universal allocation bound.

Raw `dispatch-primary-*` and `dispatch-allocation-*`, complete fork means,
`dispatch-results-summary.json`, source/correctness manifests and
`dispatch-evidence-sha256.json` are under `accessor-correction`. Frozen integrated
v1, historical Gson and all isolated artifacts/results remain preserved. All builds
were stopped during profiling/timing; the timing window is now explicitly released
to the scope and handler agents for correctness and live Elasticsearch checks.
Under the user's clarified priorities these residual accessor costs are reported
tradeoffs, not automatic release blockers. No further accessor optimization or
broad timing campaign follows this control.

Final correctness changes after timing do not replace these measured artifacts.
At the initial final-source review against the frozen integrated v1 whole-tree
inventory, two main Java sources had changed: `JsonElementTypes` (the separately measured dispatch control above) and
`ElasticsearchRequest` (handler/token preflight when exporting even an empty
request body list). The final accessor source matches the measured control hash.
The greater-than-1000-digit boundary gap adds documentation and tests for existing
behavior; parser, numeric helper and handler bridge implementations are unchanged.
Both frozen runtime inventories still verify all 78 dependency hashes.
`accessor-correction/final-source-review.json` records this comparison. The latest
backend correctness install, 26 focused boundary checks and 44 live Elasticsearch
documentation tests are separate correctness evidence; this report makes no new
performance claim for those later artifacts or the export preflight.

A subsequent final review also corrected `Utf8JsonWriteCursor`: direct output
SPI calls now reject malformed raw container member names and Java-null/non-node
values while traversing, rather than producing invalid output or omitting array
items. Three new regression tests and 81 focused checks pass, including failure
cleanup and NDJSON replay. This additional writer guard was not benchmarked;
the frozen performance results describe the earlier writer artifact, not this
final guard. No new throughput claim is made for the final source tree.

## Limits

Both frozen controls and candidate shaded manifests omit `Multi-Release: true`.
Jackson Core provides newer-JDK numeric helper implementations; current shading
may not exercise those used by unshaded deployment. Preserve parity for this
campaign and corroborate critical integrated rows on matched deployment classpaths
or uniformly corrected artifacts before making deployment-wide claims.

Supported mixed decimals/large integers and fragment/buffer variants now have
integrated harness setup evidence. Actual accessor/generated-codec/client correctness
has component/reactor evidence. Integrated parser/writer/numeric performance now
has ordinary-classpath evidence, while accessor residual costs, schema-codec performance
and complete backend throughput remain separate work. Unsupported extreme exponents are excluded from successful timing.
Java 17 target compilation is not Java 17 runtime verification. Optional handler
and functional slice experiments measure explicit boundary overhead/stand-in flows;
they are separate from this primary kernel comparison.

## Review cleanup: scalar codec dispatch comparison (2026-10-14)

A bounded comparison on `d3a21e558f` tested the current captured `Function`
implementation against a single scalar codec class with a private enum and
switches. The workload uses the actual generated `PropertyMapping` and
`IndexSettings` codecs, including nested mapping properties, mixed scalar kinds,
and analysis lists/maps. Encoding and decoding are measured separately with
preconstructed trees/models, excluding parsing and output. The candidate changes
only scalar dispatch; both variants retain identical collection/copy code and
share the same generated codec classes and dependency runtime.

JMH 1.37 ran two fresh JVM forks per variant/workload, three 500 ms warmup and
four 500 ms measurement iterations, `-Xms256m -Xmx256m`, with the GC profiler.
No builds ran concurrently. Results are throughput in millions of operations/s;
errors are JMH's reported 99.9% confidence interval half-widths.

| Generated codec / operation | Captured functions | Enum switch | Mean change |
| --- | ---: | ---: | ---: |
| PropertyMapping encode | 3.337 ± 0.137 | 3.501 ± 0.075 | +4.9% |
| PropertyMapping decode | 8.896 ± 0.262 | 8.925 ± 0.444 | +0.3% |
| IndexSettings encode | 5.019 ± 0.102 | 5.077 ± 0.077 | +1.2% |
| IndexSettings decode | 7.657 ± 0.142 | 7.693 ± 0.220 | +0.5% |

Intervals overlap in all four comparisons. Encoding allocations are unchanged
(1496 B/op for PropertyMapping, 1968 B/op for IndexSettings). Decoding allocation
estimates vary across forks and show no improvement. These short local results
do not establish a reliable performance gain, so the captured scalar functions
are retained. They do not support any general speed claim about either design.

The harness, complete candidate source, runtime/source provenance, raw JMH JSON,
logs and Jackson numeric-node bytecode inspection are saved separately under
`/home/agentuser/json-handler-review-evidence/scalar-dispatch/`; frozen evidence
under `json-handler-baseline-evidence` was not changed. The first property setup
check incorrectly compared DecimalNode input with DoubleNode codec output by
node equality and failed before timing; the corrected property runs compare
strict text round-trips before measurement and are the reported results.

Independent bytecode inspection of Jackson Databind 3.2.2 confirms that standard
numeric nodes' `asString()` delegates to their primitive or underlying exact
number rendering. The cleanup uses that API after retaining supported-class,
finite-number and decimal-range checks; the output cursor's int/long fast paths
are unchanged. No performance claim is made for the other review cleanups.
