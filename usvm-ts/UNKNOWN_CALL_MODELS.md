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
    unknownCallModelSelection = TsUnknownCallModelSelection.Only(setOf("ts.array.pop")),
    unknownCallFallback = TsResidualCallPolicy.STOP_PATH,
)
```

### `unknownCallModelSelection`

This is the only model-selection setting.

| Value | Meaning |
| --- | --- |
| `TsUnknownCallModelSelection.All` | Enable every built-in model. This is the default. |
| `TsUnknownCallModelSelection.Only(emptySet())` | Disable every built-in model. |
| `TsUnknownCallModelSelection.Only(setOf("id", ...))` | Enable the listed built-in model IDs and their declared dependencies. |

Unknown IDs are rejected when the machine creates its immutable per-run catalog. The selected models are captured at that
point, so later mutations of the selection set cannot change an active run.

Use the model's `id`, for example `ts.array.pop`. A target method name, class name, source filename, or artifact hash is
not a model ID.

Built-ins are singleton models or families implementing the sealed `TsBuiltInUnknownCallModel` interface in the
`org.usvm.machine.call.intrinsic` package. Kotlin's sealed-subclass metadata discovers them automatically; a family
supplies its parameterized models without a separate registry. Discovery and the default catalog are computed once.

The built-in catalog includes the following public APIs and their internal storage primitives. Enumerate
`TsBuiltInUnknownCallModels.catalog().modelIds` for the exact IDs in a build.

| ID | Implementation | Accepted calls |
| --- | --- | --- |
| `ts.array.shift` | Kotlin intrinsic using symbolic-memory `memcpy` | Zero-argument `shift` on a definitely one-dimensional array. |
| `ts.array.isArray` | Kotlin runtime-type primitive | The genuine global Array predicate, including null, undefined and fake-value wrappers. |
| `ts.array.fromLength` | TypeScript/EtsIR body with a heap-allocation primitive | Genuine callable `Array(length)` with one numeric argument. Valid lengths up to 16 allocate holes that read as `undefined`; larger valid lengths use fallback. |
| `ts.array.pop` | TypeScript/EtsIR body | Zero-argument `pop` on a definitely one-dimensional array that also satisfies the symbolic runtime type guard. |
| `ts.array.includes`, `ts.array.indexOf`, `ts.array.lastIndexOf` | TypeScript/EtsIR bodies | One-dimensional arrays and numeric positions, with the missing-slot exclusions below. |
| `ts.array.push`, `ts.array.fill`, `ts.array.reverse`, `ts.array.unshift`, `ts.array.slice`, `ts.array.concat` | TypeScript/EtsIR bodies with storage growth/allocation primitives | One-dimensional arrays with length/result at most 16. Push/unshift accept up to three arguments; resolved-sort arrays require matching element sorts, while mixed arrays retain runtime value kinds. Concat accepts one same-type array. Except push and full-range `fill(value)`, these operations require a current dense-array proof. |
| `ts.string.charAt`, `ts.string.charCodeAt`, `ts.string.includes`, `ts.string.indexOf`, `ts.string.lastIndexOf`, `ts.string.startsWith`, `ts.string.endsWith` | TypeScript/EtsIR bodies | Strings in initialized UTF-16 storage, including symbolic code units and numeric positions. |
| `ts.string.slice`, `ts.string.substring`, `ts.string.trim`, `ts.string.trimStart`, `ts.string.trimEnd` | TypeScript/EtsIR bodies | UTF-16 range copying; substring clamps/swaps bounds, and trim uses the ECMAScript whitespace set. |
| `ts.string.replaceAll` | TypeScript/EtsIR body | String receiver, string search and string replacement; non-overlapping UTF-16 matches, empty search and ECMAScript dollar substitutions for a dollar sign, the match, its prefix and its suffix. RegExp, callbacks and argument coercions use fallback. |
| `ts.string.toLowerCase`, `ts.string.toUpperCase` | TypeScript/EtsIR bodies | ASCII strings of at most 16 code units; other strings use residual fallback. |
| `ts.math.abs`, `ts.math.ceil`, `ts.math.floor`, `ts.math.max`, `ts.math.min`, `ts.math.round`, `ts.math.sqrt`, `ts.math.trunc` | Kotlin FP primitives | Numeric arguments; dynamic coercions use fallback. |
| `ts.number.isFinite`, `ts.number.isInteger`, `ts.number.isNaN`, `ts.number.isSafeInteger` | Kotlin FP/type primitives | Non-coercing Number predicates, including runtime-kind guards. |
| `ts.error.constructor` | TypeScript/EtsIR body | Genuine `new Error(message)` with one string argument; initializes `name` and `message`. Other arities, coercions, subclasses, `cause` and stack inspection are outside this model. |
| `ts.date.*` (38 IDs) | TypeScript/EtsIR bodies | Numeric Date construction, `UTC`, fixed-clock `now`, getters, setters, `valueOf`, and source `toISOString`; see the Date boundary below. |

Matching standard calls are assumed to refer to genuine builtins. Monkey patching and prototype replacement are
outside this experiment; no runtime provenance protocol is imposed. Receiver and argument checks establish the
memory representation and supported input domain.

The September 2026 corpus census contains 22,769 call/constructor sites. Its 49-API shortlist accounts for 1,026
sites: Number/Math (275), new Array/String searches (130), existing pop/shift (52), and Date (569). Every shortlisted
API name has a catalog entry. Eleven adjacent APIs add 280 census sites; `setUTCMinutes` and `setUTCMilliseconds`
complete the numeric UTC setter family but have no sites in this census. These 1,306 associated sites are
an inventory count, **not executed or replay-confirmed coverage**: imports, input representation, fallback domains,
and other unsupported operations can still prevent execution. The wider research inventory contains 271
unambiguous standard/host API names over 7,720 sites; most are not implemented by this catalog.

The common instance-call pipeline splits fake-value wrappers and conditional references under their runtime-kind
and branch guards before selecting an approximation or resolving a method. A wrapped array can therefore use the
model, including through an `any` alias. An unknown or non-array receiver does not become an array merely because
the method is named `shift`. A definitely-array receiver with an unresolved element sort remains applicable and uses
the fake-value representation described below.

Dense input proofs retain snapshots of the array length and element storage regions. A later write invalidates the
proof conservatively, including writes to another array sharing a region. Consequently, a chain of individually
modeled array methods can still use fallback after its first mutation or copy.

### Runtime limitations and experimental outcomes

`TsInterpreterObserver.onRuntimeFeatureLimitation` records feasible paths stopped by bounded array storage, such as
named-property access, unsupported length growth, or assigning a runtime kind absent from a typed array's storage.
Reads may return an element or `undefined`; subsequent numeric operations and typed writes preserve that runtime-kind
guard. TypeScript `as` and angle-bracket assertions are erased and never change a value or constrain its runtime kind.
These events are separate from unknown-call model decisions.
The Calls runner writes them synchronously and reports `RUNTIME_LIMITATION` when search exhausts after such a stop
without reaching the target. An actual timeout remains `TIMEOUT`; a reached target still requires original-source replay.

Calls preflight checks input binding, source/IR target mapping and known unsupported IR features before choosing a
model/fallback profile. Excluded functions remain in the corpus support ledger. Comparative runs use a frozen common
target set; failures discovered after that freeze remain scheduled failures. The analysis budget is 30 seconds per
target, profile and seed; JVM/frontend startup and original-source replay have separate recorded limits. Readiness
traverses reachable same-file callees and the initializers triggered by their static-field accesses.

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
    val requiredModelIds: Set<String>

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

The ID is used for configuration, observer events, and recursion prevention. Do not include:

- an implementation mechanism such as `intrinsic` or `ets-ir`;
- a source or EtsIR hash;
- a version number;
- a supported-domain label.

Keep the same ID when an equivalent model moves from Kotlin to TypeScript.

Selecting models with `TsUnknownCallModelSelection.Only` expands `requiredModelIds` transitively and sorts the final
catalog by ID. This keeps a high-level source model usable when it calls helper models. Missing dependency IDs are
rejected while building the catalog; an explicitly empty selection remains empty.

### Choosing a target

`TsUnknownCallTarget` matches stable call metadata declaratively:

```kotlin
TsUnknownCallTarget(
    methodName = "pop",
    failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
)
```

Only `methodName` is required. Add `enclosingClassName` or `failureReason` when the method name alone is too broad.
The catalog indexes method names, failure reasons, and enclosing classes. Overlapping enabled targets fail while
building that index; lookup returns either one model or no match, and never hides ambiguity. Catalog order is never
a priority rule. The enabled model set is frozen and sorted by ID when the catalog is built.

The target identifies a call family. State-dependent checks, such as the receiver's symbolic runtime type, belong in
`apply` or in an EtsIR model's domain guard.

The built-in array targets combine the method name with `PARTIAL_APPROXIMATION`. Array and String methods with the
same name also use a canonical enclosing class at this boundary. The failure reason is emitted only after the regular
approximation path has classified the normalized receiver by its storage type. An `any` alias of a known array can
satisfy that check; a receiver without array-type evidence cannot. The model still validates the resolved receiver
and array shape before changing memory.

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
    static pop(receiver: any[]): any {
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
    domainGuard = arrayGuard,
)

val catalog = TsUnknownCallModelCatalog(models = listOf(model))
```

The normal EtsIR interpreter executes the body. Receiver and arguments become entry-point parameters; ordinary return,
exception, field and array writes, and reference aliases flow back through the normal call stack.

Array indexing and `length` assignment use the receiver's storage type. Writing `length` supports integral values from
zero through the current length, within the configured array-size limit. Growth remains unsupported because the
engine does not represent newly created holes; those paths are pruned.

`Array.pop`, `indexOf`, `includes`, and `lastIndexOf` share one source-model family. Search offsets accept numbers and
the standard omitted or explicit-`undefined` defaults; other dynamic coercions use fallback. Array memory has no slot
presence bit, so a hole can look like a typed default. Searches for `0` or `false` therefore use fallback, as do
`indexOf(undefined)` and `lastIndexOf(undefined)`. `includes(undefined)` is accepted only for address or unresolved
storage; numeric and boolean storage use fallback because their holes currently read as typed defaults. Symbolic
numeric and boolean search values use a guarded model branch outside the typed default and residual fallback on the
unsupported default. Fake-wrapped dynamic search values use fallback. Position normalization depends on
`ts.math.floor`.

The String source family implements `charAt`, `charCodeAt`, `indexOf`, `lastIndexOf`, `includes`, `startsWith`, and
`endsWith`. Its TypeScript algorithms depend on atomic length, UTF-16 code-unit read, and one-code-unit construction
models, plus `ts.math.floor` for positions. Current symbolic String parameters do not initialize backing character
storage, so receivers and search strings must be initialized concrete constants. `charAt` also requires a concrete
index because a dynamically constructed one-code-unit String does not yet participate in value-based String equality.
Numeric-result and predicate methods can still use symbolic numeric positions over concrete strings.

The entry point must be static and have a non-empty body. After its input adapter handles optional arguments or drops
non-semantic namespace receivers, its parameter count must equal the adapted input count. Unresolved required inputs
or an arity mismatch make the model not applicable.

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
`memcpy` operations. A resolved element sort uses one canonical array region. Unresolved elements use three
payload regions (boolean, number, and address) and two boolean kind selectors. Reference kind is derived as
`!(booleanKind || numberKind)`; the exactly-one constraint excludes both primitive selectors being true. Default
allocated slots therefore represent references, including undefined. `Unknown[]` names the canonical reference
storage region, not a claim that every TypeScript array has unresolved elements.

`copyArrayElements` moves all five regions for unresolved arrays, including allocated arrays created by `slice` or
`concat`. `reverse` applies the same index permutation to every region. Scalar writes store complete fake wrappers
in the reference region, overriding older payloads and selectors. Reads and test reconstruction use the same reader.
The removed `shift` element is materialized before forking so its kind constraint and updated solver models are
inherited by every successor.

`concat` handles arrays with compatible storage sorts and scalar elements that fit the destination. Calls requiring
conversion between storage sorts, or runtime spreading of a fake/untyped argument, use normal call resolution and
fallback. Existing `fill` bounds and the finite `reverse`/`fill` caps remain approximation limitations.
Array reads, writes, length access, and `shift` use the storage type known to symbolic memory when it is unique.
Widening a local from `number[]` to `any[]` therefore keeps the same element and length regions.

In contrast, `Array.pop` is expressed as the TypeScript body shown above.

### Date experiment boundary

The built-in Date family keeps Gregorian calendar arithmetic, component overflow, leap years, and TimeClip in
`DateModels.ts`. Kotlin only routes calls, injects the experiment clock, and exposes the model's numeric timestamp
slot on a Date receiver. Calendar division and truncation use the declared `ts.math.floor` dependency.

The current experiment has these explicit limits:

- local getters, setters, and numeric component constructors use UTC; `getTimezoneOffset()` returns zero for valid
  dates and NaN for invalid dates. DST behavior is outside the model domain;
- `Date.now()` and `new Date()` require `TsOptions.dateNowMilliseconds`; one fixed value is reused throughout the
  analysis, and both calls use fallback when it is absent;
- one-argument construction supports numeric timestamps only; string parsing and copying another Date are outside
  the model domain;
- symbolic string formatting is not claimed: `toISOString()` is a source implementation for supported concrete
  execution, while symbolic string conversion remains subject to the engine's string limitations. Invalid ISO
  formatting reaches the unsupported nested `RangeError` constructor and the configured fallback.

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

Instance calls share receiver normalization before built-in approximations and ordinary method lookup. It reuses
`extractValue` to select a fake payload together with its kind constraint and `splitUHeapRef` to retain conditional
reference guards. Each feasible alternative continues through the existing virtual-call statement. This preserves
supported primitive calls such as `valueOf` and the existing `toString` approximation, while null and undefined
receivers take the property-access exception path. Other primitive calls use `NON_REFERENCE_RECEIVER` fallback.
Receiver normalization does not make the existing built-in approximations exact.

Use this decision rule after normalization:

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

Declare every nested semantic-model call in `requiredModelIds`. A selection containing only the high-level API then
expands to its helpers before the machine scene is materialized.

The state tracks each active model ID together with its call-stack depth. If the same model would redirect recursively,
lookup declines that redirection and fallback is applied instead of entering an infinite loop.

Do not implement `Array.pop` by calling `receiver.pop()` inside its own model body. Implement it through `length` and
indexed access, as in the example above.

## Artifacts

The loader invokes the native JacoDB TypeScript frontend and keeps an immutable EtsIR JSON snapshot. Each machine
materializes its own EtsIR objects from that snapshot so interpreter-local state cannot leak between analyses.

EtsIR files are merged into the analysis scene by file signature. Reusing the same file object is deduplicated;
distinct files with the same signature are rejected, including collisions with application and SDK files.

## Observation

Every completed model or fallback decision produces `TsUnknownCallEvent` through `TsInterpreterObserver.onUnknownCall`.
A model event is emitted once after all satisfiable successor callbacks complete. If a callback throws, dispatch does
not report success for the discarded step. A partially supported call may report both a model and a fallback event.

- `ModelApplied(modelId)` identifies the semantic model.
- `ResidualFallback(policy)` records the effective fallback.
- `event.outcome` is derived from the decision and is not stored as a second independent value.

Observer failures are logged and cannot alter symbolic exploration.
