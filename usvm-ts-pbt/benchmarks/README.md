# Benchmark infrastructure: hybrid analysis on open-source TypeScript

Batch experiments for the hybrid PBT + targeted symbolic execution pipeline
over an open-source TS corpus, using the **native TS parser from jacodb**
(`jacodb-ets/ts-frontend`, jacodb PR #361) as the EtsIR provider.

## 1. One-time setup

```bash
# 1. Build the jacodb ts-frontend (from the jacodb checkout on the PR #361 branch):
cd <jacodb>/jacodb-ets/ts-frontend && npm install && npm run build

# 2. Fetch the corpus (defined in corpus.json, pinned commits):
./fetch-corpus.sh          # requires git + jq; clones into benchmarks/corpus/
```

Until jacodb PR #361 is merged and the usvm dependency pin is bumped, substitute
the local jacodb build via the opt-in composite build flag `-PuseLocalJacodb=<path>`.

## 2. Running experiments

```bash
export ETS_IR_PROVIDER=ts-frontend
export ETS_FRONTEND_DIR=<jacodb>/jacodb-ets/ts-frontend
export ARKANALYZER_DIR=<pinned-arkanalyzer>   # only needed for provider=arkanalyzer

./gradlew -PuseLocalJacodb=<jacodb> :usvm-ts-pbt:runHybrid --args="\
    usvm-ts-pbt/benchmarks/corpus/TheAlgorithms-TypeScript/maths \
    --recursive \
    --modes PBT_ONLY,SYMBOLIC_ONLY,HYBRID,HYBRID_WITH_HINTS \
    --pbt-iterations 1000 --target-timeout 10 \
    --out usvm-ts-pbt/benchmarks/results/maths"
```

This produces one `HybridReport` JSON per mode: `<out>-<MODE>.json`.
Paths are resolved relative to the repository root (the `runHybrid` working dir).

Per-file isolation: frontend/load failures and per-method analysis failures are
counted and logged, never abort the batch (honest-numbers principle: the reports
carry `unsupported`/failure counters).

## 3. Aggregation

```bash
python3 aggregate.py results/maths-*.json --csv results/maths.csv
```

Prints a markdown mode-comparison table (branch/stmt coverage, PBT failures,
targets reached/replay-confirmed/fallbacks, wall time) and optionally a
per-method CSV for plots. Raw JSON reports retain full coverage timelines and
per-target attribution for the paper's ablation analysis.

## 4. Corpus policy

`corpus.json` pins each project to a commit. Preferred projects: algorithm-style
repositories (self-contained functions, arithmetic- and branch-rich) — analyzable
per-file at the EtsIR level without cross-module resolution. Cross-file imports
resolve to `Unsupported` at call sites and are reported, not guessed.
