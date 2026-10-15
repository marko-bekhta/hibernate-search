#!/usr/bin/env bash
# Summarize jfr print --json samples; counts are not exact CPU fractions.
set -euo pipefail
usage() { printf 'Usage: summarize-json-jfr.sh SAMPLES.json [--thread SUBSTRING]\n'; }
thread=searchParser-jmh-worker
samples=
while (($#)); do
    case $1 in
        -h|--help) usage; exit 0 ;;
        --thread) (($# >= 2)) || { usage >&2; exit 1; }; thread=$2; shift 2 ;;
        --*) usage >&2; exit 1 ;;
        *) [[ -z $samples ]] || { usage >&2; exit 1; }; samples=$1; shift ;;
    esac
done
[[ -n $samples ]] || { usage >&2; exit 1; }
command -v jq >/dev/null || { printf 'Missing required command: jq\n' >&2; exit 1; }
digest=$(sha256sum < "$samples")
printf 'Execution/native samples; diagnostic counts, not exact CPU fractions.\nInput: %s\nInput SHA-256: %s\nThread substring: %s\n' "$samples" "${digest%% *}" "$thread"
jq -r --arg thread "$thread" '
    def counts: group_by(.) | map({name: .[0], count: length}) | sort_by([-.count, .name]);
    def countline: (.count | tostring) as $count | (" " * ([0, 5 - ($count | length)] | max)) + $count + " " + .name;
    .recording.events as $events |
    [$events[] | select(.type == "jdk.ExecutionSample" or .type == "jdk.NativeMethodSample") |
        .values | select(((.sampledThread // .eventThread // {}).javaName // "") | contains($thread)) |
        {truncated: (.stackTrace.truncated // false),
         names: [(.stackTrace.frames // [])[] | (.method.type.name | gsub("/"; ".")) + "." + .method.name]}
    ] as $samples |
    [$samples[] | select(.names | length > 0)] as $selected |
    "Total events: \($events | length); selected stacks: \($selected | length); empty: \(($samples | length) - ($selected | length)); truncated: \([$selected[] | select(.truncated)] | length)",
    "\nTop frame", ([$selected[].names[0]] | counts[] | countline),
    "\nInclusive (one count per frame name per sample)",
    ([$selected[] | .names | unique[]] | counts[] | countline),
    "\nAll unique selected stacks (count followed by full frame names)",
    ([$selected[].names] | counts[] | "\n\(.count) samples", (.name[] | "  " + .))
' "$samples"
