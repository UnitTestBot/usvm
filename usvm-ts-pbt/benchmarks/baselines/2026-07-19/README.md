# Frozen TypeScript PBT + USVM baseline (2026-07-19)

This directory is the immutable evidence bundle for `WP-BASE`. It freezes the
three project revisions, source-to-EtsIR selections, stable branch IDs,
method-level observations, published JSON/CSV summaries and upstream license
audit. Product code is intentionally not changed here.

The artifact/audit gate is green. A fresh paired legacy timing run is explicitly
deferred to `A-BENCH`: the historical 39.498 s / 136 runs remains evidence, not
an orchestration acceptance threshold. See `baseline-manifest.json` for the
protected dirty-overlay hashes and the exact reason for the deferral.

## Validate without `/tmp`

From the repository root:

```bash
node usvm-ts-pbt/benchmarks/baselines/2026-07-19/scripts/validate-baseline.mjs
```

The validator independently derives `D_broad-v1` (84 methods / 422 edges) and
`D_primitive-reference-v1` (42 / 236) from the copied target manifests, checks
all frozen SHA-256 values and reaggregates the compact observations. Its required
broad results are 305/422 for internal PBT, 240/422 for standalone USVM and
309/422 for internal PBT followed by USVM.

`freeze-baseline.mjs` is only for re-freezing from the two original raw report
roots; ordinary validation never reads them:

```bash
node usvm-ts-pbt/benchmarks/baselines/2026-07-19/scripts/freeze-baseline.mjs \
  --batched-root /tmp/representative-ts-pbt-batched-entrypoints-20260719 \
  --legacy-root /tmp/representative-ts-pbt-20260719
```

## Contents

- `projects/*/targets.json`: full v1 target manifests, including stable branch
  IDs, successor indices and source origins.
- `projects/*/source-targets.json`: frozen source-callable and primitive
  eligibility decisions; the adjacent text files are the selected method IDs.
- `denominators/*.tsv`: sorted `projectId<TAB>stableId` lists used for all set
  formulas.
- `observations/*.json`: compact, lossy projections of raw reports. They retain
  method counts/timing and every symbolic target's reached/replay outcome, but
  omit concrete inputs, timelines and stack traces.
- `results/*`: byte-identical copies of the two published JSON/CSV result pairs.
- `upstream-audit.json`: canonical URL, exact commit/tag, SPDX and required
  `LICENSE`/`NOTICE` blobs for six external tools.

## Reproduce the batched reports

First fetch the pinned corpus with
`usvm-ts-pbt/benchmarks/fetch-corpus.sh`. Use JDK 23.0.2, the jacodb
`ed94d48c78bd69464b6f2ef7f9635cd93a6bd66d` TS frontend and the environment
below. Replace the four path placeholders; the baseline is path-independent.

```bash
export JAVA_HOME=<jdk-23.0.2-home>
export JACODB=<jacodb-at-ed94d48c>
export ETS_IR_PROVIDER=ts-frontend
export ETS_FRONTEND_DIR="$JACODB/jacodb-ets/ts-frontend"
export BASELINE="$PWD/usvm-ts-pbt/benchmarks/baselines/2026-07-19"
export RAW=<new-batched-report-root>
export CORPUS="$PWD/usvm-ts-pbt/benchmarks/corpus"
```

Each broad project is reproduced by one CLI invocation producing
`PBT_ONLY`, `HYBRID` and `SYMBOLIC_ONLY` reports:

```bash
./gradlew -q -PuseLocalJacodb="$JACODB" :usvm-ts-pbt:runHybrid --args="$CORPUS/TheAlgorithms-TypeScript/maths --recursive --method-ids $BASELINE/projects/the-algorithms-maths/entry-method-ids.txt --modes PBT_ONLY,HYBRID,SYMBOLIC_ONLY --seed 20260719 --pbt-iterations 100 --target-timeout 1 --out $RAW/the-algorithms-maths/source-entry-internal-100-batched"

./gradlew -q -PuseLocalJacodb="$JACODB" :usvm-ts-pbt:runHybrid --args="$CORPUS/typescript-algorithms/src --recursive --project-frontend --method-ids $BASELINE/projects/typescript-algorithms/entry-method-ids.txt --modes PBT_ONLY,HYBRID,SYMBOLIC_ONLY --seed 20260719 --pbt-iterations 100 --target-timeout 1 --out $RAW/typescript-algorithms/source-entry-internal-100-batched"

./gradlew -q -PuseLocalJacodb="$JACODB" :usvm-ts-pbt:runHybrid --args="$CORPUS/typescript-collections/src/lib --recursive --project-frontend --method-ids $BASELINE/projects/typescript-collections/entry-method-ids.txt --modes PBT_ONLY,HYBRID,SYMBOLIC_ONLY --seed 20260719 --pbt-iterations 100 --target-timeout 1 --out $RAW/typescript-collections/source-entry-internal-100-batched"
```

For a primitive external baseline, first generate the corpus with the audited
adapter revision, then run the following once per project/tool. `--external-inputs`
is repeatable; pass fast-check, Jazzer and ExpoSE corpora together for the
`ensemble` row.

```bash
./gradlew -q -PuseLocalJacodb="$JACODB" :usvm-ts-pbt:runHybrid --args="<project-source> --recursive <optional-project-frontend> --method-ids $BASELINE/projects/<project-id>/primitive-method-ids.txt --external-inputs <tool-corpus.json> --external-only --modes PBT_ONLY,HYBRID --seed 20260719 --pbt-iterations 100 --target-timeout 1 --out $RAW/<project-id>/primitive-<tool-budget>-batched"
```

The exact corpus protocols are: fast-check 4.9.0 with 100 generated cases per
method, Jazzer.js 4.0.0 with 1 s per method and `--ignore-exceptions`, and ExpoSE
commit `ec03edf85f883248612b1d498c6a7d9189d16d6f` with 2 s per method on Node
21.7.2. Existing adapter READMEs contain their concrete generation commands.
`typescript-collections` has no methods in the primitive denominator.

Reaggregate all reports with one command:

```bash
node usvm-ts-pbt/benchmarks/summarize-batched-entrypoints.mjs \
  --root "$RAW" \
  --old-root <legacy-per-target-report-root> \
  --out <summary.json> \
  --csv <coverage.csv>
```

## Reproduction caveats

- Coverage credit is only concrete EtsIR replay; native tool coverage is not a
  numerator.
- This campaign has one fixed seed and no confidence interval.
- Jazzer/ExpoSE report time excludes corpus-generation startup.
- The copied raw reports were produced with the protected semantic overlay
  identified in `baseline-manifest.json`; integrate that overlay under its
  assigned owners before claiming bit-for-bit rerun parity from a clean SHA.
- Fresh legacy/candidate timing must be interleaved on one integrated commit and
  retain per-method pairs. Historical aggregate timing must not be used as a
  performance gate.
