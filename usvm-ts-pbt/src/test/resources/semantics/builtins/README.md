# Exact builtin semantic fixture v1

This directory freezes the dependency-neutral contract produced by
`WP-SEM-BUILTINS`. It is an executable specification, not a claim that either
the concrete EtsIR interpreter or the symbolic engine already implements the
contract.

## What is exact

`builtin-semantics-v1.json` contains 33 cases and covers:

- `Array.isArray` for arrays, ordinary objects and array-like objects;
- `Object.prototype.toString.call` for array, object, string, null, undefined
  and Map values without a custom `Symbol.toStringTag`;
- `Object.prototype.hasOwnProperty.call` and `in` for own, inherited, absent
  and own-with-`undefined` string keys;
- `Map.set/get/has`, overwrite/size, missing versus stored `undefined`, and
  SameValueZero for `NaN` and signed zero;
- JavaScript truthiness of `Map.get`/`Map.has` results, including undefined,
  zero, `NaN`, empty string and non-empty string.

Values, expected results and traces are serialized explicitly; special numbers
are strings (`NaN`, `Infinity`, `-Infinity`, `-0`). Every case has a stable
source callable ID, closed WP-CAP labels, a target status
`supported_with_flag`, and expected outcome `exact`. Finer concepts such as
`property_membership`, `missing_vs_undefined` and `same_value_zero` live in
`semanticTags`; the validators reject any non-WP-CAP value in
`capability.labels`.

`BuiltinSemanticsFixture.ts` uses the JavaScript-compatible TypeScript subset.
That lets Node execute the source without a TypeScript dependency and keeps it
valid input for a later EtsIR differential run. `builtin-spec-runner.cjs`
executes the source patterns against native Node APIs. The Kotlin test contains
an independent value model and must produce byte-equivalent result/trace JSON;
it does not call production interpreter or symbolic-runtime code.

## Frozen real residuals

The three `residualBlockers` are joined back to the immutable 2026-07-19
manifests and observations. The validator checks the baseline SHA-256, method,
branch, stmt index and every named `reached=true, replayConfirmed=false`
observation.

| Class | Stable EtsIR target | Frozen diagnosis |
|---|---|---|
| static runtime, array side | `03-array/12-flatten-arrays.ts::%dflt::%AM0$flattenReduce/2#s5:0->6` | semantic call stmt 3 (`Array.isArray`), condition stmt 4, target if stmt 5; Node says `Array.isArray([]) === true`; concrete campaign threw `TypeError: cannot call 'isArray' of undefined` |
| static runtime, scalar side | `03-array/12-flatten-arrays.ts::%dflt::%AM0$flattenReduce/2#s5:1->9` | semantic call stmt 3, condition stmt 4, target if stmt 5; Node says `Array.isArray({}) === false`; same unmaterialized static receiver |
| Map/get/truthiness | `prime_factorization.ts::%dflt::%AM0$%dflt/1#s27:0->28` | `Map.get` stmt 25, truthiness stmt 26, target if stmt 27; witness `n=2`; empty `Map.get(2)` is undefined/falsy, so Node does not take the requested truthy successor |

The status remains `semantic_mismatch`, never `replay-confirmed`. The first two
targets were reached but not replayed in both broad hybrid and symbolic-only
runs. The Map target was reached but not replayed after internal PBT,
fast-check, Jazzer.js, ExpoSE and their ensemble; the symbolic-only batched run
did not reach it and is intentionally not listed as reached evidence.

## Explicit boundary

The `unsupported` array prevents the exact subset from silently expanding. It
currently excludes Proxy traps, custom `Symbol.toStringTag`, Symbol/BigInt
property-key conversion, accessor/prototype side effects, cross-realm brands,
object-identity Map keys before ETC alias materialization, WeakMap, and detailed
invalid-receiver TypeErrors. Each exclusion has a capability terminal status;
none is removed from a campaign denominator by this fixture.

## Verification

From the repository root:

```bash
node usvm-ts-pbt/src/test/resources/semantics/builtins/builtin-spec-runner.cjs --validate
node --test usvm-ts-pbt/src/test/resources/semantics/builtins/builtin-spec.test.cjs
./gradlew :usvm-ts-pbt:test \
  --tests org.usvm.ts.pbt.semantics.builtins.BuiltinSemanticsSpecTest
node usvm-ts-pbt/benchmarks/baselines/2026-07-19/scripts/validate-baseline.mjs
```

`A-SEM-CONCRETE` and `A-SEM-SYMBOLIC` should consume this contract independently
and add Node -> EtsIR concrete -> symbolic witness replay checks. Updating a
frozen expected value requires an explicit version bump; regenerating expected
output from the implementation under test is not allowed.
