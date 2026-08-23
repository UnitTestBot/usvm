# USVM TypeScript property-based testing

`usvm-ts-pbt` is the Kotlin-owned integration layer for concrete property-based testing backends and USVM.
Kotlin defines each property once; fast-check is the first concrete backend.

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
counterexample, run/skip/shrink counts, failure details, and elapsed time.

Predicate falsification and a timeout reported by fast-check are normal `FAILURE` results. Invalid input,
entry-point, process, and transport failures throw `PbtBackendException`.

Synchronous entry points must return a boolean directly. Asynchronous entry points must return an awaitable that
resolves to a boolean. A false precondition is passed to fast-check as a skipped input. Generation, replay, explicit
examples, checking, and shrinking retain fast-check semantics.

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
  --num-runs 1000
```

Use `--help` for the complete option list. `--source-root` and `--registry` are repeatable. Without `--registry`,
all providers run in registry-ID order; without `--property`, all selected properties run in registry order.
Replay paths and explicit examples require exactly one selected property.

The CLI writes a JSON array of results to stdout. Exit code `0` means every property succeeded, `1` means at least
one property failed, and `2` means a CLI, registry, validation, backend, or transport error. Exit-code-2 diagnostics
are written as one JSON object to stderr.

## Private fast-check adapter

The private adapter uses `tsx` to import TypeScript source and delegates each property run to `fc.check`. Gradle
builds and packages the adapter with its runtime dependencies; execution does not install or download packages.
Distribution archives are specific to the OS and architecture in their filename. Node.js 18.18 or newer is
required.

Each entry-point module must resolve to exactly one regular file below the supplied source roots. Missing,
ambiguous, escaping, or invalid exports are reported as typed backend errors. Kotlin supervises one Node process
per property and terminates a process that exceeds the configured timeout plus a short transport grace period.

## Verification

Requires JDK 11, Node.js 18.18 or newer, npm, and the repository Gradle wrapper.

```shell
npm ci --prefix usvm-ts-pbt/fast-check-adapter --ignore-scripts
npm test --prefix usvm-ts-pbt/fast-check-adapter

env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:check
```

To substitute a local JacoDB checkout, add `-PuseLocalJacodb=/absolute/path/to/jacodb` to the Gradle command.
