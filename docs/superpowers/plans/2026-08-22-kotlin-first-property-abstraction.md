# Kotlin-First Property Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the engine-neutral Kotlin property model, versioned manifest and capability contracts, plus a real fast-check domain projection behind a Kotlin-controlled Node protocol.

**Architecture:** Kotlin owns property semantics, validation, serialization, and capability aggregation. A private Node adapter consumes only versioned domain descriptors and projects them to fast-check 4.9.0; it cannot orchestrate properties or USVM. Cross-language tests prove that Kotlin manifests reach real arbitraries without adding fast-check types to the common model.

**Tech Stack:** Kotlin 2.1, kotlinx.serialization 1.7.3, JUnit 5/Kotlin test, Gradle 8.11, Node.js 18.18+, fast-check 4.9.0, Node built-in test runner.

**Spec:** `docs/superpowers/specs/2026-08-22-kotlin-first-property-abstraction-design.md`

## Global Constraints

- Kotlin is the sole owner of property definitions and common artifacts.
- Common Kotlin model and manifest code must not import fast-check or backend-native generator types.
- Predicate and precondition bodies remain TypeScript module/export references; #347 does not execute them.
- Manifest schema version and Kotlin-to-Node protocol version are both exactly `1`.
- Integer domains are inclusive signed 32-bit ranges.
- String length counts arbitrary UTF-16 code units; default maximum length is `10`.
- Array default maximum length is `10`.
- Bounded number domains reject NaN; all JavaScript special numbers use tagged encoding.
- Tuple and array samples use recursive tagged `JsConcreteValue.Array`; `ConstantDomain` remains primitive-only.
- Capability is separate from `PropertyManifest` and is keyed by backend ID and version.
- The Node adapter is private and pins fast-check `4.9.0`.
- Existing `FrontendBaselineTest` must remain green.

---

### Task 1: Kotlin Property Model, Tagged Values, Manifest, and Validation

**Files:**

- Modify: `usvm-ts-pbt/build.gradle.kts`
- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/model/JsConcreteValue.kt`
- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/model/PropertyDomain.kt`
- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/model/PropertyDefinition.kt`
- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/manifest/PropertyManifest.kt`
- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/validation/PropertyValidation.kt`
- Test: `usvm-ts-pbt/src/test/kotlin/org/usvm/ts/pbt/model/JsConcreteValueTest.kt`
- Test: `usvm-ts-pbt/src/test/kotlin/org/usvm/ts/pbt/manifest/PropertyManifestTest.kt`
- Test: `usvm-ts-pbt/src/test/kotlin/org/usvm/ts/pbt/validation/PropertyValidationTest.kt`

**Interfaces:**

- Produces: `PropertyDefinition`, `PropertyDomain`, `JsConcreteValue`, `JsNumber`, `PropertyManifest`, `PropertyManifestJson`, `validatePropertyDefinition`, and `validatePropertyManifest`.
- Consumes: only Kotlin stdlib and kotlinx.serialization; it has no Node, fast-check, JacoDB, or USVM dependency.

- [ ] **Step 1: Enable Kotlin serialization and add the JSON runtime**

```kotlin
plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(Libs.jacodb_ets)
    implementation(Libs.kotlinx_serialization_json)
    testImplementation(Libs.logback)
}
```

- [ ] **Step 2: Write failing tagged-value and manifest round-trip tests**

```kotlin
@Test
fun `negative zero keeps its raw IEEE bits through JSON`() {
    val value = JsConcreteValue.Number(JsNumber.finite(-0.0))
    val encoded = PropertyManifestJson.json.encodeToString(JsConcreteValue.serializer(), value)
    val decoded = PropertyManifestJson.json.decodeFromString(JsConcreteValue.serializer(), encoded)
    assertEquals(value, decoded)
    assertEquals((-0.0).toRawBits(), (decoded as JsConcreteValue.Number).number.toDouble().toRawBits())
}

@Test
fun `manifest round trip contains only common property data`() {
    val definition = PropertyDefinition(
        id = PropertyId("math.commutative"),
        inputs = listOf(
            PropertyInput("left", IntegerDomain(-10, 10)),
            PropertyInput("right", IntegerDomain(-10, 10)),
        ),
        predicate = TypeScriptEntryPoint("properties/math.ts", "isCommutative"),
    )
    val encoded = PropertyManifestJson.encode(definition.toManifest())
    assertEquals(definition.toManifest(), PropertyManifestJson.decode(encoded))
    assertFalse("fast-check" in encoded)
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:test \
  --tests 'org.usvm.ts.pbt.model.JsConcreteValueTest' \
  --tests 'org.usvm.ts.pbt.manifest.PropertyManifestTest'
```

Expected: compilation fails because `JsConcreteValue`, `PropertyDefinition`, and manifest APIs do not exist.

- [ ] **Step 4: Implement tagged JavaScript primitives and domain algebra**

```kotlin
@Serializable
enum class JsNumberKind {
    @SerialName("finite") FINITE,
    @SerialName("nan") NAN,
    @SerialName("positive-infinity") POSITIVE_INFINITY,
    @SerialName("negative-infinity") NEGATIVE_INFINITY,
}

@Serializable
data class JsNumber(val value: JsNumberKind, val bits: String? = null) {
    fun toDouble(): Double = when (value) {
        JsNumberKind.FINITE -> Double.fromBits(requireNotNull(bits).toULong(16).toLong())
        JsNumberKind.NAN -> Double.NaN
        JsNumberKind.POSITIVE_INFINITY -> Double.POSITIVE_INFINITY
        JsNumberKind.NEGATIVE_INFINITY -> Double.NEGATIVE_INFINITY
    }

    companion object {
        fun finite(value: Double) = JsNumber(
            JsNumberKind.FINITE,
            value.toRawBits().toULong().toString(16).padStart(16, '0'),
        )
    }
}

@Serializable
sealed interface PropertyDomain

@Serializable
@SerialName("boolean")
data object BooleanDomain : PropertyDomain

@Serializable
@SerialName("integer")
data class IntegerDomain(val min: Int = Int.MIN_VALUE, val max: Int = Int.MAX_VALUE) : PropertyDomain

@Serializable
@SerialName("number")
data class NumberDomain(
    val min: JsNumber = JsNumber.negativeInfinity(),
    val max: JsNumber = JsNumber.positiveInfinity(),
    val allowNaN: Boolean = true,
) : PropertyDomain

@Serializable
@SerialName("string")
data class StringDomain(
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_STRING_LENGTH,
) : PropertyDomain

@Serializable
@SerialName("constant")
data class ConstantDomain(val value: JsConcreteValue) : PropertyDomain

@Serializable
@SerialName("optional")
data class OptionalDomain(
    val value: PropertyDomain,
    val nil: JsConcreteValue = JsConcreteValue.Undefined,
) : PropertyDomain

@Serializable
@SerialName("tuple")
data class TupleDomain(val elements: List<PropertyDomain>) : PropertyDomain

@Serializable
@SerialName("array")
data class ArrayDomain(
    val element: PropertyDomain,
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_ARRAY_LENGTH,
) : PropertyDomain
```

- [ ] **Step 5: Implement manifest serialization with strict schema versioning**

```kotlin
@Serializable
data class PropertyManifest(
    val schemaVersion: Int = PROPERTY_MANIFEST_SCHEMA_VERSION,
    val propertyId: String,
    val inputs: List<PropertyInput>,
    val predicate: TypeScriptEntryPoint,
    val precondition: TypeScriptEntryPoint? = null,
)

object PropertyManifestJson {
    val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(manifest: PropertyManifest): String = json.encodeToString(manifest)
    fun decode(value: String): PropertyManifest = json.decodeFromString<PropertyManifest>(value)
        .also { requireValid(validatePropertyManifest(it)) }
}
```

- [ ] **Step 6: Write failing validation tests**

```kotlin
@Test
fun `validation reports all structural errors in deterministic order`() {
    val invalid = PropertyDefinition(
        id = PropertyId.unchecked(" bad id "),
        inputs = listOf(
            PropertyInput("value", IntegerDomain(10, -10)),
            PropertyInput("value", StringDomain(-1, 0)),
        ),
        predicate = TypeScriptEntryPoint("../escape.ts", "not-valid-name"),
    )
    assertEquals(
        listOf(
            "property.id.invalid",
            "input.name.duplicate",
            "domain.integer.bounds",
            "domain.string.length",
            "entrypoint.module.invalid",
            "entrypoint.export.invalid",
        ),
        validatePropertyDefinition(invalid).diagnostics.map { it.code },
    )
}
```

- [ ] **Step 7: Run validation tests and verify RED**

Run:

```shell
./gradlew --no-daemon :usvm-ts-pbt:test \
  --tests 'org.usvm.ts.pbt.validation.PropertyValidationTest'
```

Expected: compilation fails because validation APIs do not exist.

- [ ] **Step 8: Implement deterministic structural validation**

```kotlin
data class ValidationDiagnostic(val code: String, val message: String, val path: String)

data class PropertyValidationResult(val diagnostics: List<ValidationDiagnostic>) {
    val isValid: Boolean get() = diagnostics.isEmpty()
}

fun validatePropertyDefinition(definition: PropertyDefinition): PropertyValidationResult =
    PropertyValidator.validate(definition).sortedWith(compareBy({ it.path }, { it.code }))
        .let(::PropertyValidationResult)
```

Recursively validate every domain, exact finite-number bit encoding, optional nil values, entry-point paths/exports, duplicate inputs, and schema version.

- [ ] **Step 9: Run all Task 1 tests and verify GREEN**

```shell
./gradlew --no-daemon :usvm-ts-pbt:test \
  --tests 'org.usvm.ts.pbt.model.*' \
  --tests 'org.usvm.ts.pbt.manifest.*' \
  --tests 'org.usvm.ts.pbt.validation.*'
```

Expected: all selected tests pass with no warnings from project code.

- [ ] **Step 10: Commit the model increment**

```shell
git add usvm-ts-pbt/build.gradle.kts usvm-ts-pbt/src/main usvm-ts-pbt/src/test
git commit -m "feat(ts-pbt): add Kotlin property model"
```

---

### Task 2: Backend Projection Capability and Aggregate Classification

**Files:**

- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/backend/ProjectionCapability.kt`
- Test: `usvm-ts-pbt/src/test/kotlin/org/usvm/ts/pbt/backend/ProjectionCapabilityTest.kt`

**Interfaces:**

- Consumes: structural paths and validated `PropertyDefinition` from Task 1.
- Produces: `ProjectionLevel`, `ProjectionCapability`, `CapabilityDiagnostic`, `PropertyCapabilityLevel`, `aggregateProjectionCapabilities`, and `classifyPropertyCapability`.

- [ ] **Step 1: Write failing capability composition tests**

```kotlin
@Test
fun `least capable nested projection wins`() {
    val capability = aggregateProjectionCapabilities(
        backendId = "fast-check",
        backendVersion = "4.9.0",
        capabilities = listOf(exact(), approximate("domain.string.approximate")),
    )
    assertEquals(ProjectionLevel.APPROXIMATE, capability.level)
}

@Test
fun `supported concrete and unsupported symbolic is concrete only`() {
    assertEquals(
        PropertyCapabilityLevel.CONCRETE_ONLY,
        classifyPropertyCapability(exact("fast-check"), unsupported("usvm", "entrypoint.async")),
    )
}
```

- [ ] **Step 2: Run the test and verify RED**

```shell
./gradlew --no-daemon :usvm-ts-pbt:test \
  --tests 'org.usvm.ts.pbt.backend.ProjectionCapabilityTest'
```

Expected: compilation fails because capability APIs do not exist.

- [ ] **Step 3: Implement capability models and deterministic aggregation**

```kotlin
enum class ProjectionLevel { EXACT, APPROXIMATE, UNSUPPORTED }
enum class PropertyCapabilityLevel { EXACT, APPROXIMATE, CONCRETE_ONLY, UNSUPPORTED }

data class ProjectionCapability(
    val backendId: String,
    val backendVersion: String,
    val level: ProjectionLevel,
    val diagnostics: List<CapabilityDiagnostic> = emptyList(),
)

fun classifyPropertyCapability(
    concrete: ProjectionCapability,
    symbolic: ProjectionCapability,
): PropertyCapabilityLevel = when {
    concrete.level == ProjectionLevel.UNSUPPORTED -> PropertyCapabilityLevel.UNSUPPORTED
    symbolic.level == ProjectionLevel.UNSUPPORTED -> PropertyCapabilityLevel.CONCRETE_ONLY
    concrete.level == ProjectionLevel.APPROXIMATE || symbolic.level == ProjectionLevel.APPROXIMATE ->
        PropertyCapabilityLevel.APPROXIMATE
    else -> PropertyCapabilityLevel.EXACT
}
```

Reject non-exact capabilities without diagnostics, sort diagnostics by path/code, and preserve backend identity/version.

- [ ] **Step 4: Run Task 2 and Task 1 tests and verify GREEN**

```shell
./gradlew --no-daemon :usvm-ts-pbt:test \
  --tests 'org.usvm.ts.pbt.backend.*' \
  --tests 'org.usvm.ts.pbt.model.*' \
  --tests 'org.usvm.ts.pbt.manifest.*' \
  --tests 'org.usvm.ts.pbt.validation.*'
```

- [ ] **Step 5: Commit the capability increment**

```shell
git add usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/backend \
  usvm-ts-pbt/src/test/kotlin/org/usvm/ts/pbt/backend
git commit -m "feat(ts-pbt): add backend capability model"
```

---

### Task 3: Private fast-check Domain Projection Adapter

**Files:**

- Create: `usvm-ts-pbt/fast-check-adapter/package.json`
- Create: `usvm-ts-pbt/fast-check-adapter/package-lock.json`
- Create: `usvm-ts-pbt/fast-check-adapter/src/js-value.mjs`
- Create: `usvm-ts-pbt/fast-check-adapter/src/project-domain.mjs`
- Create: `usvm-ts-pbt/fast-check-adapter/src/projection-cli.mjs`
- Test: `usvm-ts-pbt/fast-check-adapter/test/js-value.test.mjs`
- Test: `usvm-ts-pbt/fast-check-adapter/test/project-domain.test.mjs`
- Test: `usvm-ts-pbt/fast-check-adapter/test/projection-cli.test.mjs`

**Interfaces:**

- Consumes: schema-version-1 domain and `JsConcreteValue` JSON produced by Task 1.
- Produces: `decodeJsValue`, `encodeJsValue`, `projectDomain`, `projectionCapability`, and a one-shot `sample` protocol executable.

- [ ] **Step 1: Add the private adapter package and lock fast-check 4.9.0**

```json
{
  "name": "@usvm/fast-check-adapter",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "node --test"
  },
  "dependencies": {
    "fast-check": "4.9.0"
  }
}
```

Run `npm install --package-lock-only --ignore-scripts` in `usvm-ts-pbt/fast-check-adapter` to generate the exact lock file, then `npm ci --ignore-scripts`.

- [ ] **Step 2: Write failing Node tests for tagged values and every domain**

```javascript
import assert from 'node:assert/strict';
import test from 'node:test';
import fc from 'fast-check';
import { projectDomain } from '../src/project-domain.mjs';

test('bounded integers use a real fast-check arbitrary', () => {
  const arbitrary = projectDomain({ kind: 'integer', min: -3, max: 7 });
  const samples = fc.sample(arbitrary, { seed: 42, numRuns: 100 });
  assert.ok(samples.every((value) => Number.isInteger(value) && value >= -3 && value <= 7));
});

test('strings are arbitrary UTF-16 code-unit sequences', () => {
  const arbitrary = projectDomain({ kind: 'string', minLength: 2, maxLength: 4 });
  const samples = fc.sample(arbitrary, { seed: 42, numRuns: 100 });
  assert.ok(samples.every((value) => value.length >= 2 && value.length <= 4));
});

for (const [name, domain, predicate] of [
  ['boolean', { kind: 'boolean' }, (value) => typeof value === 'boolean'],
  ['bounded number', boundedNumber(-1.5, 2.5), (value) => !Number.isNaN(value) && value >= -1.5 && value <= 2.5],
  ['constant -0', constant(numberValue(-0)), (value) => Object.is(value, -0)],
  ['optional undefined', optional(integer(-2, 2), undefinedValue()),
    (value) => value === undefined || (Number.isInteger(value) && value >= -2 && value <= 2)],
  ['tuple', tuple(booleanDomain(), integer(0, 3)),
    (value) => Array.isArray(value) && value.length === 2 && typeof value[0] === 'boolean'],
  ['array', array(integer(0, 3), 1, 4),
    (value) => Array.isArray(value) && value.length >= 1 && value.length <= 4],
]) {
  test(`${name} projects to values satisfying the common domain`, () => {
    const samples = fc.sample(projectDomain(domain), { seed: 42, numRuns: 100 });
    assert.ok(samples.every(predicate));
  });
}

test('unknown domain kinds are rejected explicitly', () => {
  assert.throws(() => projectDomain({ kind: 'object' }), /domain\.kind\.unknown/);
});
```

Add a second optional case with null, a nested-array case, and a tagged-number table containing NaN, both infinities, positive zero, and negative zero using the same fixed-seed sampling pattern.

- [ ] **Step 3: Run Node tests and verify RED**

```shell
npm test --prefix usvm-ts-pbt/fast-check-adapter
```

Expected: tests fail with `ERR_MODULE_NOT_FOUND` for adapter source modules.

- [ ] **Step 4: Implement exact tagged-value conversion**

```javascript
export function decodeJsNumber(number) {
  switch (number.value) {
    case 'finite': return bitsToDouble(number.bits);
    case 'nan': return Number.NaN;
    case 'positive-infinity': return Number.POSITIVE_INFINITY;
    case 'negative-infinity': return Number.NEGATIVE_INFINITY;
    default: throw protocolError('js-number.kind.unknown');
  }
}

export function encodeJsNumber(value) {
  if (Number.isNaN(value)) return { value: 'nan' };
  if (value === Number.POSITIVE_INFINITY) return { value: 'positive-infinity' };
  if (value === Number.NEGATIVE_INFINITY) return { value: 'negative-infinity' };
  return { value: 'finite', bits: doubleToBits(value) };
}
```

Use `DataView` with explicit big-endian order for stable 16-hex-digit double encoding.

- [ ] **Step 5: Implement recursive domain projection**

```javascript
export function projectDomain(domain) {
  switch (domain.kind) {
    case 'boolean': return fc.boolean();
    case 'integer': return fc.integer({ min: domain.min, max: domain.max });
    case 'string':
      return fc.array(fc.integer({ min: 0, max: 0xffff }), {
        minLength: domain.minLength,
        maxLength: domain.maxLength,
      }).map((units) => String.fromCharCode(...units));
    case 'constant': return fc.constant(decodeJsValue(domain.value));
    case 'optional':
      return fc.option(projectDomain(domain.value), { nil: decodeJsValue(domain.nil) });
    case 'tuple': return fc.tuple(...domain.elements.map(projectDomain));
    case 'array':
      return fc.array(projectDomain(domain.element), {
        minLength: domain.minLength,
        maxLength: domain.maxLength,
      });
    default: throw protocolError('domain.kind.unknown');
  }
}

function projectNumber(domain) {
  const min = decodeJsNumber(domain.min);
  const max = decodeJsNumber(domain.max);
  const finite = fc.double({ min, max, noNaN: true, noDefaultInfinity: true });
  const specials = [];
  if (domain.allowNaN) specials.push(fc.constant(Number.NaN));
  if (min === Number.NEGATIVE_INFINITY) specials.push(fc.constant(Number.NEGATIVE_INFINITY));
  if (max === Number.POSITIVE_INFINITY) specials.push(fc.constant(Number.POSITIVE_INFINITY));
  return specials.length === 0 ? finite : fc.oneof(finite, ...specials);
}
```

- [ ] **Step 6: Run domain tests and verify GREEN**

```shell
npm test --prefix usvm-ts-pbt/fast-check-adapter
```

- [ ] **Step 7: Write failing one-shot protocol tests**

```javascript
test('sample response echoes request identity and returns tagged values', async () => {
  const response = await invokeCli({
    protocolVersion: 1,
    requestId: 'sample-1',
    operation: 'sample',
    seed: 42,
    numSamples: 4,
    domains: [{ kind: 'integer', min: -1, max: 1 }],
  });
  assert.equal(response.requestId, 'sample-1');
  assert.equal(response.status, 'ok');
  assert.equal(response.samples.length, 4);
});
```

Also test protocol-version mismatch, unknown operations, malformed JSON, and that stderr logging never appears in stdout.

- [ ] **Step 8: Implement `projection-cli.mjs` and verify GREEN**

```javascript
const input = await readStdin();
let response;
try {
  const request = validateRequest(JSON.parse(input));
  const arbitrary = fc.tuple(...request.domains.map(projectDomain));
  const tuples = fc.sample(arbitrary, { seed: request.seed, numRuns: request.numSamples });
  response = {
    protocolVersion: 1,
    requestId: request.requestId,
    status: 'ok',
    samples: tuples.map((tuple) => tuple.map(encodeJsValue)),
  };
} catch (error) {
  response = protocolErrorResponse(error);
}
process.stdout.write(`${JSON.stringify(response)}\n`);
```

`validateRequest` accepts only protocol version `1`, operation `sample`, a non-empty request ID and domains, an integer seed, and `numSamples` in `1..10000`. `protocolErrorResponse` preserves a parsed request ID when available and emits stable `protocol.version.unsupported`, `protocol.operation.unsupported`, `protocol.json.invalid`, and `protocol.request.invalid` codes.

```shell
npm test --prefix usvm-ts-pbt/fast-check-adapter
```

- [ ] **Step 9: Commit the adapter increment**

```shell
git add usvm-ts-pbt/fast-check-adapter
git commit -m "feat(ts-pbt): project domains to fast-check"
```

---

### Task 4: Kotlin-to-Node Protocol, Examples, Gradle Wiring, and Documentation

**Files:**

- Modify: `usvm-ts-pbt/build.gradle.kts`
- Modify: `usvm-ts-pbt/README.md`
- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/fastcheck/FastCheckProjectionProtocol.kt`
- Create: `usvm-ts-pbt/src/main/kotlin/org/usvm/ts/pbt/fastcheck/FastCheckProjectionClient.kt`
- Test: `usvm-ts-pbt/src/test/kotlin/org/usvm/ts/pbt/fastcheck/FastCheckProjectionClientTest.kt`
- Test: `usvm-ts-pbt/src/test/kotlin/org/usvm/ts/pbt/examples/ExamplePropertiesTest.kt`
- Create: `usvm-ts-pbt/src/test/resources/properties/examples/PropertyExamples.ts`

**Interfaces:**

- Consumes: validated manifests from Task 1, capabilities from Task 2, and the one-shot CLI from Task 3.
- Produces: versioned Kotlin protocol DTOs, `FastCheckProjectionClient.sample`, four executable test definitions, Gradle verification tasks, and user documentation.

- [ ] **Step 1: Write failing Kotlin-to-Node integration tests**

```kotlin
@Test
fun `Kotlin domains produce deterministic tagged fast-check samples`() {
    val request = FastCheckProjectionRequest(
        requestId = "integration-1",
        seed = 42,
        numSamples = 20,
        domains = listOf(IntegerDomain(-10, 10), ArrayDomain(BooleanDomain, 0, 3)),
    )
    val response = client.sample(request)
    assertEquals("integration-1", response.requestId)
    assertEquals(20, response.samples.size)
    response.samples.forEach { sample -> assertConforms(sample, request.domains) }
}

@Test
fun `protocol version mismatch is a typed backend error`() {
    val error = assertFailsWith<FastCheckProjectionException> {
        client.sample(validRequest.copy(protocolVersion = 999))
    }
    assertEquals("protocol.version.unsupported", error.code)
}
```

- [ ] **Step 2: Run the focused integration tests and verify RED**

```shell
./gradlew --no-daemon :usvm-ts-pbt:test \
  --tests 'org.usvm.ts.pbt.fastcheck.FastCheckProjectionClientTest'
```

Expected: compilation fails because protocol DTOs and client do not exist.

- [ ] **Step 3: Implement protocol DTOs and the one-shot process client**

```kotlin
@Serializable
data class FastCheckProjectionRequest(
    val protocolVersion: Int = FAST_CHECK_PROTOCOL_VERSION,
    val requestId: String,
    val operation: String = "sample",
    val seed: Int,
    val numSamples: Int,
    val domains: List<PropertyDomain>,
)

class FastCheckProjectionClient(
    private val nodeExecutable: String = "node",
    private val adapterEntryPoint: Path,
) {
    fun sample(request: FastCheckProjectionRequest): FastCheckProjectionResponse {
        val process = ProcessBuilder(nodeExecutable, adapterEntryPoint.toString()).start()
        process.outputWriter(Charsets.UTF_8).use { it.write(protocolJson.encodeToString(request)) }
        val stdout = process.inputReader(Charsets.UTF_8).readText()
        val stderr = process.errorReader(Charsets.UTF_8).readText()
        val exit = process.waitFor()
        if (exit != 0) throw FastCheckProjectionException("backend.process.failed", stderr)
        return decodeProjectionResponse(stdout)
    }
}
```

Reject invalid request sizes before process launch and return typed errors for process startup, nonzero exit, empty output, malformed JSON, mismatched IDs, and protocol error responses.

- [ ] **Step 4: Run the Kotlin-to-Node tests and verify GREEN**

```shell
./gradlew --no-daemon :usvm-ts-pbt:test \
  --tests 'org.usvm.ts.pbt.fastcheck.FastCheckProjectionClientTest'
```

- [ ] **Step 5: Add Gradle npm installation and Node test tasks**

```kotlin
val installFastCheckAdapter = tasks.register<Exec>("installFastCheckAdapter") {
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "ci", "--ignore-scripts")
    inputs.files(
        fastCheckAdapterDir.resolve("package.json"),
        fastCheckAdapterDir.resolve("package-lock.json"),
    )
    outputs.dir(fastCheckAdapterDir.resolve("node_modules"))
}

val testFastCheckAdapter = tasks.register<Exec>("testFastCheckAdapter") {
    dependsOn(installFastCheckAdapter)
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "test")
    inputs.dir(fastCheckAdapterDir.resolve("src"))
    inputs.dir(fastCheckAdapterDir.resolve("test"))
}

tasks.test { dependsOn(installFastCheckAdapter) }
tasks.check { dependsOn(testFastCheckAdapter) }
```

Use `npm.cmd` on Windows. Track package files and adapter source/tests as task inputs; never silently skip Node verification when npm is absent.

- [ ] **Step 6: Add four example Kotlin definitions and TypeScript export fixtures**

```kotlin
val relational = PropertyDefinition(
    id = PropertyId("example.relational"),
    inputs = listOf(
        PropertyInput("left", IntegerDomain()),
        PropertyInput("right", IntegerDomain()),
    ),
    predicate = TypeScriptEntryPoint("properties/examples/PropertyExamples.ts", "isCommutative"),
)
```

Add bounded, precondition, and array definitions. Assert each validates, serializes, and projects through fast-check. The TypeScript resource exports `isCommutative`, the bounded predicate, the precondition, and the array predicate without executing them in this issue.

- [ ] **Step 7: Update module documentation**

Document:

- Kotlin ownership and backend dependency direction;
- the domain table and exact defaults;
- `PropertyManifest` versus `ProjectionCapability`;
- TypeScript module/export references;
- the private fast-check adapter and protocol boundary;
- the supported and rejected extension mechanisms;
- focused Kotlin, Node, and full module verification commands;
- the explicit #348–#354 boundaries.

- [ ] **Step 8: Run focused verification**

```shell
npm test --prefix usvm-ts-pbt/fast-check-adapter
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon :usvm-ts-pbt:test
```

Expected: all Node and Kotlin tests pass, including `FrontendBaselineTest`.

- [ ] **Step 9: Run full module and static verification**

```shell
env -u ARKANALYZER_DIR ETS_IR_PROVIDER=ts-frontend \
  ./gradlew --no-daemon \
  :usvm-ts-pbt:clean :usvm-ts-pbt:check \
  :usvm-ts-pbt:detektMain :usvm-ts-pbt:detektTest
git diff --check origin/main...HEAD
```

- [ ] **Step 10: Commit the integration increment**

```shell
git add usvm-ts-pbt docs/superpowers/plans/2026-08-22-kotlin-first-property-abstraction.md
git commit -m "feat(ts-pbt): integrate Kotlin property projection"
```
