#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright Red Hat Inc. and Hibernate Authors
# Linux-only controlled runner. Never changes host settings or downloads executables.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../../.." && pwd)
JAR=$ROOT/integrationtest/performance/backend/elasticsearch/target/benchmarks.jar
invocation=$(printf '%q ' "$0" "$@")
fail() { printf '%s\n' "$*" >&2; exit 1; }
usage() {
    cat <<'HELP'
Usage: run-json-benchmarks.sh --out DIRECTORY [options]
  --cores LIST       Allowed online CPUs, e.g. 0,2 (default: two physical cores)
  --skip-build       Use an existing benchmark JAR, checking source freshness
  --forks N          Independent forks (default: 3)
  --warmup N         Warmup iterations (default: 5)
  --iterations N     Measurement iterations (default: 5)
  --seconds N        Seconds per iteration (default: 1)
  --quick            Non-gating: one fork, one warmup/measurement
  --cold             Non-gating: one un-warmed single-shot invocation per fork
  --pattern REGEX    JMH benchmark selection
  --fixtures LIST    Comma-separated fixtures; empty disables fixture/lifecycle parameters
  --lifecycle MODE   fresh (default) or reused
  --param NAME=VALUE Additional JMH parameter (repeatable)
  --profiler SPEC    Optional JMH profiler; separate diagnostic run
  --jvm-args ARGS    Additional fork JVM arguments (recorded verbatim)
  -h, --help         Show this help
Requires Linux, Bash 4+, jq, taskset, Java and GNU core utilities.
HELP
}
out= cores_spec= profiler= jvm_args=
forks=3 warmup=5 iterations=5 seconds=1
skip_build=false quick=false cold=false
pattern='JsonComparisonBenchmark.(gson|jackson|synchronousCursor|incrementalCursor|boundedWorker)$'
fixtures=small,mapping,unicode,nested,scalar-1m,scalar-8m,bulk-10000
lifecycle=fresh
params=()
while (($#)); do
    case $1 in
        -h|--help) usage; exit 0 ;;
        --skip-build) skip_build=true; shift; continue ;;
        --quick) quick=true; shift; continue ;;
        --cold) cold=true; shift; continue ;;
        --out|--cores|--forks|--warmup|--iterations|--seconds|--pattern|--fixtures|--lifecycle|--param|--profiler|--jvm-args)
            (($# >= 2)) || fail "$1 requires a value"
            case $1 in
                --out) out=$2 ;; --cores) cores_spec=$2 ;;
                --forks|--warmup|--iterations|--seconds)
                    [[ $2 =~ ^[0-9]+$ && $2 =~ [1-9] ]] || fail "$1 must be positive"
                    printf -v "${1#--}" '%d' "$((10#$2))" ;;
                --pattern) pattern=$2 ;; --fixtures) fixtures=$2 ;;
                --lifecycle) lifecycle=$2 ;;
                --param) [[ $2 =~ ^[a-zA-Z_][a-zA-Z_0-9]*=.+$ ]] || fail '--param requires a nonempty name=value'; params+=("$2") ;;
                --profiler) profiler=$2 ;; --jvm-args) jvm_args=$2 ;;
            esac
            shift 2 ;;
        *) fail "Unknown argument: $1" ;;
    esac
done
[[ -n $out ]] || fail '--out is required'
[[ $lifecycle == fresh || $lifecycle == reused ]] || fail '--lifecycle must be fresh or reused'
[[ $quick != true || $cold != true ]] || fail '--quick and --cold are mutually exclusive'
[[ $(uname -s) == Linux ]] || fail 'Linux is required for affinity verification'
for tool in jq taskset java sha256sum lscpu; do command -v "$tool" >/dev/null || fail "Missing required command: $tool"; done
read_value() { if [[ -r $1 ]]; then cat -- "$1"; else printf unavailable; fi; }
capture() { "$@" 2>&1 || :; }
# Canonicalize CPU lists, rejecting malformed or reversed ranges.
expand_cores() {
    local spec=$1 component low high cpu
    local -a components
    [[ $spec =~ ^[0-9]+(-[0-9]+)?(,[0-9]+(-[0-9]+)?)*$ ]] || fail "Invalid CPU selection: $spec"
    IFS=, read -r -a components <<< "$spec"
    for component in "${components[@]}"; do
        low=${component%-*}; high=${component#*-}
        low=$((10#$low)); high=$((10#$high))
        ((low <= high)) || fail 'Reversed CPU range'
        for ((cpu=low; cpu<=high; cpu++)); do printf '%s\n' "$cpu"; done
    done | sort -nu | jq -s .
}
allowed=$(expand_cores "$(sed -n 's/^Cpus_allowed_list:[[:space:]]*//p' /proc/self/status)")
online=$(expand_cores "$(cat /sys/devices/system/cpu/online)")
eligible=$(jq -n --argjson allowed "$allowed" --argjson online "$online" '$allowed - ($allowed - $online)')
topology=$(
    while read -r cpu; do
        base=/sys/devices/system/cpu/cpu$cpu
        jq -n --argjson cpu "$cpu" \
            --arg socket "$(read_value "$base/topology/physical_package_id")" \
            --arg core "$(read_value "$base/topology/core_id")" \
            --arg siblings "$(read_value "$base/topology/thread_siblings_list")" \
            --arg core_type "$(read_value "$base/topology/core_type")" \
            --arg max_frequency "$(read_value "$base/cpufreq/cpuinfo_max_freq")" \
            --arg base_frequency "$(read_value "$base/cpufreq/base_frequency")" \
            --arg governor "$(read_value "$base/cpufreq/scaling_governor")" \
            --arg current_frequency "$(read_value "$base/cpufreq/scaling_cur_freq")" '$ARGS.named'
    done < <(jq -r '.[]' <<< "$eligible")
)
topology=$(jq -s . <<< "$topology")
if [[ -n $cores_spec ]]; then
    selected=$(expand_cores "$cores_spec")
else
    selected=$(jq '[unique_by([.socket, .core]) | sort_by(.cpu) | group_by([.core_type, .max_frequency])[]] | sort_by([-length, .[0].cpu]) | (.[0] // []) | map(.cpu) | .[:2] | sort' <<< "$topology")
fi
jq -en --argjson selected "$selected" --argjson eligible "$eligible" '$selected | length > 0 and (($selected - $eligible) | length == 0)' >/dev/null || fail 'Selected CPUs must be online and in process affinity'
count=$(jq length <<< "$selected")
[[ $count -ge 2 || $pattern != *boundedWorker* ]] || fail 'Worker comparison requires at least two allowed CPUs'
[[ ! -e $out ]] || fail "Output directory already exists: $out"
mkdir -p -- "$(dirname -- "$out")"
mkdir -- "$out"
out=$(cd -- "$out" && pwd)
cd -- "$ROOT"
if [[ $skip_build == false ]]; then
    "$ROOT/mvnw" clean install -pl integrationtest/performance/backend/elasticsearch -am -DskipTests -DskipITs -Dno-build-cache > "$out/build.log" 2>&1
fi
[[ -f $JAR ]] || fail 'Missing benchmark JAR; build first or omit --skip-build'
if [[ $skip_build == true ]]; then
    newer=$(find util/json/src/main backend/elasticsearch/src/main integrationtest/performance/backend/elasticsearch/src/main -type f -name '*.java' -newer "$JAR" -print -quit)
    [[ -z $newer ]] || fail 'Benchmark JAR predates source changes; rebuild before --skip-build'
fi
cpu_list=$(jq -r 'map(tostring) | join(",")' <<< "$selected")
affinity=$(taskset -c "$cpu_list" sh -c "sed -n 's/^Cpus_allowed_list:[[:space:]]*//p' /proc/self/status")
[[ -n $affinity ]] || fail 'Could not verify CPU affinity'
jvm="-Xms1g -Xmx1g -XX:+UseG1GC -XX:ActiveProcessorCount=$count -Djson.benchmark.cores=$affinity"
[[ -z $jvm_args ]] || jvm+=" $jvm_args"
if [[ $quick == true ]]; then forks=1 warmup=1 iterations=1; fi
if [[ $cold == true ]]; then warmup=0 iterations=1; fi
command=(taskset -c "$affinity" java -jar "$JAR" "$pattern" -f "$forks" -wi "$warmup" -i "$iterations" -w "${seconds}s" -r "${seconds}s" -t 1 -jvmArgs "$jvm" -rf json -rff "$out/jmh.json")
[[ $cold == false ]] || command+=(-bm ss)
[[ -z $fixtures ]] || command+=(-p "fixture=$fixtures" -p "lifecycle=$lifecycle")
for parameter in "${params[@]}"; do command+=(-p "$parameter"); done
[[ -z $profiler ]] || command+=(-prof "$profiler")
git diff HEAD --binary > "$out/source.patch"
source_dirs=(integrationtest/performance/backend/elasticsearch/src integrationtest/performance/backend/scripts util/json/src)
while IFS= read -r -d '' source; do
    git diff --no-index --binary -- /dev/null "$source" >> "$out/source.patch" || [[ $? == 1 ]]
done < <(git ls-files --others --exclude-standard -z -- "${source_dirs[@]}")
hash() { local digest; digest=$(sha256sum < "$1"); printf '%s' "${digest%% *}"; }
source_hashes=$(
    while IFS= read -r -d '' source; do
        jq -n --arg path "$source" --arg hash "$(hash "$source")" '{key: $path, value: $hash}'
    done < <(find "${source_dirs[@]}" -type f -print0)
)
source_hashes=$(jq -s from_entries <<< "$source_hashes")
gating=false
if ((forks >= 3 && warmup >= 5 && iterations >= 5)) && [[ $quick == false && $cold == false && -z $profiler ]]; then gating=true; fi
jq -n --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --argjson gating "$gating" \
    --argjson command "$(printf '%s\0' "${command[@]}" | jq -Rs 'split("\u0000")[:-1]')" \
    --arg invocation "$invocation" --arg jvm "$jvm" --arg revision "$(git rev-parse HEAD)" \
    --arg status "$(git status --short)" --arg patch_sha256 "$(hash "$out/source.patch")" \
    --arg artifact_sha256 "$(hash "$JAR")" --arg java "$(capture java -version)" \
    --arg uname "$(uname -a)" --arg cpu "$(capture lscpu)" --argjson topology "$topology" \
    --argjson allowed "$allowed" --argjson selected "$selected" \
    --arg cgroup "$(read_value /proc/self/cgroup)" --arg cpu_max "$(read_value /sys/fs/cgroup/cpu.max)" \
    --arg cpu_stat_before "$(read_value /sys/fs/cgroup/cpu.stat)" --arg memory_max "$(read_value /sys/fs/cgroup/memory.max)" \
    --arg cpuset "$(read_value /sys/fs/cgroup/cpuset.cpus.effective)" --arg load_before "$(read_value /proc/loadavg)" \
    --arg pressure_before "$(read_value /proc/pressure/cpu)" --arg turbo "$(read_value /sys/devices/system/cpu/intel_pstate/no_turbo)" \
    --arg boost "$(read_value /sys/devices/system/cpu/cpufreq/boost)" --arg perf_event_paranoid "$(read_value /proc/sys/kernel/perf_event_paranoid)" \
    --argjson exclusive_reservation false --argjson source_hashes "$source_hashes" '$ARGS.named' > "$out/environment.json"
printf '%q ' "${command[@]}" | tee "$out/command.txt"
printf '\n' | tee -a "$out/command.txt"
exit_code=0
"${command[@]}" > "$out/console.log" 2>&1 || exit_code=$?
jq --arg load_after "$(read_value /proc/loadavg)" --arg pressure_after "$(read_value /proc/pressure/cpu)" \
    --arg cpu_stat_after "$(read_value /sys/fs/cgroup/cpu.stat)" --argjson exit_code "$exit_code" \
    '. + $ARGS.named' "$out/environment.json" > "$out/environment.json.tmp"
mv -- "$out/environment.json.tmp" "$out/environment.json"
# JMH can exit zero when individual forks fail.
if ((exit_code != 0)) || ! jq -e 'type == "array" and length > 0' "$out/jmh.json" >/dev/null || grep -Eq '<failure>|ERROR:' "$out/console.log"; then
    fail 'Benchmark failed; inspect console.log'
fi
printf '%s results in %s\n' "$(jq length "$out/jmh.json")" "$out"
