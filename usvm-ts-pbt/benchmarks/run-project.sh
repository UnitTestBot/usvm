#!/usr/bin/env bash
# End-to-end measurement pipeline for one open-source TypeScript project.
#
#   ./run-project.sh <git-url | local-path | corpus-name> [options]
#
# Does everything: clone (if needed) -> build ts-frontend (if needed) ->
# run the hybrid analyzer in all requested ablation modes -> aggregate ->
# write reports and a summary table under results/<name>/.
#
# Examples:
#   ./run-project.sh https://github.com/TheAlgorithms/TypeScript.git --include maths
#   ./run-project.sh TheAlgorithms-TypeScript --include sorts --modes PBT_ONLY,HYBRID
#   ./run-project.sh ~/my/local/ts-project
#
# Options:
#   --include <subdir>       analyze only this subdirectory of the project (repeatable)
#   --modes <M1,M2,...>      ablation modes (default: PBT_ONLY,SYMBOLIC_ONLY,HYBRID,HYBRID_WITH_HINTS)
#   --pbt-iterations <n>     PBT budget per method (default: 1000)
#   --target-timeout <sec>   per-target symbolic timeout (default: 10)
#   --max-files <n>          cap the number of files (default: unlimited)
#   --seed <n>               PBT seed (default: 0)
#   --commit <sha>           checkout this commit after cloning (reproducibility)
#
# Environment (auto-detected when possible):
#   JACODB_DIR         jacodb checkout with jacodb-ets/ts-frontend (default: ~/Programming/jacodb)
#   ETS_FRONTEND_DIR   built ts-frontend (default: $JACODB_DIR/jacodb-ets/ts-frontend)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CORPUS_DIR="$SCRIPT_DIR/corpus"
RESULTS_DIR="$SCRIPT_DIR/results"

# ---------------------------------------------------------------- arguments
[[ $# -ge 1 ]] || { grep '^#' "$0" | head -30; exit 1; }
TARGET="$1"; shift

INCLUDES=()
MODES="PBT_ONLY,SYMBOLIC_ONLY,HYBRID,HYBRID_WITH_HINTS"
PBT_ITERATIONS=1000
TARGET_TIMEOUT=10
MAX_FILES=0
SEED=0
COMMIT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --include) INCLUDES+=("$2"); shift 2 ;;
        --modes) MODES="$2"; shift 2 ;;
        --pbt-iterations) PBT_ITERATIONS="$2"; shift 2 ;;
        --target-timeout) TARGET_TIMEOUT="$2"; shift 2 ;;
        --max-files) MAX_FILES="$2"; shift 2 ;;
        --seed) SEED="$2"; shift 2 ;;
        --commit) COMMIT="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ---------------------------------------------------------------- resolve project
if [[ -d "$TARGET" ]]; then
    PROJECT_DIR="$(cd "$TARGET" && pwd)"
    NAME="$(basename "$PROJECT_DIR")"
elif [[ "$TARGET" == http* || "$TARGET" == git@* ]]; then
    NAME="$(basename "$TARGET" .git)"
    PROJECT_DIR="$CORPUS_DIR/$NAME"
    if [[ ! -d "$PROJECT_DIR" ]]; then
        echo "[clone] $TARGET -> $PROJECT_DIR"
        git clone --filter=blob:none "$TARGET" "$PROJECT_DIR"
    fi
    if [[ -n "$COMMIT" ]]; then
        git -C "$PROJECT_DIR" checkout --quiet "$COMMIT"
    fi
    echo "[project] $NAME @ $(git -C "$PROJECT_DIR" rev-parse --short HEAD)"
elif [[ -d "$CORPUS_DIR/$TARGET" ]]; then
    NAME="$TARGET"
    PROJECT_DIR="$CORPUS_DIR/$NAME"
else
    echo "Not a directory, URL, or known corpus name: $TARGET"
    exit 1
fi

# ---------------------------------------------------------------- toolchain
JACODB_DIR="${JACODB_DIR:-$HOME/Programming/jacodb}"
ETS_FRONTEND_DIR="${ETS_FRONTEND_DIR:-$JACODB_DIR/jacodb-ets/ts-frontend}"

if [[ ! -f "$ETS_FRONTEND_DIR/dist/index.js" ]]; then
    if [[ -f "$ETS_FRONTEND_DIR/package.json" ]]; then
        echo "[build] ts-frontend at $ETS_FRONTEND_DIR"
        (cd "$ETS_FRONTEND_DIR" && npm install && npm run build)
    else
        echo "ts-frontend not found: $ETS_FRONTEND_DIR (set ETS_FRONTEND_DIR or JACODB_DIR)"
        exit 1
    fi
fi

# ---------------------------------------------------------------- inputs
ANALYZE_DIRS=()
if [[ ${#INCLUDES[@]} -gt 0 ]]; then
    for inc in "${INCLUDES[@]}"; do ANALYZE_DIRS+=("$PROJECT_DIR/$inc"); done
else
    ANALYZE_DIRS=("$PROJECT_DIR")
fi

OUT_DIR="$RESULTS_DIR/$NAME"
mkdir -p "$OUT_DIR"

EXTRA_ARGS=()
[[ "$MAX_FILES" != "0" ]] && EXTRA_ARGS+=("--max-files" "$MAX_FILES")

# ---------------------------------------------------------------- run
export ETS_IR_PROVIDER=ts-frontend
export ETS_FRONTEND_DIR

for dir in "${ANALYZE_DIRS[@]}"; do
    suffix=""
    [[ ${#ANALYZE_DIRS[@]} -gt 1 || ${#INCLUDES[@]} -gt 0 ]] && suffix="-$(basename "$dir")"
    out_prefix="$OUT_DIR/$NAME$suffix"

    echo "[analyze] $dir"
    (cd "$REPO_ROOT" && ./gradlew -q "-PuseLocalJacodb=$JACODB_DIR" :usvm-ts-pbt:runHybrid --args="\
        $dir --recursive \
        --modes $MODES \
        --pbt-iterations $PBT_ITERATIONS \
        --target-timeout $TARGET_TIMEOUT \
        --seed $SEED \
        ${EXTRA_ARGS[*]:-} \
        --out $out_prefix" 2>&1 | grep -vE "^\[|WARN|INFO|ERROR|    at |    org\.|Exception")

    echo
    echo "== Summary: $NAME$suffix =="
    python3 "$SCRIPT_DIR/aggregate.py" "$out_prefix"-*.json --csv "$out_prefix.csv" \
        | tee "$out_prefix-summary.md"
    echo
done

echo "Reports: $OUT_DIR"
