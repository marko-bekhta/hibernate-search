# Numeric UTF-8 evidence

See the [focused report](../elasticsearch-async-json-numeric-utf8.md). All campaigns
ran serially, pinned to CPUs 0,2, with recorded in-fork affinity, source patches/
hashes, artifact identity and exact commands. Each directory preserves original
runner output; copied directory labels describe the eventual role of a campaign.
The original temporary output paths in metadata are intentionally unchanged.

Primary controls use three forks/eight one-second warmups/five measurements.
Profiles, token/known-shape diagnostics and short probes are separately labeled.
The runner's `gating` boolean checks fork/iteration/profiler configuration; it does
not turn a token-only benchmark into a complete-tree gate. All retained-code
primary and final profiles/diagnostics share artifact SHA-256
`cf2ac6ff22977d305eb372aff85ea469448fb6a68a3b9b2e2197c64856d18001`.

[scores.txt](scores.txt) summarizes all scores, per-fork means and normalized
allocation where present. Raw JSON remains authoritative. The final JFR analyzer sorts tied counts/stacks
explicitly; this post-campaign formatting correction changes ordering only and
is independent of the benchmark JAR. Recorded patches/hashes retain its earlier
revision; production and benchmark Java hashes match the retained campaigns.

| Campaign | Role | Evidence |
| --- | --- | --- |
| baseline | Initial three-fork complete-tree integer control; ingress harness before bufferKind. | [raw](baseline/jmh.json) · [command](baseline/command.txt) · [environment](baseline/environment.json) · [source](baseline/source.patch) · [console](baseline/console.log) |
| feeder-diagnostic | Exploratory one-fork upstream tokens / validated numbers / known-shape tree. | [raw](feeder-diagnostic/jmh.json) · [command](feeder-diagnostic/command.txt) · [environment](feeder-diagnostic/environment.json) · [source](feeder-diagnostic/source.patch) · [console](feeder-diagnostic/console.log) |
| heap-access-probe | One-fork heap-only candidate probe; exploratory. | [raw](heap-access-probe/jmh.json) · [command](heap-access-probe/command.txt) · [environment](heap-access-probe/environment.json) · [source](heap-access-probe/source.patch) · [console](heap-access-probe/console.log) |
| heap-access-jfr | Separate JFR heap-only profile; production sources match retained code; earlier ingress harness. | [raw](heap-access-jfr/jmh.json) · [command](heap-access-jfr/command.txt) · [environment](heap-access-jfr/environment.json) · [source](heap-access-jfr/source.patch) · [console](heap-access-jfr/console.log) |
| combined-probe | One-fork heap-access + typed-frame probe; rejected combined implementation. | [raw](combined-probe/jmh.json) · [command](combined-probe/command.txt) · [environment](combined-probe/environment.json) · [source](combined-probe/source.patch) · [console](combined-probe/console.log) |
| ascii-probe | One-fork combined + bulk-ASCII validator experiment; validator change rejected. | [raw](ascii-probe/jmh.json) · [command](ascii-probe/command.txt) · [environment](ascii-probe/environment.json) · [source](ascii-probe/source.patch) · [console](ascii-probe/console.log) |
| fallback-before | Original production, final ingress harness; three-fork direct/read-only controls. | [raw](fallback-before/jmh.json) · [command](fallback-before/command.txt) · [environment](fallback-before/environment.json) · [source](fallback-before/source.patch) · [console](fallback-before/console.log) |
| combined-primary | Three-fork combined candidate complete-tree integer/mixed/Unicode comparison; not retained. | [raw](combined-primary/jmh.json) · [command](combined-primary/command.txt) · [environment](combined-primary/environment.json) · [source](combined-primary/source.patch) · [console](combined-primary/console.log) |
| combined-fallback | Three-fork combined candidate direct/read-only controls; not retained. | [raw](combined-fallback/jmh.json) · [command](combined-fallback/command.txt) · [environment](combined-fallback/environment.json) · [source](combined-fallback/source.patch) · [console](combined-fallback/console.log) |
| typed-frame-primary | Three-fork typed-frame-only heap/direct/read-only controls; change rejected. | [raw](typed-frame-primary/jmh.json) · [command](typed-frame-primary/command.txt) · [environment](typed-frame-primary/environment.json) · [source](typed-frame-primary/source.patch) · [console](typed-frame-primary/console.log) |
| matched-baseline | Original production with final ingress harness; three-fork heap/direct reference. | [raw](matched-baseline/jmh.json) · [command](matched-baseline/command.txt) · [environment](matched-baseline/environment.json) · [source](matched-baseline/source.patch) · [console](matched-baseline/console.log) |
| heap-access-primary | Retained heap-only production; three-fork complete-tree integer/mixed comparison. | [raw](heap-access-primary/jmh.json) · [command](heap-access-primary/command.txt) · [environment](heap-access-primary/environment.json) · [source](heap-access-primary/source.patch) · [console](heap-access-primary/console.log) |
| heap-access-direct | Retained heap-only production; three-fork direct control. | [raw](heap-access-direct/jmh.json) · [command](heap-access-direct/command.txt) · [environment](heap-access-direct/environment.json) · [source](heap-access-direct/source.patch) · [console](heap-access-direct/console.log) |
| upstream-tokens | Three-fork same-final-artifact Core token controls; diagnostic despite fork settings. | [raw](upstream-tokens/jmh.json) · [command](upstream-tokens/command.txt) · [environment](upstream-tokens/environment.json) · [source](upstream-tokens/source.patch) · [console](upstream-tokens/console.log) |
| allocation | Separate one-fork complete-tree integer/mixed GC allocation profile. | [raw](allocation/jmh.json) · [command](allocation/command.txt) · [environment](allocation/environment.json) · [source](allocation/source.patch) · [console](allocation/console.log) |
| decomposition-allocation | Separate one-fork Core token / number / known-shape tree GC profile. | [raw](decomposition-allocation/jmh.json) · [command](decomposition-allocation/command.txt) · [environment](decomposition-allocation/environment.json) · [source](decomposition-allocation/source.patch) · [console](decomposition-allocation/console.log) |
| owned-lexemes-allocation | Separate one-fork already-owned-lexeme tree construction GC profile. | [raw](owned-lexemes-allocation/jmh.json) · [command](owned-lexemes-allocation/command.txt) · [environment](owned-lexemes-allocation/environment.json) · [source](owned-lexemes-allocation/source.patch) · [console](owned-lexemes-allocation/console.log) |
| fragment-smoke | One-fork/one-warmup mixed-number 1/3/7-byte correctness/harness smoke; not gating. | [raw](fragment-smoke/jmh.json) · [command](fragment-smoke/command.txt) · [environment](fragment-smoke/environment.json) · [source](fragment-smoke/source.patch) · [console](fragment-smoke/console.log) |
| unicode-control | Retained production three-fork Unicode complete-tree guard. | [raw](unicode-control/jmh.json) · [command](unicode-control/command.txt) · [environment](unicode-control/environment.json) · [source](unicode-control/source.patch) · [console](unicode-control/console.log) |

JFR evidence: [text stacks](heap-access-jfr/stacks.txt),
[recording summary](heap-access-jfr/recording-summary.txt),
[recording hash](heap-access-jfr/recording-sha256.txt),
[extraction commands](heap-access-jfr/extraction-command.txt). Counts are diagnostic,
not exact CPU fractions. No JFR recording or other binary build artifact is committed.

Verification logs: [105 JSON tests/module install](json-tests.log),
[eight backend framing tests/module install](framing-tests.log),
[final benchmark package](benchmark-build.log),
[initial clean scoped reactor](initial-clean-reactor.log). The initial attempt to
install without cleaning hit an already-modular JAR and was corrected to the clean
scoped reactor retained here. A fixture-local variable naming compile error was
corrected before the final benchmark package.
