# Kotlin-First Property Abstraction Design

**Issue:** [#347](https://github.com/UnitTestBot/usvm/issues/347)

**Status:** Kotlin-first architecture approved; implementation in progress

## Context

`usvm-ts-pbt` will combine USVM with concrete property-based testing engines. fast-check is the first concrete engine, but it is not the only possible PBT backend. USVM and the pipeline orchestration must remain usable with another backend without redefining properties or changing the common artifacts.

The existing baseline module from #346 contains Kotlin/JacoDB integration and a native `ts-frontend` smoke test. It deliberately does not port the historical custom generator, concrete interpreter, or shrinking implementation.

The original #347 design put the shared property API in TypeScript and let it construct both fast-check objects and a symbolic manifest. That makes fast-check and Node the architectural owner of the property definition. The revised architecture makes Kotlin the owner and treats fast-check as an adapter.

## Goals

1. Define a single Kotlin representation of a TypeScript property, its inputs, and its runtime entry points.
2. Keep the representation independent of fast-check, Node, and USVM implementation types.
3. Produce a versioned, serializable property manifest from the Kotlin definition.
4. Define a backend projection contract and capability reporting model.
5. Implement the initial projection from common domains to real fast-check `Arbitrary` objects through an internal Node adapter.
6. Preserve JavaScript primitive semantics across Kotlin/JSON/Node boundaries.
7. Validate definitions and protocol messages before a backend executes them.

## Non-Goals

Issue #347 will not implement:

- property registry discovery or campaign execution;
- `fc.check`, replay, or shrinking;
- coverage collection;
- TypeScript source-to-EtsIR entry-point resolution;
- USVM symbolic value construction or predicate execution;
- the end-to-end pipeline or public CLI;
- arbitrary fast-check combinators in the common property model.

Those responsibilities remain in #348–#354.

## Architectural Ownership

The dependency direction is fixed:

```text
Kotlin PropertyDefinition
        |
        +--> PropertyManifest
        |
        +--> PBT backend projection --> fast-check Node adapter
        |
        +--> symbolic projection -----> USVM (implemented in #351)
```

Kotlin owns:

- property identity and input ordering;
- domain semantics and constraints;
- TypeScript predicate and precondition references;
- manifest and protocol schemas;
- validation and capability aggregation;
- backend selection and orchestration in later issues.

The internal Node adapter owns only the fast-check projection. It does not discover properties, select execution modes, invoke USVM, or define common artifacts.

## Property Model

### Property definition

The public Kotlin model is immutable and serializable through a separate manifest projection:

```kotlin
data class PropertyDefinition(
    val id: PropertyId,
    val inputs: List<PropertyInput>,
    val predicate: TypeScriptEntryPoint,
    val precondition: TypeScriptEntryPoint? = null,
)

data class PropertyInput(
    val name: String,
    val domain: PropertyDomain,
)
```

Input order is semantically significant because TypeScript predicate parameters are positional. Input names are unique within a property and appear in diagnostics and artifacts.

`PropertyId` is a validated value object. Its canonical text matches `[A-Za-z0-9][A-Za-z0-9._/-]*`. IDs are stable across backends and runs.

### TypeScript entry points

Predicate and precondition bodies remain in TypeScript so concrete execution uses the original JavaScript semantics and USVM analyzes the same source:

```kotlin
data class TypeScriptEntryPoint(
    val module: String,
    val exportName: String,
    val executionKind: ExecutionKind = ExecutionKind.SYNC,
)

enum class ExecutionKind {
    SYNC,
    ASYNC,
}
```

`module` is a normalized, project-relative POSIX path. Absolute paths, empty path segments, and parent traversal are rejected. `exportName` must be a JavaScript identifier. The common model does not store a JavaScript closure.

An asynchronous entry point may be supported by a concrete backend and unsupported by USVM. This is represented by per-backend capability, not by changing the property definition.

## Domain Algebra

`PropertyDomain` is a sealed Kotlin hierarchy. It describes the valid value set and all constraints explicitly; backend defaults must not silently change property semantics.

The initial variants are:

```kotlin
sealed interface PropertyDomain

data object BooleanDomain : PropertyDomain

data class IntegerDomain(
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE,
) : PropertyDomain

data class NumberDomain(
    val min: JsNumber = JsNumber.NegativeInfinity,
    val max: JsNumber = JsNumber.PositiveInfinity,
    val allowNaN: Boolean = true,
) : PropertyDomain

data class StringDomain(
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_STRING_LENGTH,
) : PropertyDomain

data class ConstantDomain(
    val value: JsConcreteValue,
) : PropertyDomain

data class OptionalDomain(
    val value: PropertyDomain,
    val nil: JsConcreteValue = JsConcreteValue.Undefined,
) : PropertyDomain

data class TupleDomain(
    val elements: List<PropertyDomain>,
) : PropertyDomain

data class ArrayDomain(
    val element: PropertyDomain,
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_ARRAY_LENGTH,
) : PropertyDomain
```

`DEFAULT_MAX_STRING_LENGTH` and `DEFAULT_MAX_ARRAY_LENGTH` are both `10`. They are stable common-model constants, not values inherited from fast-check. A manifest always contains resolved length bounds, so another backend sees identical semantics.

`IntegerDomain` uses the signed 32-bit integer set, matching TypeScript numbers that are exact for all values in the range. `NumberDomain` describes ECMAScript binary64 values. An unbounded number domain includes finite values, both infinities, negative zero, and optionally NaN. Setting either bound to a value other than its default makes the domain bounded; bounded domains must set `allowNaN = false` and accept only values satisfying their declared inclusive bounds.

`StringDomain` contains arbitrary UTF-16 code-unit sequences, including valid surrogate pairs and unpaired surrogates. Length bounds count UTF-16 code units, matching JavaScript `String.length`. The fast-check adapter constructs this domain from arrays of integers in `0..0xffff` instead of inheriting changing `fc.string()` defaults.

The initial `ConstantDomain` supports JavaScript primitives only. Objects, functions, symbols, and bigints are rejected rather than coerced. `OptionalDomain.nil` must be either `JsConcreteValue.Undefined` or `JsConcreteValue.Null`; other sentinel values are rejected.

Tuple and array nesting is recursive. Cycles cannot occur because the model is immutable and value-based.

## JavaScript Value Encoding

Ordinary JSON cannot distinguish or preserve `undefined`, NaN, infinities, and negative zero. All values crossing a manifest or backend protocol use a tagged `JsConcreteValue` representation:

```json
{ "kind": "undefined" }
{ "kind": "null" }
{ "kind": "boolean", "value": true }
{ "kind": "string", "value": "text" }
{ "kind": "number", "value": "finite", "bits": "8000000000000000" }
{ "kind": "number", "value": "nan" }
{ "kind": "number", "value": "positive-infinity" }
{ "kind": "number", "value": "negative-infinity" }
{ "kind": "array", "elements": [{ "kind": "undefined" }, { "kind": "null" }] }
```

Finite doubles use their exact unsigned 64-bit hexadecimal IEEE-754 representation. This preserves negative zero and avoids decimal round-trip ambiguity. NaN uses one semantic tag because the pipeline does not expose NaN payloads.

`JsConcreteValue` represents one concrete JavaScript value rather than a domain or JacoDB IR value.
`JsConcreteValue.Array`
recursively encodes tuple and array samples crossing the Kotlin-to-Node protocol. It does not expand
`ConstantDomain`: constants remain restricted to JavaScript primitives and validation rejects a composite
constant.

## Property Manifest

`PropertyManifest` is the canonical engine-neutral serialization of a validated property definition:

```json
{
  "schemaVersion": 1,
  "propertyId": "array.reverse-twice",
  "inputs": [
    {
      "name": "values",
      "domain": {
        "kind": "array",
        "element": { "kind": "integer", "min": -100, "max": 100 },
        "minLength": 0,
        "maxLength": 20
      }
    }
  ],
  "predicate": {
    "module": "properties/arrays.ts",
    "exportName": "reverseTwicePreservesValues",
    "executionKind": "sync"
  }
}
```

The manifest contains no fast-check type name, arbitrary configuration object, seed, replay path, shrink data, USVM expression, or backend capability result.

`PropertyDefinition.toManifest()` produces a manifest only after validation. `PropertyManifestValidator` also validates deserialized input so stored artifacts cannot bypass invariants.

## Backend Projection and Capability

Backend support depends on the backend implementation and version. Capability is therefore a separate artifact rather than a field frozen into `PropertyManifest`:

```kotlin
enum class ProjectionLevel {
    EXACT,
    APPROXIMATE,
    UNSUPPORTED,
}

data class ProjectionCapability(
    val backendId: String,
    val backendVersion: String,
    val level: ProjectionLevel,
    val diagnostics: List<CapabilityDiagnostic>,
)

data class CapabilityDiagnostic(
    val code: String,
    val message: String,
    val path: String,
)
```

Domain composition takes the least capable child projection:

```text
EXACT < APPROXIMATE < UNSUPPORTED
```

A property is `concrete-only` for a selected combination when the concrete PBT projection is not `UNSUPPORTED` and the USVM projection is `UNSUPPORTED`. `concrete-only` is an aggregate pipeline classification, not a fourth backend projection level.

Diagnostics use stable codes and structural paths such as `inputs[0].domain.element`. A non-exact result must contain at least one diagnostic reason.

Issue #347 defines the projection contract and implements the fast-check capability provider. The USVM provider is implemented in #351. Tests for aggregation use controlled capability providers and do not pretend that symbolic lowering already exists.

## fast-check Adapter

The adapter is an internal implementation detail under `usvm-ts-pbt/fast-check-adapter`. It is not a public TypeScript property API or a publishable npm package.

It contains:

- a private `package.json` that pins fast-check;
- an ECMAScript module that maps each manifest domain recursively to a real `fc.Arbitrary`;
- tagged JavaScript value encode/decode functions;
- a small one-shot protocol executable used by Kotlin integration tests;
- Node built-in tests for the projection.

The initial protocol operation samples projected domains to prove that Kotlin definitions reach real fast-check arbitraries. It does not run predicates or campaigns.

### Protocol envelope

Requests and responses use one JSON document on standard input/output:

```json
{
  "protocolVersion": 1,
  "requestId": "projection-test-1",
  "operation": "sample",
  "seed": 42,
  "numSamples": 10,
  "domains": [
    { "kind": "integer", "min": -10, "max": 10 }
  ]
}
```

Successful responses echo `protocolVersion` and `requestId`, contain `status: "ok"`, and encode sample values as `JsConcreteValue`. Validation failures return `status: "error"` with stable diagnostic codes. Process startup failures and invalid non-JSON output are reported by the Kotlin caller as transport errors.

The adapter writes protocol output only to stdout. Human-readable logging goes to stderr so it cannot corrupt the protocol.

## Validation and Error Handling

Validation rejects definitions before backend execution when any of the following holds:

- invalid or empty property ID;
- empty input list;
- duplicate or invalid input names;
- integer or number minimum greater than maximum;
- NaN used as a numeric bound;
- negative length or minimum length greater than maximum;
- empty tuple;
- unsupported constant value;
- absolute, escaping, or malformed TypeScript module path;
- invalid export name;
- unknown manifest schema version;
- unknown backend protocol version or operation.

Validation returns all independent structural diagnostics in deterministic path/code order. Programmer-facing factory methods may throw a single `InvalidPropertyDefinitionException` containing the report; deserialization and backend boundaries return typed validation results instead of unchecked casts.

## Source Layout

The planned responsibilities are:

```text
usvm-ts-pbt/
  src/main/kotlin/org/usvm/ts/pbt/
    model/              PropertyDefinition, entry points, domain algebra, JsConcreteValue
    manifest/           versioned DTOs and serialization
    backend/            projection capability contracts and aggregation
    fastcheck/          Kotlin protocol DTOs and one-shot process client
  src/test/kotlin/org/usvm/ts/pbt/
    model/              definition and validation tests
    manifest/           serialization and round-trip tests
    backend/            capability aggregation tests
    fastcheck/          Kotlin-to-Node projection integration tests
  src/test/resources/properties/
    examples/           TypeScript predicate/precondition fixtures
  fast-check-adapter/
    package.json
    package-lock.json
    src/
    test/
```

Kotlin serialization uses `kotlinx.serialization`. The Node adapter uses ECMAScript modules and Node's built-in test runner; TypeScript compilation and predicate loading are deferred to #348.

Gradle owns installation and verification tasks for the private adapter. `:usvm-ts-pbt:check` runs Kotlin tests, Node adapter tests, and cross-language protocol tests. The existing native frontend baseline remains part of the same check.

## Testing Strategy

Tests follow red-green TDD during implementation.

### Kotlin unit tests

- validate every domain variant and invalid constraint;
- verify deterministic diagnostic ordering and paths;
- round-trip every manifest and tagged JavaScript value, including recursive protocol arrays;
- preserve finite double bits, negative zero, NaN, and infinities;
- aggregate nested domain and property capabilities;
- classify a supported concrete plus unsupported symbolic projection as `concrete-only`.

### Node unit tests

- project every supported domain to a real fast-check arbitrary;
- assert sampled values satisfy the declared constraints;
- decode constants and optional nil values exactly;
- reject unknown domain kinds and malformed tagged values;
- keep protocol stdout free of logs.

### Cross-language integration tests

Kotlin creates and serializes four example definitions:

1. a two-input relational property;
2. a bounded-input property;
3. a property with a TypeScript precondition reference;
4. an array property.

The test sends their domains through the Node adapter with a fixed seed and validates returned tagged samples against the original Kotlin domains. It also covers protocol version mismatch and malformed backend output.

The example TypeScript exports are fixtures for manifest validation in #347; actual predicate execution starts in #348.

## Extension Rules

A new common domain requires:

1. a semantic Kotlin model and validation rules;
2. a manifest schema change or backward-compatible variant;
3. an explicit capability decision from every backend;
4. conformance tests for each exact or approximate projection;
5. documentation of unsupported semantics.

A backend-specific custom domain may be represented only through an explicitly namespaced extension descriptor. Other backends must report `UNSUPPORTED`; they must never guess or silently approximate it.

A new PBT backend consumes `PropertyManifest` and implements the projection capability contract. It must not require changes to `PropertyDefinition`, the USVM backend, or common orchestration for already supported domains.

## Rejected Alternatives

### TypeScript-first shared API

A TypeScript `defineProperty` API that owns fast-check arbitraries makes Node and fast-check the center of the model. Supporting another PBT backend would require translating or replacing fast-check objects. This contradicts the Kotlin-first pipeline boundary.

### Kotlin definitions translated into fast-check CLI commands

fast-check is a JavaScript library, not a declarative CLI. A stable one-shot Node adapter with a versioned JSON protocol is smaller and testable. It reconstructs library objects internally while Kotlin remains the caller.

### Kotlin implementations of predicates

Reimplementing predicates in Kotlin would create a second property body and would not execute the original JavaScript semantics. Kotlin stores only TypeScript module/export references.

### Capability embedded in the manifest

Backend support changes with backend versions. Freezing capability into the engine-neutral manifest would make identical property semantics serialize differently depending on installed engines. Capability is therefore a separate versioned report.

## Issue Boundaries After #347

- #348 implements `FastCheckBackend`, TypeScript entry-point loading, `fc.check`, replay configuration, and structured concrete results.
- #349 adds backend-neutral coverage artifacts and c8/Istanbul support to the fast-check backend.
- #350 maps common entry points and coverage locations to EtsIR.
- #351 implements the USVM domain and precondition projection.
- #352 searches for predicate violations with USVM.
- #353 replays USVM witnesses and delegates shrinking to a capable PBT backend.
- #354 implements the Kotlin-orchestrated end-to-end pipeline.
- #355–#357 consume backend-identified artifacts for hints, benchmarks, and evaluation.
