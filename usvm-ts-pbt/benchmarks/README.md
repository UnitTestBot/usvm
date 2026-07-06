# Benchmark infrastructure: hybrid analysis on open-source TypeScript

Batch experiments for the hybrid PBT + targeted symbolic execution pipeline
over open-source TS projects, using the **native TS parser from jacodb**
(`jacodb-ets/ts-frontend`, jacodb PR #361) as the EtsIR provider.

## TL;DR — one command per project

```bash
# by URL: clones into corpus/, analyzes, aggregates, writes reports
./run-project.sh https://github.com/TheAlgorithms/TypeScript.git --include maths

# by corpus name (already cloned) or by local path
./run-project.sh typescript-algorithms --include src
./run-project.sh ~/work/my-ts-project

# narrower/faster runs
./run-project.sh <target> --modes PBT_ONLY,HYBRID --max-files 40 --target-timeout 5
```

The script does everything end-to-end:

1. resolves the project (clone by URL / corpus name / local path; `--commit <sha>`
   for reproducibility),
2. builds `ts-frontend` if missing (`JACODB_DIR`, default `~/Programming/jacodb`),
3. runs the hybrid analyzer over all requested ablation modes
   (default: `PBT_ONLY,SYMBOLIC_ONLY,HYBRID,HYBRID_WITH_HINTS`) with per-file
   fault isolation,
4. aggregates and writes everything under `results/<name>/`:
   - `<name>-<MODE>.json` — full machine-readable report per mode
     (per-method coverage, coverage timelines, per-target wall time,
     hint/fallback attribution, unsupported-execution counters),
   - `<name>-summary.md` — the mode-comparison table,
   - `<name>.csv` — per-method rows for plotting.

Until jacodb PR #361 is merged and the usvm dependency pin is bumped, the local
jacodb build is substituted automatically via `-PuseLocalJacodb=$JACODB_DIR`.

## Pinned corpus

`corpus.json` pins reference projects to commits; `./fetch-corpus.sh` clones them
(requires `git` + `jq`). Preferred projects are algorithm-style repositories
(self-contained functions, arithmetic- and branch-rich) — analyzable per-file at
the EtsIR level without cross-module resolution. Cross-file imports resolve to
honest `Unsupported` at call sites and are reported, never guessed.

## Manual (advanced) invocation

The underlying CLI offers finer control:

```bash
export ETS_IR_PROVIDER=ts-frontend
export ETS_FRONTEND_DIR=<jacodb>/jacodb-ets/ts-frontend

./gradlew -PuseLocalJacodb=<jacodb> :usvm-ts-pbt:runHybrid --args="\
    <dir-or-file.ts> --recursive \
    --modes PBT_ONLY,SYMBOLIC_ONLY,HYBRID,HYBRID_WITH_HINTS \
    --pbt-iterations 1000 --target-timeout 10 \
    --out results/my-run"

python3 aggregate.py results/my-run-*.json --csv results/my-run.csv
```

See `./gradlew :usvm-ts-pbt:runHybrid --args=""` for the full option list
(`--class`, `--method`, `--exclude`, `--seed`, `--no-fallback`, ...).

## Interpreting the numbers

- **Branch %** counts covered branch *edges* `(if, successor)` — the handoff
  granularity of the hybrid pipeline.
- **Unsupported** counts PBT executions that hit constructs the concrete
  interpreter does not model (generators, cross-file imports under per-file
  scenes, exotic intrinsics). They are excluded from pass/fail verdicts.
- **Reached / Replay OK** — symbolic targets reached, and those whose
  solver-synthesized inputs were *confirmed by concrete replay* (the ground
  truth for crediting a branch to the symbolic phase).
- **Fallbacks** — hint-free retries after an unsuccessful hinted run: the
  measured price of the (deliberately unsound) type-hint pruning.
