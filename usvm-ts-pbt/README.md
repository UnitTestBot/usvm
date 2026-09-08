# USVM TypeScript property-based testing

`usvm-ts-pbt` is the Kotlin-owned integration layer for concrete property-based testing backends and USVM.
Kotlin defines each property once; fast-check is the first concrete backend.

See [DESIGN.md](DESIGN.md) for component responsibilities, Kotlin–TypeScript data flow, process supervision, and
runtime packaging.

## Kotlin property model

```kotlin
val property = PropertyDefinition(
    id = PropertyId("array.reverse-twice"),
    inputs = listOf(
        PropertyInput(
            name = "values",
            domain = ArrayDomain(
                element = IntegerDomain(min = -100, max = 100),
                minLength = 0,
                maxLength = 20,
            ),
        ),
    ),
    predicate = TypeScriptEntryPoint(
        module = "properties/arrays.ts",
        exportName = "reverseTwicePreservesValues",
    ),
)
```

Input order is significant because TypeScript parameters are positional. The referenced function remains in the
user's TypeScript source tree:

```typescript
export function reverseTwicePreservesValues(values: number[]): boolean {
  return values.toReversed().toReversed().every((value, index) => value === values[index]);
}
```

| Domain           | Semantics and defaults                                                                             |
| ---------------- | -------------------------------------------------------------------------------------------------- |
| `BooleanDomain`  | JavaScript booleans                                                                                |
| `IntegerDomain`  | Inclusive signed 32-bit range; defaults to `Int.MIN_VALUE..Int.MAX_VALUE`                          |
| `NumberDomain`   | ECMAScript binary64; defaults to both infinities and `allowNaN = true`; bounded domains reject NaN |
| `StringDomain`   | Arbitrary UTF-16 code units; length is JavaScript `String.length`; defaults to `0..10`             |
| `ConstantDomain` | One tagged JavaScript primitive                                                                    |
| `OptionalDomain` | Nested domain plus exactly `undefined` or `null` as the nil value                                  |
| `TupleDomain`    | Non-empty ordered recursive domains                                                                |
| `ArrayDomain`    | Recursive element domain; defaults to length `0..10`                                               |

`JsConcreteValue` is a lossless tagged representation used for examples and counterexamples. It preserves
`undefined`, `null`, NaN, infinities, negative zero, and nested arrays.

The normative behavior of inputs, preconditions, predicates, mutation isolation, projection, search, and replay is
defined once in the [property execution contract](PROPERTY_EXECUTION_CONTRACT.md).

## Execute a property

`FastCheckBackend` accepts TypeScript source roots and loads `.ts` entry points directly. User projects do not
need a separate TypeScript compilation step or a path to the private adapter.

```kotlin
val backend = FastCheckBackend(
    sourceRoots = listOf(Path.of("/workspace/packages/core/src")),
)

val result = backend.run(
    property = property,
    configuration = PropertyRunConfiguration(
        seed = 42,
        numRuns = 1_000,
        timeoutMillis = 30_000,
    ),
)
```

The defaults are 100 successful runs and a 60-second timeout. Configuration also supports replay paths and
positional explicit examples. `PropertyRunResult` contains the property ID, status, actual seed, replay path,
counterexample, run/skip/shrink counts, failure details, elapsed time, and optional per-property coverage.

Predicate falsification, discard-budget exhaustion, and a timeout reported by fast-check are completed `FAILURE`
results with distinct `PropertyFailureKind` values. Only `PROPERTY` denotes a candidate violation;
`PRECONDITION_EXHAUSTED` and `TIMEOUT` do not. Invalid input, entry-point contract, process, and transport failures
throw `PbtBackendException`.

Generation, replay, explicit examples, checking, and shrinking use the same invocation path and follow the
[property execution contract](PROPERTY_EXECUTION_CONTRACT.md).

## Per-property TypeScript coverage

Coverage is a backend capability, not part of `PropertyDefinition` or `PropertyManifest`.
`PropertyBasedTestingBackend.coverageCapability` exposes the backend identity, backend version, and collector
before execution. `FastCheckBackend` reports c8 10.1.3. An unsupported backend reports
`coverage.unsupported` explicitly.

Kotlin requests coverage for one run through `PropertyRunConfiguration`:

```kotlin
val result = backend.run(
    property = property,
    configuration = PropertyRunConfiguration(
        seed = 42,
        coverageRequest = PropertyCoverageRequest(
            scopes = setOf(CoverageScope.SOURCE_UNDER_TEST),
            includePatterns = listOf("packages/core/**/*.ts"),
            excludePatterns = listOf("**/*.generated.ts"),
        ),
    ),
)
```

The default scope is `SOURCE_UNDER_TEST`. Available scopes are:

| Scope                        | Files retained after source-map remapping                                 |
| ---------------------------- | ------------------------------------------------------------------------- |
| `SOURCE_UNDER_TEST`          | Files below a source root except exact predicate and precondition modules |
| `PROPERTY_ENTRY_POINTS`      | Exact predicate and optional precondition modules                         |
| `GENERATED_BACKEND_WRAPPERS` | Files in the private adapter runtime outside `node_modules`               |
| `DEPENDENCIES`               | Executed files below `node_modules`                                       |

Include and exclude globs operate on original remapped paths, use `/` separators, and support `*`, `?`, and `**`.
An empty include list retains every file in a selected scope; exclude rules always win.

For every requested property Kotlin creates a unique c8 workspace, runs the private adapter as
`node c8.js ... node execution-cli.js`, decodes `coverage-final.json`, and removes the workspace. This isolation
prevents coverage from one property contaminating another. The adapter inherits the caller's current directory,
while an explicit empty c8 configuration prevents project-local c8 settings from altering collection. A falsified
property remains a completed Node run, so its artifact preserves coverage collected before falsification.

Before starting c8, Kotlin probes the configured Node executable once. Versions older than 18.18 are rejected with
`coverage.runtime.unsupported`; an unavailable or unparseable version uses `coverage.runtime.version-unavailable`.
The verified version is recorded in the artifact provenance without a second probe.

The artifact has kind `NODE_SOURCE` and contains backend/property identity, c8 and Node provenance, canonical
source roots, the original request, and deterministic per-file statement, function, and branch hits. Lines are
one-based and columns are zero-based, following Istanbul. Node source coverage remains separate from future EtsIR
replay coverage.

Missing or malformed reports use `coverage.report.missing` and `coverage.report.invalid`. Reports larger than
64 MiB are rejected as invalid before they are read. JavaScript below a TypeScript source root that c8 could not
remap produces `coverage.source-map.missing` or `coverage.source-map.invalid`; a missing packaged c8 runtime
produces `coverage.collector.not-found`.

## Property-to-EtsIR mapping

`PropertyEtsMapper` combines a backend-neutral `PropertyManifest`, an `EtsScene`, and optional
`PropertyCoverageArtifact` into one `PropertyEtsMappingArtifact` per property:

```kotlin
val mapping = PropertyEtsMapper(
    scene = etsScene,
    sourceRoots = sourceRoots,
).map(
    manifest = property.toManifest(),
    coverage = result.coverage,
)
```

Predicate and optional precondition exports are resolved independently, including named and bare-star TypeScript
re-exports and extensionless `.ts`, `.ets`, `.d.ts`, and directory-index module paths. Direct function exports map
only to file-level EtsIR methods; namespace-star exports are not treated as functions, bare-star exports do not
forward `default`, explicit runtime exports take precedence over bare-star exports, and duplicate re-export paths
to the same method collapse to one target. Type-alias exports do not mask bare-star runtime exports. The current
EtsIR export model preserves `isTypeOnly` independently of the declaration kind, so type-only named and star
re-exports do not mask a bare-star runtime fallback.
Every resolved entry point has explicit receiver, ordered input, and result bindings. The receiver uses stack slot
zero and property inputs follow it in manifest order. A coverage artifact for another property is rejected rather
than combined with the manifest.

Existing source roots and files are canonicalized through real paths, so symlinked frontend inputs align with
backend coverage; an unresolvable root is `UNSUPPORTED`. Istanbul's one-based lines and zero-based columns become
zero-based half-open ranges with UTF-16 offsets, matching TypeScript and EtsIR source spans. Recognized TypeScript
line terminators are CRLF, lone CR, LF, Unicode line separator (`U+2028`), and Unicode paragraph separator
(`U+2029`).
Statement ranges are compared with `EtsSourceSpan` origins. Several normalized EtsIR statements sharing one exact
origin remain one `EXACT` mapping with several targets; several distinct origins inside a covered range are
`AMBIGUOUS`.

Binary Istanbul branches map to `EtsIfStmt`. Arm zero is the true CFG successor and arm one is the false successor,
as recorded by `EtsMappingProvenance`. Other branch shapes are `UNSUPPORTED`; the mapper does not guess switch,
logical-expression, or backend-specific arm semantics.

Only one EtsIR condition from one source candidate is an `EXACT` branch mapping. Multiple conditions are
`AMBIGUOUS` even when they share one source origin, because a short-circuit expression can lower to conditions with
different CFG semantics.

The pinned c8/V8 collector reports one-arm `branch` records, which therefore remain unsupported for CFG-edge
mapping; statement coverage from the same artifact remains usable.

| Status        | Meaning                                                                                                                                             |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EXACT`       | One source identity was established; normalized statements may produce several EtsIR targets with that shared identity.                             |
| `AMBIGUOUS`   | Several entry points, source origins, source candidates, or branch conditions match, and every candidate is preserved.                              |
| `UNMAPPED`    | The input is supported, but no EtsIR target matches it.                                                                                             |
| `UNSUPPORTED` | The input cannot be interpreted safely, for example because coverage, source text, origins, coordinates, bindings, or branch shape are unsupported. |

Stable mapping diagnostics include `mapping.entry-point.unmapped`, `mapping.entry-point.ambiguous`,
`mapping.entry-point.bindings.unsupported`, `mapping.coverage.unavailable`,
`mapping.coverage.property-id.mismatch`, `mapping.statement.unmapped`, `mapping.statement.ambiguous`,
`mapping.branch.unmapped`, `mapping.branch.ambiguous`, `mapping.branch.shape.unsupported`,
`mapping.branch.cfg.unsupported`,
`mapping.source.unavailable`, `mapping.source.location.unsupported`, and
`mapping.source-origins.unsupported`, and `mapping.source-root.unsupported`. Backend provenance is preserved
separately from mapping provenance and
backend diagnostics are copied without reinterpretation.

## USVM projection and property search

`UsvmProjectionCapabilityResolver` compares the declared domains with the exact EtsIR parameter bindings.
Booleans, bounded integers and numbers, supported primitive constants, optionals, bounded tuples, and bounded
arrays are projected by `UsvmDomainProjector`. Strings are an explicit over-approximation: USVM constrains their
type and UTF-16 length but not their contents. Nested arrays, incompatible EtsIR types, and collections above
`UsvmProjectionOptions.maxSymbolicCollectionLength` are unsupported with stable diagnostics.

`UsvmPropertyProjector` executes a mapped synchronous precondition over projected inputs. It reports accepted and
rejected domains separately; a reachable exception or non-boolean result is `PROPERTY_ERROR`, solver uncertainty is
`SOLVER_UNKNOWN`, and async, non-exact, or unsupported residual-call execution is `UNSUPPORTED`.

`UsvmPropertySearcher` evaluates the mapped precondition and predicate in one symbolic state. A false precondition
is `PRECONDITION_REJECTED` when it excludes the complete projected domain. A precondition exception or non-boolean
result is `PROPERTY_ERROR`. Predicate `false` and escaping predicate exceptions are `VIOLATION_REACHED`, while a
non-boolean predicate is `PROPERTY_ERROR`. Timeout, solver uncertainty, unsupported execution, engine failure, and
input-resolution failure retain distinct statuses and are never treated as proof or as violations.

The shared fixture in `src/test/resources/properties/contract/PropertyExecutionContract.ts` is executed by both
`FastCheckBackend` and the USVM projection/search path. It covers precondition admission, rejection, exception and
non-boolean results, plus false, throwing, literal-boolean-typed, never-typed, and non-boolean predicates. A shared
mutation regression also verifies that a USVM candidate is reconstructed from the input before predicate mutation
and reproduces through fast-check. Special values, alias preservation, mutation isolation, shrinking, and replay
remain covered at the concrete invocation boundary.

## Registries and CLI

The CLI loads Kotlin property registries through `ServiceLoader`:

```kotlin
class ExamplePropertyRegistryProvider : PropertyRegistryProvider {
    override val registryId: String = "example"

    override fun load(): PropertyRegistry = PropertyRegistry(
        listOf(arrayReverseTwiceProperty, anotherProperty),
    )
}
```

Register the provider in
`META-INF/services/org.usvm.ts.pbt.registry.PropertyRegistryProvider` using its fully qualified class name. Put the
provider JAR on the application classpath, then run:

```shell
java -cp '/opt/usvm-ts-pbt/lib/*:/workspace/example-properties.jar' \
  org.usvm.ts.pbt.cli.FastCheckCliKt \
  --source-root /workspace/packages/core/src \
  --registry example \
  --property array.reverse-twice \
  --seed 42 \
  --num-runs 1000 \
  --coverage \
  --coverage-scope source-under-test \
  --coverage-exclude '**/*.generated.ts'
```

Use `--help` for the complete option list. `--source-root` and `--registry` are repeatable. Without `--registry`,
all providers run in registry-ID order; without `--property`, all selected properties run in registry order.
Replay paths and explicit examples require exactly one selected property.

`--coverage` enables collection. `--coverage-scope`, `--coverage-include`, and `--coverage-exclude` are repeatable;
scope and path options require `--coverage`. Without an explicit scope, source-under-test coverage is collected.

The CLI writes a JSON array of results to stdout. Exit code `0` means every property succeeded, `1` means at least
one property failed, and `2` means a CLI, registry, validation, backend, or transport error. Exit-code-2 diagnostics
are written as one JSON object to stderr.

## Verification

Requires JDK 11, Node.js 18.18 or newer, npm, and the repository Gradle wrapper.
The private distribution pins c8 10.1.3 because it supports the module's Node 18 floor.

```shell
npm ci --prefix usvm-ts-pbt/fast-check-adapter --ignore-scripts
npm test --prefix usvm-ts-pbt/fast-check-adapter

env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:check
```

To substitute a local JacoDB checkout, add `-PuseLocalJacodb=/absolute/path/to/jacodb` to the Gradle command.
