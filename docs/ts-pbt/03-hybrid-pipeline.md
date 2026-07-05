# Hybrid pipeline: PBT -> targeted symbolic execution with type feedback

> Research note. Module: `usvm-ts-pbt`. Date: 2026-07-03.

## 1. Pipeline (implemented, all tests green)

```
EtsScene (jacodb-ets, ArkAnalyzer neo/2025-09-03)
   │
   ├─ Phase 1: PbtPhase (org.usvm.ts.pbt.hybrid)
   │    EtsConcreteInterpreter × InputGenerator(seeded, constant-mined)
   │    → CoverageTracker (stmt + branch edges), TypeProfiler, failures + Shrinker
   │
   ├─ Handoff: CoverageTracker.uncoveredBranches()
   │
   ├─ Phase 2: SymbolicPhase — per uncovered edge (I, S):
   │    TsReachabilityTarget chain InitialPoint(entry)→Intermediate(I)→Final(S),
   │    TsMachine(TARGETED, stopOnTargetsReached, YICES, per-target timeout),
   │    TsOptions.inputTypeHints ← TypeProfiler (HYBRID_WITH_HINTS mode),
   │    hint-free fallback on failure,
   │    state captured at propagation time (ReachingStateCaptor),
   │    inputs via TsTestResolver.resolveInputs (new, works for non-terminated states),
   │    concrete replay confirms the edge → merged into CoverageTracker
   │
   └─ HybridAnalyzer → HybridReport (JSON) / CLI (report.Main)
```

Modes: `PBT_ONLY | SYMBOLIC_ONLY | HYBRID | HYBRID_WITH_HINTS` (ablation-ready).

## 2. usvm-ts changes (minimal, reviewed)

1. `TsInputTypeHints.kt` (new): `TsHintType` enum + method-keyed hint map.
2. `TsOptions.inputTypeHints` (default `EMPTY` — zero behavior change).
3. `TsInterpreter.getInitialState`: for `TsUnresolvedSort` parameters, after
   `mkFakeValue`, `applyParameterTypeHints` asserts a disjunction over the fake
   object's type discriminators (`fpTypeExpr`/`boolTypeExpr`/`refTypeExpr`) and
   refines the ref slot (`null`/`undefined`/string-type/not-nullish) — an
   *unsound-by-design* prune with orchestrator-level fallback.
4. `TsTestResolver.resolveInputs` (new): before-state resolution only, usable for
   states captured mid-execution.
5. `TsTestResolver` + `ObjectClass` promoted from test to main sources.

## 3. Engineering findings worth writing up

* **stop/collect race in targeted mode**: `stopOnTargetsReached` halts the machine
  when the target tree is removed — at *propagation* time — while both
  `TargetsReachedStatesCollector` and `CoveredNewStatesCollector` only collect
  *terminated* states; additionally, in `TsMachine` the collector observes
  *before* the `machineObserver` (`ReachabilityObserver`), so at termination the
  `reachedTerminal` flag is not yet set. Consequence: `REACHED_TARGET` collection
  effectively never yields states in this configuration. Fix: capture the state
  at propagation time (custom `UMachineObserver` placed after
  `ReachabilityObserver`) and resolve *inputs only* from its model.
* **Steps are the wrong metric for the hints ablation on micro-benchmarks**: with
  `useSolverForForks = true` infeasible discriminator forks are pruned eagerly, so
  machine steps stay equal; hints save *solver work* (visible as wall time /
  solver calls on larger corpora). The CLI reports wall time per target.
* **PBT constant mining is strong**: equality guards against literal constants
  (`x === 1234567`) are covered by mining the method body; the symbolic phase is
  needed for *arithmetic* relations (`x * 2 === 98764` → engine synthesizes
  `x = 49382`, confirmed by concrete replay).

## 4. End-to-end status

* `HybridSamples.magic`: PBT 3/4 edges, symbolic reaches the arithmetic branch,
  replay confirms, 100% branch coverage. Input `x = 49382` recovered from the model.
* `HybridSamples.crashy`: PBT finds the out-of-bounds throw, shrinker minimizes
  to `([], 0)`.
* CLI: `./gradlew :usvm-ts-pbt:runHybrid --args="<file|dir> [--mode ...] [--out report.json]"`
  (requires `ARKANALYZER_DIR` → pinned AA build; paths absolute or relative to repo root).

## 5. First corpus run (usvm-ts samples/operators, HYBRID_WITH_HINTS)

`--pbt-iterations 500 --target-timeout 10`, 90 methods, total wall ~6 s:

* branch coverage 524/720 = **72.8%**, stmt coverage 1660/1974 = **84.1%**,
  44/90 methods at 100% branches;
* PBT: 29052 executions, 3500 `Unsupported` (bigint, `delete`, `in`-operator
  paths — honestly excluded, not guessed);
* symbolic: 275 targets, 80 reached, 25 replay-confirmed, 195 hint-fallbacks.
  Most unreached targets are the samples' *intentionally unreachable* branches
  (`return -1; // unreachable`) — i.e. correct behavior, and a natural
  connection to the UnreachableCodeDetector use-case;
* observed engine issue: unbounded mutual recursion
  `StrictEq.resolveFakeObject ↔ resolveBinaryOperator` on some fake-object
  comparisons (logged stack overflows; states killed) — differential/corpus
  finding #3 for the engine maintainers.

Improvement backlog (paper experiments): raise replay-confirmation rate
(fake-object input resolution), distinguish "infeasible" from "timeout" per
target, add solver-time metric for the hints ablation.

## 6. Test suite map (usvm-ts-pbt)

* `JsSemanticsTest` — ECMAScript coercion corner cases.
* `ConcreteInterpreterDslTest` — interpreter over DSL-built EtsIR (no ArkAnalyzer).
* `ConcreteVsSymbolicDifferentialTest` — differential oracle vs TsMachine
  (7 sample suites; 1 whitelisted engine divergence).
* `PbtPhaseTest` — coverage, shrinking, type profiles, seed reproducibility.
* `HybridE2eTest` — full pipeline + hints ablation harness.
* `HybridAnalyzerTest` — 4 modes, JSON round-trip.
