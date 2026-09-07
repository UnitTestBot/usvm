# TypeScript unknown-call models

This document describes the semantic-model path used when the normal TypeScript interpreter cannot execute a call.

## Mental model

There are only three stages:

1. The regular interpreter and existing compatibility approximations try to execute the call.
2. If execution cannot continue, `TsUnknownCallModelCatalog` selects one enabled semantic model by its target.
3. If no model handles the call or a model leaves a residual state, the configured fallback is applied.

```text
normal execution
      |
      | cannot execute
      v
enabled model with matching target? -- no --> fallback
      |
     yes
      v
model accepts these inputs? -------- no --> fallback
      |
     yes
      v
model successors + optional residual ----> residual uses fallback
```

The catalog contains model objects directly. There are no implementation-kind values, backend registrations, or
separate descriptor and implementation IDs.

## Configuration

Unknown-call behavior is configured directly in `TsOptions`:

```kotlin
TsOptions(
    enabledUnknownCallModelIds = setOf("ts.array.shift"),
    unknownCallFallback = TsResidualCallPolicy.STOP_PATH,
)
```

### `enabledUnknownCallModelIds`

This is the only model-selection setting.

| Value | Meaning |
| --- | --- |
| `null` | Enable every built-in model. This is the default. |
| `emptySet()` | Disable every built-in model. |
| `setOf("id", ...)` | Enable exactly the listed built-in model IDs. |

Unknown IDs are rejected when the machine creates its immutable per-run catalog. The input set is copied at that
point, so later mutations cannot change an active run.

Use the model's `id`, for example `ts.array.shift`. A target method name, class name, source filename, or fingerprint is
not a model ID.

The built-in catalog currently contains one model:

| ID | Implementation | Accepted calls |
| --- | --- | --- |
| `ts.array.shift` | Kotlin intrinsic using symbolic-memory `memcpy` | Zero-argument `shift` on a definitely one-dimensional array whose element sort is known. |

An `any`/unknown receiver, a fake-value wrapper, a non-array receiver, and an array whose element sort is unresolved do
not become applicable merely because the method is named `shift`; they use fallback.

### `unknownCallFallback`

The fallback is applied when:

- no enabled model target matches the call;
- the selected model returns `null` because it cannot safely handle the concrete inputs;
- a model returns a satisfiable `residualGuard`.

The available policies are:

| Policy | Behavior |
| --- | --- |
| `STOP_PATH` | Prune the unsupported state. This is the default. |
| `FRESH_SYMBOLIC_RETURN` | Continue with a fresh symbolic result and ignore unknown side effects and exceptions. |

`FRESH_SYMBOLIC_RETURN` is deliberately imprecise. Use it only when opaque continuation is preferable to pruning.

## Model identity and target

Every model implements `TsUnknownCallModel`:

```kotlin
interface TsUnknownCallModel {
    val id: String
    val target: TsUnknownCallTarget

    fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution?
}
```

### Choosing an ID

Use a stable semantic name:

```text
<ecosystem>.<owner-or-type>.<operation>[.<semantic-variant>]
```

Examples:

- `ts.array.shift`
- `ts.array.pop`
- `node.buffer.copy`

The ID is used for configuration, observer events, and catalog fingerprints. Do not include:

- an implementation mechanism such as `intrinsic`;
- a hash;
- a version number;
- a supported-domain label.

Keep the same ID if an equivalent model is later reimplemented by another mechanism.

### Choosing a target

`TsUnknownCallTarget` matches stable call metadata declaratively:

```kotlin
TsUnknownCallTarget(
    methodName = "shift",
    failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
)
```

Only `methodName` is required. Add `enclosingClassName` or `failureReason` when the method name alone is too broad.
The catalog rejects overlapping enabled targets before execution, so catalog order is never a priority rule.

The target identifies a call family. State-dependent checks, such as the receiver's symbolic runtime type, belong in
`apply`.

The built-in array target intentionally combines the method name with `PARTIAL_APPROXIMATION` instead of a class name.
That failure reason is emitted only after the regular approximation path has classified the receiver as an
`EtsArrayType`. Calls on `any`/unknown receivers reach another failure reason and cannot match this target. The model
still validates the resolved receiver and its element sort before changing memory.

## Applicability and residual states

There is no separate `EXACT` or `PARTIAL` flag.

- `apply(...) == null` means the model rejects the complete call. The dispatcher uses fallback.
- `residualGuard == null` means the returned execution completely handles the accepted state.
- A non-null `residualGuard` sends precisely that symbolic subdomain to fallback.

For example, a model may handle an array receiver under `isArray` and leave `!isArray` as residual:

```kotlin
TsUnknownCallModelExecution(
    successors = listOf(
        TsUnknownCallModelSuccessor(
            guard = isArray,
            completion = completion,
        ),
    ),
    residualGuard = ctx.mkNot(isArray),
)
```

Model authors are responsible for making successor guards and the residual guard disjoint and exhaustive. This
property belongs in focused model tests; the dispatcher does not invoke the solver a second time merely to validate a
model on every call.

## When to write an intrinsic

An intrinsic directly builds guarded successors and symbolic-memory operations in Kotlin. Use it only for an operation
that TypeScript cannot express without losing symbolic efficiency or correctness.

`Array.shift` is the built-in example because shifting a symbolic array is naturally represented by one
`memory.memcpy` operation.

Good intrinsic candidates include:

- bulk symbolic-memory copy or fill;
- symbolic collection primitives;
- solver operations unavailable in the modeled language;
- type-system operations that cannot be represented faithfully by ordinary code.

Do not write an intrinsic merely because a library method is stateful.

## Source-model migration

A source model uses the same `TsUnknownCallModel` object and the same ID, target, successor, and residual contract.
The source-model work in PR #380 should extend a successor completion with the EtsIR entry point and resolved inputs,
make the model's EtsIR files visible in the analysis scene, and enter that method through the regular interpreter.
Receiver binding, arguments, returns, exceptions, heap changes, aliases, and nested calls then use normal interpreter
semantics. They must not be reimplemented in a source-specific dispatcher or backend registry.

The model checks its supported domain before entering EtsIR. An unsupported call returns `null`; a guarded supported
subdomain uses the complementary residual guard and the same configured fallback. Recursive redirection is prevented
by tracking the active model ID in execution state, not by creating a second catalog.

`Array.pop` is the source-model example. Its TypeScript body uses indexing and `length`; it must not call `pop` again.
The existing `Array.shift` intrinsic remains the example for engine-only symbolic-memory `memcpy`.

## Dynamic receivers

A method name does not prove the receiver type. In particular, `value.shift()` may call a user-defined property rather
than `Array.prototype.shift`.

Use this decision rule:

| Receiver knowledge | Action |
| --- | --- |
| Definitely the modeled built-in receiver type | Apply the model. |
| Definitely another type | Return `null`; use fallback. |
| Possibly the modeled type, with a trustworthy built-in target | Use a type guard and residual complement. |
| `any`/unknown without proof of the built-in target | Return `null`; use fallback. |

Never choose `typeStreamOf(receiver).firstOrNull()` as proof. It returns one possible type, not necessarily the only
possible type. Use a statically proven type, `singleOrNull()` where uniqueness is guaranteed, or an explicit symbolic
type guard.

## Fingerprints

The catalog sorts enabled models by ID and hashes their length-prefixed IDs. Therefore model registration order does
not affect the fingerprint and ambiguous concatenations cannot collide merely because of ID boundaries.

The fingerprint identifies the frozen enabled model set for one run. It is not a version and must not be used as a
manually maintained configuration value. Experiment metadata records the tool revision separately. If model source
can change independently of that revision, the runner also records a content hash for the external source or generated
artifact; that content identity is experiment metadata, not another model ID, version, or compatibility setting. Keep
the catalog fingerprint based only on enabled model IDs rather than adding implementation-specific fingerprint fields
to the common model contract.

## Observation

Every applied model or fallback produces `TsUnknownCallEvent` through `TsInterpreterObserver.onUnknownCall`.

- `ModelApplied(modelId)` identifies the semantic model.
- `ResidualFallback(policy)` records the effective fallback.
- `event.outcome` is derived from the decision and is not stored as a second independent value.

Observer failures are logged and cannot alter symbolic exploration.
