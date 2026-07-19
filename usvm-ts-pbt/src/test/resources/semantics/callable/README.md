# Exact callable semantic fixture v1

This directory freezes the dependency-neutral contract produced by
`WP-SEM-CALLABLE`. It is an executable specification, not a claim that the
concrete EtsIR interpreter or the symbolic engine already implements callable
materialization.

## Contract

`callable-semantics-v1.json` embeds 20 valid ETC v2 case records. Each record
has exactly one explicit scene-aware plan:

- 13 records materialize a stable `modulePath + exportName + callableKind`
  reference and execute an exact dispatch;
- 7 records remain `unrepresentable/function` and produce a stable reject;
- no function is guessed, silently dropped, or converted to `undefined`.

The materialized subset covers ordinary functions, top-level and imported
arrows, imported functions, callable object fields, property receiver binding,
an ETC `constructorPlan` plus instance method, a static method,
`Function.prototype.call`, direct recursion, and JavaScript extra/missing
callback arguments. The frozen traces include all four required shapes:
direct, field, imported, and `.call`.

`CallableSemanticsFixture.ts` and `CallableSemanticsLibrary.ts` intentionally
use the JavaScript-compatible TypeScript subset. Node executes those sources as
one oracle. `CallableSemanticsSpecTest` has a separate Kotlin registry/value
model and produces byte-equivalent results and traces without calling the
production interpreter, intrinsics, or symbolic runtime. A separate Kotlin
test decodes every embedded record through the real ETC v2 codec and verifies
that the generic non-scene-aware codec throws for callable references and
constructor plans instead of returning `undefined`.

Capability labels are only the closed WP-CAP v1 enum. Callable details such as
`receiver_binding`, `callback_arity`, and `materialized_function_value` live in
`semanticTags`. Exact cases require `callableValueModel`; imported cases also
require `moduleRuntimeModel`.

## Explicit reject boundary

The stable reject subset covers:

- closures with captured mutable cells;
- returned arrows with a lexical `this` environment;
- bound functions;
- Proxy call/construct traps;
- async functions and Promise scheduling;
- generators and suspension identity;
- dynamically obtained callables without stable source identity.

Each boundary has a closed capability terminal status and reason code. Future
support must introduce a lossless ETC/materialization plan and bump this
contract rather than weakening the reject.

## Frozen real witnesses and module ownership

The fixture joins exactly 11 broad-denominator `typescript-collections`
targets back to the immutable 2026-07-19 target manifest and observations:

- `indexOf`: both `s9` successors;
- `lastIndexOf`: both `s9` successors;
- `remove`: `s6:0->7`;
- `frequency`: both `s10` successors;
- `equals`: both `s9` and both `s15` successors.

Every target is `reached=true, replayConfirmed=false` in the frozen
`internal-pbt-usvm` report. The concrete side threw while reading
`defaultEquals` from an unmaterialized namespace. These witnesses are shared
with `WP-SEM-MODULE` and use `branchId` as the union key:

- `semantics/module/module-semantics-v1.json` owns namespace initialization and
  the import binding for `util.defaultEquals`;
- this fixture owns the materialized function value and callable dispatch.

The v1 manifest has exact source ranges and stable EtsIR branch IDs, but no
emitted-JS v2 source-map artifact. Accordingly every shared witness says
`sourceBindingStatus=exact` and `etsIrMappingStatus=unmapped`; it does not invent
a statement mapping.

## Verification

From the repository root:

```bash
node usvm-ts-pbt/src/test/resources/semantics/callable/callable-spec-runner.cjs --validate
node --test usvm-ts-pbt/src/test/resources/semantics/callable/callable-spec.test.cjs
./gradlew :usvm-ts-pbt:test \
  --tests org.usvm.ts.pbt.semantics.callable.CallableSemanticsSpecTest
./gradlew :usvm-ts-pbt:compileTestKotlin :usvm-ts-pbt:detekt
node usvm-ts-pbt/benchmarks/baselines/2026-07-19/scripts/validate-baseline.mjs
```

`A-SEM-CONCRETE` and `A-SEM-SYMBOLIC` should consume this contract independently
and add Node -> concrete EtsIR -> symbolic replay checks. No runtime code belongs
in this fixture package.
