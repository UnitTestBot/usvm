# Compatibility with the jacodb native TS parser (jacodb PR #361)

> Research note. Date: 2026-07-06.

## 1. Status: fully compatible

jacodb PR #361 (`caelmbleidd/ts_native_parser`) adds `jacodb-ets/ts-frontend` — a
TypeScript-compiler-based EtsIR producer replacing ArkAnalyzer — and reworks
`LoadEtsFile.kt`: the provider is selected by `ETS_IR_PROVIDER`
(`ts-frontend` (default) | `arkanalyzer`), with `ETS_FRONTEND_DIR` pointing at the
built frontend (`npm run build` → `dist/index.js`; the CLI is flag-compatible with
`serializeArkIR`). The `loadEtsFileAutoConvert`-family signatures are unchanged, so
**usvm-ts-pbt requires no code changes**.

Verified end-to-end: usvm built with the PR-branch jacodb substituted via the new
opt-in composite build (`./gradlew -PuseLocalJacodb=<path> ...`, see
`settings.gradle.kts`), full usvm-ts-pbt suite (29 tests incl. the differential
oracle and hybrid e2e) is green under **both** providers.

## 2. Findings from cross-frontend differential testing

Running the same differential oracle under the second frontend immediately
yielded new results — cross-frontend differential testing is itself a method:

1. **The compare-to-zero truthiness idiom is ambiguous IR.** Both frontends lower
   `if (x)` to the ConditionExpr `x != 0` — byte-identical to a genuine
   source-level `x != 0` loose comparison. The two differ in JS for non-number
   operands (`[] != 0` is *false*, yet `[]` is truthy; `undefined != 0` is *true*,
   yet `undefined` is falsy). The concrete interpreter now follows the idiom
   contract (compare-to-zero on a non-number operand = ToBoolean), documented in
   `EtsConcreteInterpreter`.
   *Feedback for #361: a dedicated truthiness ConditionExpr op would remove the
   ambiguity.*
2. **The engine falls into the NaN hole of the same idiom**: it evaluates
   `x != 0` numerically, so for `x = undefined` it derives
   `ToNumber(undefined) = NaN != 0 → true` and treats `undefined` as *truthy*
   (`And.andOfUnknown(0, undefined)`: engine 21, JS 0). Whitelisted; engine issue.
3. `TypeCoercion.transitiveCoercionNoTypes` is doubly ambiguous: a genuine
   `c != 0` comparison (idiom-indistinguishable) plus the engine's non-JS `&&`
   on references; JS gives 2, engine 1, concrete 4. Whitelisted.

## 3. How to run against the native parser

```bash
cd <jacodb>/jacodb-ets/ts-frontend && npm install && npm run build
cd <usvm>
export ETS_IR_PROVIDER=ts-frontend
export ETS_FRONTEND_DIR=<jacodb>/jacodb-ets/ts-frontend
./gradlew -PuseLocalJacodb=<jacodb> :usvm-ts-pbt:test
```

(The `ARKANALYZER_DIR`-gated tests still require the variable to be *set* until
the enabling condition is made provider-aware; its value is unused by the native
provider.) Once #361 is merged and usvm bumps the jacodb pin, `-PuseLocalJacodb`
becomes unnecessary and the native parser works out of the box — including for the
open-source-corpus benchmark infrastructure, which eliminates the per-file
ArkAnalyzer/Node round-trip fragility on large corpora.
