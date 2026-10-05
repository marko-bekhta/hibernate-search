# Owned-String UTF-8 experiment evidence

See the [report](../elasticsearch-async-json-string-utf8.md). Raw JMH JSON retains
all iteration samples, exact commands, complete console output, source patches,
source/artifact hashes and measurement environment. Primary and allocation runs
use the same benchmark artifact; earlier smokes have their own recorded identity.
`gating` records runner controls, not universal performance acceptance.
Only trailing whitespace in logs and final blank lines in JSON are normalized;
measurement values and source patches are unchanged.

| Run | Purpose | Evidence |
| --- | --- | --- |
| primary | Complete String-to-tree operations, four fixtures, three forks, eight warmups/five measurements. | [raw](primary/jmh.json) · [command](primary/command.txt) · [environment](primary/environment.json) · [source](primary/source.patch) · [console](primary/console.log) |
| allocation | Separate one-fork five-warmup/three-measurement GC and encoding-only diagnostics; non-gating. | [raw](allocation/jmh.json) · [command](allocation/command.txt) · [environment](allocation/environment.json) · [source](allocation/source.patch) · [console](allocation/console.log) |
| wrapped-smoke | Historical standard String-backed bounded encoder; one-fork/one-iteration non-gating smoke. | [raw](wrapped-smoke/jmh.json) · [command](wrapped-smoke/command.txt) · [environment](wrapped-smoke/environment.json) · [source](wrapped-smoke/source.patch) · [console](wrapped-smoke/console.log) |
| staged-smoke | Focused standard encoder with fixed array-backed character staging; one-fork/one-iteration non-gating smoke. | [raw](staged-smoke/jmh.json) · [command](staged-smoke/command.txt) · [environment](staged-smoke/environment.json) · [source](staged-smoke/source.patch) · [console](staged-smoke/console.log) |

[Primary scores and independent fork means](primary-summary.csv) ·
[Separate diagnostic/allocation scores](allocation-summary.csv) ·
[Standalone boundary/limit/cleanup verification](verification.log) ·
[Focused 102-test JSON verification](json-tests.log) ·
[Clean reactor benchmark build](reactor-benchmark-build.log) ·
[Final benchmark package/style/forbidden-API build](benchmark-build.log) ·
[Generated harness setup and helper source inspection](source-checks.log).

No binary benchmark artifact or profiler recording is committed. The source
patches plus base revision reconstruct each measured artifact's source; environment
files retain its SHA-256 identity. No production parser change was adopted.
