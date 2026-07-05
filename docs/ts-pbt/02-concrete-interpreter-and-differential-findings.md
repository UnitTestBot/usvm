# Concrete EtsIR interpreter + differential-testing findings

> Research note for the hybrid PBT + symbolic execution effort.
> Module: `usvm-ts-pbt`. Date: 2026-07-02.

## 1. What was built

`usvm-ts-pbt` (new Gradle module) contains the first *concrete* interpreter for EtsIR
(`org.usvm.ts.pbt.interpreter.EtsConcreteInterpreter`):

* JS value universe `VValue` (`VNumber/VBool/VString/VNull/VUndefined/VObject/VArray/VNamespace`),
  reference semantics for objects/arrays;
* full ECMAScript coercions in `JsSemantics` (ToNumber/ToString/ToPrimitive/ToInt32,
  loose/strict equality, relational ops, `typeof`, `in`);
* statement walker over `EtsBlockCfg` (convention: **first if-successor = true branch** —
  the *model-level* contract after the jacodb lift, see §3);
* calls: static + virtual dispatch by name over the class hierarchy (mirrors
  `TsInterpreter.visitVirtualMethodCall`), intrinsics registry (`Math`, `Number`,
  `console`/`Logger` no-ops, array/string methods) — anything unknown yields
  `ExecutionResult.Unsupported`, never a silently wrong value;
* outcomes: `Returned / Threw / Diverged (budget) / Unsupported`.

`TsTestResolver` + `ObjectClass` were promoted from usvm-ts *test* sources to *main*,
so symbolic states can be converted to concrete inputs outside tests
(`org.usvm.util.TsTestResolver`, unchanged package).

## 2. Differential oracle

`ConcreteVsSymbolicDifferentialTest`: every input model produced by `TsMachine`
(via `TsTestResolver`) is replayed on the concrete interpreter; return values must
match. 7 sample suites (Add, Less, Neg, And, Equality, Truthy, TypeCoercion):
**~100 compared executions, 1 confirmed divergence** (whitelisted, see below).

## 3. Finding A: ArkAnalyzer if-successor order drift (critical)

* DTO contract: AA serializes if-successors as **(false, true)**; jacodb's
  `EtsMethodBuilder` (`Convert.kt`) reverses them, so the *model* (`EtsBlockCfg`)
  uses **(true, false)** — which is what `TsInterpreter.visitIfStmt` assumes.
* The local AA checkout (`lipen/usvm` branch) emits a **different order** than the
  CI-pinned `neo/2025-09-03`. After the lift this **inverts every branch** of every
  method: this was the real cause of the "pre-existing" local usvm-ts test failures
  (`Add`: violated invariants; reachability: "unreachable stmt reached").
* Fix used here: a dedicated worktree `~/Programming/arkanalyzer-neo-2025-09-03`
  (built from the CI-pinned branch); run all TS tests with
  `ARKANALYZER_DIR=~/Programming/arkanalyzer-neo-2025-09-03`.
* Lesson for the paper (methodology): the concrete interpreter + differential suite
  detected a *frontend contract violation* end-to-end within hours — this is an
  argument for the triple-oracle setup (concrete IR interpreter vs symbolic engine
  vs production JS engine).

Beware Gradle test caching: changing `ARKANALYZER_DIR` does **not** invalidate test
tasks; use `--rerun-tasks` when switching AA versions.

## 4. Finding B: usvm-ts `+` approximation on references

For reference operands usvm-ts resolves `+` numerically, so `null + {}` becomes
fp `NaN`; JS applies ToPrimitive + string concatenation
(`null + {} === "null[object Object]"`). Consequently in
`Add.addUnknownValues(null, {})` the engine follows the `res != res` (NaN) branch,
which is concretely unreachable, and reports return `NaN` where JS returns `42`.
Whitelisted in the differential suite as a known engine approximation.

## 5. Environment requirements

* `ARKANALYZER_DIR` → CI-pinned AA build (`neo/2025-09-03`), see §3.
* Solver: YICES (same as usvm-ts test config).
* usvm-ts-pbt test resources include `usvm-ts/src/test/resources` (read-only reuse).
