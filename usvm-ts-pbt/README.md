# USVM TypeScript property-based testing

`usvm-ts-pbt` is the Kotlin-owned integration layer for concrete property-based testing backends and USVM.
Kotlin defines each property once; fast-check is the first replaceable concrete backend.

## Architecture

```text
Kotlin PropertyDefinition
        |
        +--> versioned PropertyManifest
        |
        +--> PBT projection ----------> private fast-check Node adapter
        |
        +--> symbolic projection -----> USVM (#351)
```

Kotlin owns property identity, ordered inputs, domain semantics, TypeScript entry-point references, validation,
capability aggregation, and later orchestration. Common Kotlin code never contains `fc.Arbitrary` or another
backend-native generator type.

Predicate and precondition bodies remain exported TypeScript functions. Kotlin refers to each function by a
normalized project-relative module path, export name, and synchronous or asynchronous execution kind. Issue #347
validates and serializes those references but does not load or execute the functions.

## Kotlin property model

```kotlin
val property = PropertyDefinition(
    id = PropertyId("array.reverse-twice"),
    inputs = listOf(
        PropertyInput(
            name = "values",
            domain = ArrayDomain(IntegerDomain(-100, 100), minLength = 0, maxLength = 20),
        ),
    ),
    predicate = TypeScriptEntryPoint(
        module = "properties/arrays.ts",
        exportName = "reverseTwicePreservesValues",
    ),
)

val manifest = property.toManifest()
```

Input order is significant because TypeScript parameters are positional. Names are unique and are retained in
diagnostics and artifacts.

| Domain | Semantics and defaults |
| --- | --- |
| `BooleanDomain` | JavaScript booleans |
| `IntegerDomain` | Inclusive signed 32-bit range; defaults to `Int.MIN_VALUE..Int.MAX_VALUE` |
| `NumberDomain` | ECMAScript binary64; defaults to both infinities and `allowNaN = true`; bounded domains reject NaN |
| `StringDomain` | Arbitrary UTF-16 code units; length is JavaScript `String.length`; defaults to `0..10` |
| `ConstantDomain` | One tagged JavaScript primitive |
| `OptionalDomain` | Nested domain plus exactly `undefined` or `null` as the nil value |
| `TupleDomain` | Non-empty ordered recursive domains |
| `ArrayDomain` | Recursive element domain; defaults to length `0..10` |

`PropertyDomain` describes a set of allowed inputs. `JsConcreteValue` describes one concrete JavaScript value used as a
constant or returned sample; it is unrelated to JacoDB IR values. Its tagged encoding preserves `undefined`,
`null`, NaN, both infinities, and the raw IEEE-754 bits of finite numbers, including negative zero. Protocol
samples also use recursive tagged arrays so tuple and array values cross JSON without losing nested special
values. `ConstantDomain` still rejects composite values.

## Manifest and capability are separate

`PropertyManifest` is schema-versioned engine-neutral data. It contains property semantics and TypeScript
entry-point references, but no backend name, fast-check configuration, seed, replay path, shrink data, coverage,
or USVM expression.

`ProjectionCapability` is a backend-and-version-specific report with `EXACT`, `APPROXIMATE`, or `UNSUPPORTED`
level and stable diagnostics. Recursive composition selects the least capable child. A concrete projection that
is supported while the selected USVM projection is unsupported is classified by the pipeline as `CONCRETE_ONLY`;
that classification is not stored in the manifest.

## Private fast-check adapter

`fast-check-adapter` is a private TypeScript module pinned to fast-check 4.9.0. Gradle compiles it with `tsc` into
an ignored `dist` directory before Kotlin integration tests run. The adapter recursively reconstructs real
`fc.Arbitrary` objects from common domain descriptors. Kotlin invokes the compiled one-shot `sample` operation
over one JSON request on stdin and one JSON response on stdout. The adapter does not discover properties, load
predicates, run campaigns, select USVM, or orchestrate the pipeline.

Both manifest and protocol versions start at `1`. Kotlin validates outgoing request sizes and verifies process
exit status, JSON shape, protocol version, request identity, sample shape, and typed backend diagnostics.

## Extension rules

- A new PBT backend consumes the common manifest and implements projection/capability reporting. Existing
  `PropertyDefinition` instances and USVM code must not change for already-supported domains.
- A new common domain needs explicit Kotlin semantics and validation, serialization, a capability decision from
  every backend, and conformance tests.
- A backend-specific extension must be namespaced and must be reported as unsupported by backends that do not
  implement it.
- Backend-native arbitrary objects, arbitrary TypeScript closures, silent approximation, and backend defaults in
  the common model are rejected extension mechanisms.

## Verification

Requires JDK 11, Node.js 18.18 or newer, npm, and the repository Gradle wrapper. The full Gradle check installs and
compiles the pinned private adapter, runs its compiled Node tests, runs Kotlin/Node protocol tests, and retains the
native `ts-frontend` baseline from #346.

```shell
npm ci --prefix usvm-ts-pbt/fast-check-adapter --ignore-scripts
npm run build --prefix usvm-ts-pbt/fast-check-adapter
npm test --prefix usvm-ts-pbt/fast-check-adapter

env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:test

env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:clean :usvm-ts-pbt:check
```

To substitute a local JacoDB checkout, add
`-PuseLocalJacodb=/absolute/path/to/jacodb` to the Gradle command.

## Issue boundaries

- #348 loads TypeScript entry points and executes Kotlin definitions through `fc.check`.
- #349 records backend-neutral per-property coverage.
- #350 maps entry points and coverage locations to EtsIR.
- #351 projects common domains and preconditions into USVM.
- #352 searches for property violations with USVM.
- #353 replays USVM witnesses and delegates shrinking to a capable PBT backend.
- #354 assembles the Kotlin-orchestrated end-to-end pipeline.
- #355–#357 build runtime hints, benchmarks, and evaluation on backend-identified artifacts.
