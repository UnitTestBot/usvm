# Project state: hybrid PBT + symbolic execution for TS (handoff document)

> Last updated: 2026-07-08. **Read this first** when resuming work.
> Context: prototype for the PhD "Гибридные методы анализа динамических
> языков программирования" and the accompanying paper.

## 1. Branch map (all pushed to origin)

| Branch | Purpose | Contents |
|---|---|---|
| `caelmbleidd/ts_pbt` (**PR #341**) | The PBT/hybrid prototype | `usvm-ts-pbt` module, minimal usvm-ts extensions that belong to the feature (input type hints in `getInitialState`, `TsTestResolver` promotion + `resolveInputs`), benchmark infra, research notes `docs/ts-pbt/0*` |
| `caelmbleidd/pbt_article` | The paper | = ts_pbt + `docs/ts-pbt/paper/` (acmart LaTeX; `latexmk -pdf main.tex`) |
| `caelmbleidd/ts-interpreter-fixes` | Engine fixes, **kept separate from PBT by agreement** (base: master @ dab6d432) | mock unresolved virtual calls instead of killing the state; inc/dec unary expressions in TsExprResolver |

Policy: PBT work goes to `ts_pbt`; engine (usvm-ts machine) fixes go ONLY to
`ts-interpreter-fixes`; paper text goes to `pbt_article` (rebase it on ts_pbt
as needed). Never mix engine fixes into ts_pbt.

## 2. Current results (reproducible via benchmarks/run-project.sh)

TheAlgorithms/TypeScript `maths` (62 methods, 264 branch edges, seed 0,
pbt-iterations 1000, target-timeout 10 s, ts-frontend provider):

| Mode | Branch % | Wall s | Notes |
|---|---|---|---|
| PBT only | 78.0 | 6.3 | |
| Symbolic only | 63.1 → 64.7 | ~306 | second number = with the inc/dec engine fix |
| Hybrid | **83.0** | 156 | 13/58 residual targets reached |
| Hybrid + hints | 83.0 | 307 | 45 fallbacks (residual is engine-hard, hints can't help there) |

loiane/javascript-datastructures-algorithms `src` (307 methods, 670 edges):
PBT 62.5% @ 1.7 s; symbolic-only 57.2% @ 427 s; hybrid **75.1%** @ 243 s.
Known issue there: replay-confirmed ≈ 0 for instance methods (see backlog).

History: hybrid on maths was 64% before the engine mock-fix + interpreter
extensions (notes 02/03 tell the diagnosis story — useful for the paper).

## 3. In-flight / external dependencies

- **usvm PR #341** (ts_pbt): CI was fixed (validateProjectList + @types/node
  pin for the AA build + `:usvm-ts-pbt:check` in ci-ts) but the run was NOT
  re-verified after the last force-pushes — check `gh pr checks 341`.
- **jacodb PR #361** (native ts-frontend): usvm-ts-pbt is verified compatible
  (note 04). Until merged + usvm pin bump, use
  `./gradlew -PuseLocalJacodb=<jacodb-checkout>` and `ETS_IR_PROVIDER=ts-frontend`.
- Local toolchain expectations: pinned ArkAnalyzer build (`neo/2025-09-03`)
  at `~/Programming/arkanalyzer-neo-2025-09-03`; jacodb checkout with the
  ts-frontend at `~/Programming/jacodb` (branch `caelmbleidd/ts_native_parser`).

## 4. Backlog

**PBT side (ts_pbt):**
1. Replay confirmation for instance methods: solver-synthesized `this`
   objects rarely replay (TsClass -> VObject decode gaps) — investigate on
   the datastructures corpus (`replay-confirmed: 1` out of 84 reached).
2. ~~Project-level scenes~~ DONE (2026-07-10): the batch CLI loads the whole
   corpus into one EtsScene by default (`--file-scenes` restores per-file).
   Effect on datastructures: PBT Unsupported 13436 -> 6719, PBT branch
   62.5 -> 64.8%, symbolic-only 57.2 -> 59.8% with replay-confirmed
   101 -> 115; the in-project unresolved bucket (lessThan/Stack) vanished —
   residual unresolved calls are pure builtins (Symbol 32, iterator `next`
   32, toLowerCase 8, Array.isArray 4, JSON.stringify 4, Object.keys 2).
3. Symbolic->PBT feedback loop: reached inputs as seeds for a mutation round.
4. Fallback budgeting for type hints (skip the retry when the hinted run
   timed out rather than proved UNSAT) — motivated by the ablation numbers.
5. Mutation-based bug seeding over `org.jacodb.ets.dsl` for a mutation-score
   experiment (paper).

**Engine side (ts-interpreter-fixes only):**

DONE so far in the branch: mock unresolved virtual calls (instead of killing
the state) with a distinct "Mocking an unresolved call" log line; inc/dec
unary expressions; precise approximations for the corpus-hot builtins
(Number.isInteger/isSafeInteger/isFinite, Math floor/ceil/trunc/round/abs/
sqrt/min/max with JS NaN semantics, console.* as undefined). Frequency data
(full-log probes, 2026-07-10): maths corpus unresolved calls were dominated
by Math.abs (208), Number.isInteger (55), Math.sqrt (13) — now modeled;
the datastructures corpus is dominated by *in-project cross-file* targets
(lessThan 46, greaterThan 24, class Stack 29) — a per-file-scene problem to
fix on the PBT side, not by mocking. Effect of precise approximations:
symbolic-only replay-confirmed on maths went 66 -> 83 of ~105 reached (the
measurable reduction of over-approximation). Residual mocks on maths:
generators `next` (18), array HOF `some` (10), `Symbol` (8).

Backlog:
1. Container constructors: `new Array(n)` / `Map` / `Set` currently resolve
   to the unresolved-class mock (13 Error / 9 Map / 8 Array / 1 Set per
   probe); `Array(n)` can be modeled precisely with initializeArray.
2. Unbounded recursion `StrictEq.resolveFakeObject <-> resolveBinaryOperator`
   (stack overflow on fake-object comparisons; seen on corpus runs).
3. The NaN hole of the compare-to-zero truthiness idiom: engine evaluates
   `x != 0` numerically, so `undefined` becomes truthy (note 04 §2);
   candidate fix: `mkTruthyExpr` for compare-to-zero on non-fp operands.
4. `+` on references is approximated numerically (`null + {}` -> NaN instead
   of string concat) — differential finding, whitelisted.
5. Lt/logical operators on mixed fake-object sorts (whitelisted findings).
6. Strings: `TsTestStateResolver` returns placeholder strings -> lossy replay.
7. Consider TS-level builtin implementations (a builtins.ts lowered to EtsIR
   and added to the scene as SDK) for things awkward to encode in SMT.

**Front-end findings to report upstream:**
- ArkAnalyzer lowers `const old = x++` as `x := x + 1; old := x` (old gets the
  NEW value) — lowering bug, documented in the ts-interpreter-fixes sample.
- The `if (x)` -> `x != 0` idiom is ambiguous IR (indistinguishable from a
  genuine loose comparison); suggested a dedicated truthiness ConditionExpr
  for jacodb #361.

## 5. Docs index

- `usvm-ts-pbt/README.md` — module overview, architecture, env, pitfalls.
- `usvm-ts-pbt/benchmarks/README.md` — one-command measurement pipeline.
- `docs/ts-pbt/01-arkanalyzer-and-ets-ir.md` — front ends, EtsIR contract.
- `docs/ts-pbt/02-concrete-interpreter-and-differential-findings.md` —
  interpreter design; AA successor-order drift (the branch-inversion story).
- `docs/ts-pbt/03-hybrid-pipeline.md` — pipeline, usvm-ts touch points,
  stop/collect race, first corpus numbers.
- `docs/ts-pbt/04-jacodb-native-parser-compat.md` — ts-frontend compat,
  truthiness-idiom ambiguity, how to run with the native parser.
- `docs/ts-pbt/paper/` (branch `pbt_article`) — the paper; numbers in
  Table 1 come from `benchmarks/results/maths2-*.json`-style runs.

## 6. Quick sanity commands

```bash
export ARKANALYZER_DIR=~/Programming/arkanalyzer-neo-2025-09-03
./gradlew :usvm-ts-pbt:test --rerun-tasks          # 29 tests, expect 0 failures
./gradlew :usvm-ts:test --rerun-tasks              # 422+ tests, expect 0 failures
cd usvm-ts-pbt/benchmarks && ./run-project.sh TheAlgorithms-TypeScript --include maths
```

Remember `--rerun-tasks` whenever env vars (providers) change — Gradle does
not track them.
