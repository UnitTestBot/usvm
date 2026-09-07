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
    enabledUnknownCallModelIds = setOf("ts.array.pop"),
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

Use the model's `id`, for example `ts.array.pop`. A target method name, class name, source filename, or artifact hash is
not a model ID.

The built-in catalog currently contains:

| ID | Implementation | Accepted calls |
| --- | --- | --- |
| `ts.array.shift` | Kotlin intrinsic using symbolic-memory `memcpy` | Zero-argument `shift` on a definitely one-dimensional array. |
| `ts.array.pop` | TypeScript/EtsIR body | Zero-argument `pop` on a statically proven `number[]` receiver that also satisfies the symbolic runtime type guard. |

An `any`/unknown receiver, a fake-value wrapper, and a non-array receiver do not become applicable merely because the
method is named `pop` or `shift`; they use fallback. A definitely-array `shift` receiver with an unresolved element sort
remains applicable and uses the fake-value representation described below. An array outside the `pop` model's
`number[]` domain uses fallback.

### `unknownCallFallback`

The fallback is applied when:

- no enabled model target matches the call;
- the selected model returns `null` because it cannot safely handle the concrete inputs;
- a model returns a satisfiable `residualGuard`;
- recursive redirection attempts to enter the same model again.

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

- `ts.array.pop`
- `ts.array.shift`
- `node.buffer.copy`

The ID is used for configuration, observer events, recursion prevention, and catalog fingerprints. Do not include:

- an implementation mechanism such as `intrinsic` or `ets-ir`;
- a source or EtsIR hash;
- a version number;
- a supported-domain label.

Keep the same ID when an equivalent model moves from Kotlin to TypeScript.

### Choosing a target

`TsUnknownCallTarget` matches stable call metadata declaratively:

```kotlin
TsUnknownCallTarget(
    methodName = "pop",
    failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
)
```

Only `methodName` is required. Add `enclosingClassName` or `failureReason` when the method name alone is too broad.
The catalog rejects overlapping enabled targets before execution, so catalog order is never a priority rule.

The target identifies a call family. State-dependent checks, such as the receiver's symbolic runtime type, belong in
`apply` or in an EtsIR model's domain guard.

The built-in array targets intentionally combine the method name with `PARTIAL_APPROXIMATION` instead of a class name.
That failure reason is emitted only after the regular approximation path has classified the receiver as an
`EtsArrayType`. Calls on `any`/unknown receivers reach another failure reason and cannot match these targets. Each model
still validates the resolved receiver and its supported domain before changing memory. `shift` accepts an unresolved
element sort, while `pop` currently accepts only `number[]`.

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

## TypeScript bodies and intrinsics

Use a TypeScript body by default. Use a Kotlin intrinsic only for an operation that TypeScript cannot express without
losing symbolic efficiency or correctness.

### TypeScript/EtsIR model

A TypeScript model is ordinary source code:

```typescript
export class ArrayModels {
    static pop(receiver: number[]): number | undefined {
        const length = receiver.length;
        if (length === 0) {
            return undefined;
        }

        const result = receiver[length - 1];
        receiver.length = length - 1;
        return result;
    }
}
```

Load the source and put the resulting model directly in the catalog:

```kotlin
val artifact = loadEtsIrUnknownCallModelArtifact(
    sourcePath = modelPath,
    entryPointClassName = "ArrayModels",
    entryPointMethodName = "pop",
)

val model = TsEtsIrUnknownCallModel(
    id = "ts.array.pop",
    target = TsUnknownCallTarget(methodName = "pop"),
    artifact = artifact,
    domainGuard = numberArrayGuard,
)

val catalog = TsUnknownCallModelCatalog(models = listOf(model))
```

The normal EtsIR interpreter executes the body. Receiver and arguments become entry-point parameters; ordinary return,
exception, field and array writes, and reference aliases flow back through the normal call stack.

The entry point must be static and have a non-empty body. Its parameter count must equal the resolved receiver plus
argument count. Unresolved inputs or an arity mismatch make the model not applicable.

The domain guard has three useful outcomes:

| Guard | Result |
| --- | --- |
| Concrete `false` | The model is not applicable; fallback handles the complete state. |
| Concrete `true` | The interpreter enters the TypeScript body; there is no residual state. |
| Symbolic expression | The true branch enters the body and the complementary branch uses fallback. |

### Kotlin intrinsic

An intrinsic is simply another `TsUnknownCallModel` implementation. It directly builds guarded successors and symbolic
memory operations.

`Array.shift` is the built-in example because shifting a symbolic array is naturally represented by symbolic-memory
`memcpy` operations. A resolved element sort uses one array region. A symbolic array with an unresolved element sort
uses the boolean, number, and address regions that back a fake value; its removed element is materialized before
forking so the exactly-one type constraint and updated solver models are inherited by every successor. In contrast,
`Array.pop` is expressed as the TypeScript body shown above.

Good intrinsic candidates include:

- bulk symbolic-memory copy or fill;
- symbolic collection primitives;
- solver operations unavailable in TypeScript;
- type-system operations that cannot be represented faithfully in EtsIR.

Do not write a Kotlin intrinsic merely because a library method is stateful. If ordinary TypeScript can express the
semantics, keep the model in TypeScript.

## Dynamic receivers

A method name does not prove the receiver type. In particular, `value.pop()` may call a user-defined property rather
than `Array.prototype.pop`.

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

## Nested calls and recursion

Unknown calls made inside a TypeScript model body use the same catalog and fallback as the original program. This lets
source models compose with other source models and intrinsics.

The state tracks each active model ID together with its call-stack depth. If the same model would redirect recursively,
lookup declines that redirection and fallback is applied instead of entering an infinite loop.

Do not implement `Array.pop` by calling `receiver.pop()` inside its own model body. Implement it through `length` and
indexed access, as in the example above.

## Artifacts and fingerprints

The loader snapshots the source bytes, invokes the native JacoDB TypeScript frontend, and rejects source mutation during
generation. The resulting artifact records source and EtsIR SHA-256 hashes for reproducibility.

The catalog sorts enabled models by ID and hashes their length-prefixed IDs. Therefore model registration order does
not affect the fingerprint and ambiguous concatenations cannot collide merely because of ID boundaries. The
fingerprint identifies the frozen enabled model set for one run. It is not a version and must not be used as a manually
maintained configuration value. Experiment metadata records the tool revision separately. If model source can change
independently of that revision, the runner also records the artifact's content hashes as experiment metadata; those
hashes are not another model ID, version, compatibility setting, or part of the common model contract.

EtsIR files are merged into the analysis scene by file signature. Reusing the same file object is deduplicated;
distinct files with the same signature are rejected.

## Observation

Every applied model or fallback produces `TsUnknownCallEvent` through `TsInterpreterObserver.onUnknownCall`.

- `ModelApplied(modelId)` identifies the semantic model.
- `ResidualFallback(policy)` records the effective fallback.
- `event.outcome` is derived from the decision and is not stored as a second independent value.

Observer failures are logged and cannot alter symbolic exploration.
