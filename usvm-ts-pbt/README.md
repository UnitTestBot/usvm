# usvm-ts-pbt: hybrid PBT + targeted symbolic execution for TypeScript/ArkTS

A research prototype combining dynamic and static analysis at the EtsIR level
(the ArkIR of the jacodb toolchain): a property-based-testing phase executes
the method concretely on random inputs, and the uncovered branch edges are
handed to the usvm-ts symbolic engine as reachability targets. Runtime type
profiles observed by the dynamic phase prune the type dimension of the
symbolic search (fake-object discriminators), with an automatic hint-free
fallback.

> Start here, then see `docs/ts-pbt/00-project-state.md` (repo root) for the
> current branch map, results, and backlog, and `docs/ts-pbt/01..04-*.md` for
> the research notes.

## Architecture map

```
org.usvm.ts.pbt
├── interpreter/   the first *concrete* interpreter for EtsIR
│   ├── VValue.kt              JS value universe (+VFunction, VMap, VSet)
│   ├── JsSemantics.kt         ECMAScript coercions (ToNumber/ToString/equality/...)
│   ├── EtsConcreteInterpreter.kt  CFG walker; first if-successor = true branch
│   ├── CallResolver.kt        static/virtual/ptr_call resolution, fn aliases
│   ├── Intrinsics.kt          Math/Number/Array-HOFs/Map/Set/strings registry
│   └── ExecutionResult.kt     Returned | Threw | Diverged | Unsupported
├── gen/           type-driven input generators (+constant mining), Shrinker
├── coverage/      CoverageTracker: stmt + branch-EDGE coverage, timelines
├── hybrid/        PbtPhase, SymbolicPhase (targets, capture, replay),
│                  TypeProfiler, HybridAnalyzer (4 ablation modes)
├── report/        JSON reports (kotlinx-serialization), TsTestValue<->VValue,
│                  Main.kt — the batch CLI
└── benchmarks/    corpus + one-command pipeline (see benchmarks/README.md)
```

Key design invariants:

- **Honesty**: anything unmodeled is `Unsupported` (never a silently wrong
  value); `Unsupported`/`Diverged` are excluded from PBT verdicts and counted
  in reports.
- **Replay as ground truth**: a branch is credited to the symbolic phase only
  if the solver-synthesized inputs replay it concretely.
- **One machine run per symbolic target** (`TargetsReachedStopStrategy` waits
  for ALL targets; batching lets one infeasible branch eat the whole budget).
- **The reaching state is captured at target-propagation time** by our own
  observer (the stock REACHED_TARGET collector misses it — see note 03 §3);
  inputs come from `TsTestResolver.resolveInputs` (works for non-terminated
  states).
- **Compare-to-zero idiom**: both front ends lower `if (x)` to `x != 0`;
  the interpreter treats compare-to-zero on a non-number as ToBoolean
  (see note 04 §2 — the IR is ambiguous by design).

## Environment

| What | Value |
|---|---|
| ArkAnalyzer (provider 1) | build of the **CI-pinned branch** `neo/2025-09-03`; `export ARKANALYZER_DIR=...`. Do NOT use other branches: if-successor order drifts and silently inverts every branch (note 02 §3) |
| jacodb ts-frontend (provider 2, default for benchmarks) | jacodb PR #361 branch; `npm install && npm run build` in `jacodb-ets/ts-frontend`; `export ETS_IR_PROVIDER=ts-frontend ETS_FRONTEND_DIR=<jacodb>/jacodb-ets/ts-frontend`; substitute jacodb via `./gradlew -PuseLocalJacodb=<jacodb> ...` until the pin is bumped |
| Solver | YICES (bundled via ksmt) |
| JDK | detekt requires <= 21 locally (`JAVA_HOME=<jdk21> ./gradlew detektMain`) |

**Gradle pitfall**: changing `ARKANALYZER_DIR`/`ETS_IR_PROVIDER` does *not*
invalidate test tasks — use `--rerun-tasks` when switching providers.

## Running

```bash
# tests (29): unit, differential oracle vs TsMachine, hybrid e2e, ablation
ARKANALYZER_DIR=<pinned-aa> ./gradlew :usvm-ts-pbt:test

# one method / one file
./gradlew :usvm-ts-pbt:runHybrid --args="<abs-path>/File.ts --mode HYBRID_WITH_HINTS --out report.json"

# a whole open-source project, all ablation modes, aggregated:
cd usvm-ts-pbt/benchmarks && ./run-project.sh <git-url|corpus-name|local-path> [--include dir]
```

## Differential-test whitelist policy

`ConcreteVsSymbolicDifferentialTest` replays every input model the engine
produces; mismatches FAIL the build unless whitelisted in
`knownEngineDivergences` with a root-cause comment. Only add entries after
manually confirming against ECMAScript semantics that the divergence is an
engine/front-end issue (current entries: `+` on references, Lt on mixed
fake-object sorts, the NaN hole of the truthiness idiom — see notes 02/04).

## What is intentionally out of scope (v1)

Generators/`yield`, async/Promise, closures capturing mutable state, BigInt,
cross-file imports under per-file scenes (all reported as `Unsupported`),
string constraints in the symbolic phase (engine limitation: placeholder
strings, lossy replay).
