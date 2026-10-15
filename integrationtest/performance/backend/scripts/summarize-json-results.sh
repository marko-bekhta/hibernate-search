#!/usr/bin/env bash
# Print primary scores and independent fork means; preserve original JMH uncertainty.
set -euo pipefail
if (($# == 0)) || [[ $1 == --help || $1 == -h ]]; then
    printf 'Usage: summarize-json-results.sh RESULTS.json [RESULTS.json ...]\n'
    if (($# == 0)); then exit 1; else exit 0; fi
fi
command -v jq >/dev/null || { printf 'Missing required command: jq\n' >&2; exit 1; }
printf 'run,benchmark,fixture,lifecycle,observation,score,jmh_99.9_error,units,fork_means,fork_cv_percent,allocation_B_per_op\n'
for path in "$@"; do
    run=$(basename -- "$(dirname -- "$path")")
    jq -r --arg run "$run" '
        .[] | .primaryMetric as $metric |
        [($metric.rawData // [])[] | add / length] as $means |
        ($means | length) as $n |
        (if $n > 0 then ($means | add) / $n else 0 end) as $mean |
        (if $n > 1 and $mean != 0 then
            ([$means[] | (. - $mean) | . * .] | add / ($n - 1) | sqrt) / $mean * 100
         else "nan" end) as $cv |
        [$run, (.benchmark | split(".") | last), (.params.fixture // ""),
         (.params.lifecycle // ""), (.params.observation // ""),
         $metric.score, $metric.scoreError, $metric.scoreUnit,
         ($means | map(tostring) | join(";")), $cv,
         (.secondaryMetrics["gc.alloc.rate.norm"].score // "")] | @csv
    ' "$path"
done
