package org.usvm.ts.pbt.semantics.builtins

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.util.getResourcePath
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Dependency-neutral executable model for the frozen builtin contract.
 *
 * This deliberately does not call EtsConcreteInterpreter, Intrinsics or any
 * symbolic implementation: the integration agents must make those layers
 * agree with this model and the independent Node oracle later.
 */
class BuiltinSemanticsSpecTest {
    private val specPath: Path = getResourcePath("/semantics/builtins/builtin-semantics-v1.json")
    private val spec: JsonObject = Json.parseToJsonElement(specPath.toFile().readText()).jsonObject
    private val cases: JsonArray get() = spec.getValue("cases").jsonArray

    @Test
    fun `frozen contract is complete explicit and stable`() {
        assertEquals(1, spec.getValue("schemaVersion").jsonPrimitive.content.toInt())
        assertEquals("usvm-ts-pbt.builtins.exact.v1", spec.string("contractId"))
        assertEquals(33, cases.size)

        val ids = cases.map { it.jsonObject.string("id") }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.containsAll(EXACT_BOUNDARY_CASES))
        assertTrue(ids.contains("map-same-value-zero-nan"))

        val requiredOperations = spec.getValue("requiredOperations").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(OPERATIONS, requiredOperations)
        cases.forEach { element ->
            val case = element.jsonObject
            assertTrue(case.string("sourceCallableId").startsWith(SOURCE_ID_PREFIX), case.string("id"))
            val capability = case.getValue("capability").jsonObject
            assertEquals("supported_with_flag", capability.string("expectedStatus"), case.string("id"))
            assertEquals("exact", capability.string("expectedOutcome"), case.string("id"))
            val labels = capability.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
            val semanticTags = capability["semanticTags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            assertTrue(labels.isNotEmpty(), case.string("id"))
            assertTrue(labels.all { it in CAPABILITY_LABELS }, "${case.string("id")}: $labels")
            assertTrue(semanticTags.all { it in SEMANTIC_TAGS }, "${case.string("id")}: $semanticTags")
            assertTrue(labels.intersect(semanticTags.toSet()).isEmpty(), case.string("id"))
        }

        val blockers = spec.getValue("residualBlockers").jsonArray.map(JsonElement::jsonObject)
        assertEquals(3, blockers.size)
        assertEquals(2, blockers.count { it.string("semanticClass") == "static_runtime_builtin" })
        assertEquals(1, blockers.count { it.string("semanticClass") == "map_membership_truthiness" })
        blockers.forEach { blocker ->
            val etsIr = blocker.getValue("etsIr").jsonObject
            val outcome = blocker.getValue("frozenOutcome").jsonObject
            val capability = blocker.getValue("capability").jsonObject
            val labels = capability.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
            val semanticTags = capability["semanticTags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            assertTrue(labels.isNotEmpty() && labels.all { it in CAPABILITY_LABELS }, blocker.string("id"))
            assertTrue(semanticTags.all { it in SEMANTIC_TAGS }, blocker.string("id"))
            assertEquals("semantic_mismatch", outcome.string("status"))
            assertFalse(outcome.getValue("replayConfirmed").jsonPrimitive.boolean)
            assertEquals(
                etsIr.getValue("semanticCallStmtIndex").jsonPrimitive.content.toInt(),
                outcome.getValue("mismatchStmtIndex").jsonPrimitive.content.toInt(),
                blocker.string("id"),
            )
            assertTrue(
                etsIr.getValue("semanticCallStmtIndex").jsonPrimitive.content.toInt() <
                    etsIr.getValue("conditionStmtIndex").jsonPrimitive.content.toInt(),
                blocker.string("id"),
            )
            assertTrue(
                etsIr.getValue("conditionStmtIndex").jsonPrimitive.content.toInt() <
                    etsIr.getValue("ifStmtIndex").jsonPrimitive.content.toInt(),
                blocker.string("id"),
            )
        }

        val unsupported = spec.getValue("unsupported").jsonArray.map(JsonElement::jsonObject)
        assertEquals(8, unsupported.size)
        unsupported.forEach { item ->
            assertTrue(item.string("reason").isNotBlank(), item.string("id"))
            assertTrue(item.string("expectedStatus") in CAPABILITY_STATUSES, item.string("id"))
        }

        val source = getResourcePath("/semantics/builtins/BuiltinSemanticsFixture.ts").toFile().readText()
        listOf(
            "Array.isArray(subject)",
            "Object.prototype.toString.call(subject)",
            "Object.prototype.hasOwnProperty.call(subject, key)",
            "key in subject",
            "subject.set(key, value)",
            "subject.get(key)",
            "subject.has(key)",
            "Boolean(subject)",
        ).forEach { expression -> assertTrue(source.contains(expression), expression) }
    }

    @Test
    fun `Kotlin exact model matches every frozen Node result and trace`() {
        cases.forEach { element ->
            val case = element.jsonObject
            val expected = case.getValue("expected")
            val actual = BuiltinSpecModel.run(case)
            assertEquals(expected, actual, case.string("id"))
        }
    }

    @Test
    fun `three real residuals resolve to immutable manifests and reached-not-replayed observations`() {
        val root = repositoryRoot()
        val baseline = spec.getValue("baselineEvidence").jsonObject
        val observationsPath = root.resolve(baseline.string("observationsPath"))
        assertEquals(baseline.string("observationsSha256"), sha256(observationsPath))
        val observations = Json.parseToJsonElement(observationsPath.toFile().readText()).jsonObject
            .getValue("reports").jsonArray
            .map(JsonElement::jsonObject)

        spec.getValue("residualBlockers").jsonArray.map(JsonElement::jsonObject).forEach { blocker ->
            val etsIr = blocker.getValue("etsIr").jsonObject
            val manifest = Json.parseToJsonElement(
                root.resolve(blocker.string("targetManifestPath")).toFile().readText(),
            ).jsonObject
            val method = manifest.getValue("methods").jsonArray
                .map(JsonElement::jsonObject)
                .single { it.string("methodId") == etsIr.string("methodId") }
            val branch = method.getValue("branches").jsonArray
                .map(JsonElement::jsonObject)
                .single { it.string("branchId") == etsIr.string("branchId") }
            assertEquals(
                etsIr.getValue("ifStmtIndex").jsonPrimitive.content.toInt(),
                branch.getValue("ifStmtIndex").jsonPrimitive.content.toInt(),
                blocker.string("id"),
            )

            val scenarios = blocker.getValue("observationScenarios").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val observedTargets = observations
                .filter { it.string("projectId") == blocker.string("projectId") && it.string("scenario") in scenarios }
                .flatMap { it.getValue("methods").jsonArray.map(JsonElement::jsonObject) }
                .filter { it.string("methodId") == etsIr.string("methodId") }
                .flatMap { methodReport ->
                    methodReport["symbolic"]
                        ?.jsonObject
                        ?.get("targets")
                        ?.jsonArray
                        ?.map(JsonElement::jsonObject)
                        .orEmpty()
                }
                .filter { it.string("branchId") == etsIr.string("branchId") }
            assertEquals(scenarios.size, observedTargets.size, blocker.string("id"))
            observedTargets.forEach { target ->
                assertTrue(target.getValue("reached").jsonPrimitive.boolean, blocker.string("id"))
                assertFalse(target.getValue("replayConfirmed").jsonPrimitive.boolean, blocker.string("id"))
            }
        }
    }

    private fun repositoryRoot(): Path = generateSequence(
        Path.of(System.getProperty("user.dir")).toAbsolutePath(),
        Path::getParent,
    ).firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
        ?: error("repository root is not reachable from ${System.getProperty("user.dir")}")

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE_ID_PREFIX = "ts:semantic/builtins/BuiltinSemanticsFixture.ts::free:"

        val OPERATIONS = setOf(
            "array.isArray",
            "object.toStringTag",
            "object.hasOwn",
            "property.in",
            "map.set",
            "map.get",
            "map.has",
            "map.size",
            "truthy",
        )

        val EXACT_BOUNDARY_CASES = setOf(
            "has-own-value",
            "has-own-inherited-false",
            "has-own-missing",
            "has-own-undefined",
            "property-in-own",
            "property-in-inherited",
            "property-in-missing",
            "property-in-own-undefined",
            "map-get-missing",
            "map-get-stored-undefined",
            "map-has-missing",
            "map-has-stored-undefined",
        )

        val CAPABILITY_STATUSES = setOf(
            "supported",
            "supported_with_flag",
            "external_only",
            "unsupported",
            "needs_dynamic_probe",
        )

        // Closed WP-CAP v1 taxonomy. Fine-grained semantics belongs to SEMANTIC_TAGS.
        val CAPABILITY_LABELS = setOf(
            "primitive_arithmetic",
            "module_init",
            "callable",
            "iterator",
            "array_object",
            "map_set",
            "builtin_call",
            "spread_yield",
            "unresolved_pointer_call",
        )

        val SEMANTIC_TAGS = setOf(
            "static_builtin_call",
            "primitive_string",
            "nullish",
            "property_membership",
            "missing_vs_undefined",
            "prototype_chain",
            "map_mutation",
            "truthiness",
            "same_value_zero",
        )
    }
}

private sealed interface ModelValue
private object ModelUndefined : ModelValue
private object ModelNull : ModelValue
private data class ModelBoolean(val value: Boolean) : ModelValue
private data class ModelString(val value: String) : ModelValue
private data class ModelNumber(val value: Double) : ModelValue
private data class ModelArray(val elements: List<ModelValue>) : ModelValue
private data class ModelObject(
    val own: MutableMap<String, ModelValue>,
    val prototype: ModelObject?,
) : ModelValue

private class ModelMap(val entries: MutableList<ModelMapEntry>) : ModelValue
private data class ModelMapEntry(val key: ModelValue, var value: ModelValue)

private object BuiltinSpecModel {
    fun run(case: JsonObject): JsonObject {
        val environment = case.getValue("environment").jsonObject
            .mapValuesTo(mutableMapOf()) { (_, encoded) -> decode(encoded.jsonObject) }
        val trace = mutableListOf<String>()

        case.getValue("steps").jsonArray.map(JsonElement::jsonObject).forEach { step ->
            val receiverName = step.string("receiver")
            val receiver = requireNotNull(environment[receiverName]) { "unknown receiver $receiverName" }
            val arguments = step["arguments"]?.jsonArray.orEmpty().map { argumentElement ->
                val argument = argumentElement.jsonObject
                argument["ref"]?.jsonPrimitive?.contentOrNull?.let { reference ->
                    requireNotNull(environment[reference]) { "unknown argument reference $reference" }
                } ?: decode(requireNotNull(argument["literal"]) { "argument must have ref or literal" }.jsonObject)
            }
            val operation = step.string("operation")
            val result = execute(operation, receiver, arguments)
            environment[step.string("saveAs")] = result

            val renderedArguments = step["arguments"]?.jsonArray.orEmpty().mapIndexed { index, argumentElement ->
                val argument = argumentElement.jsonObject
                argument["ref"]?.jsonPrimitive?.contentOrNull?.let { "ref:$it" } ?: format(arguments[index])
            }
            val renderedResult = if (operation == "map.set") "receiver:$receiverName" else format(result)
            val renderedCall = listOf(receiverName, *renderedArguments.toTypedArray()).joinToString(", ")
            trace += "$operation($renderedCall) -> $renderedResult"
        }

        val result = requireNotNull(environment[case.string("return")])
        return buildJsonObject {
            put("result", encode(result))
            put("truthy", truthy(result))
            put("trace", buildJsonArray { trace.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun execute(operation: String, receiver: ModelValue, arguments: List<ModelValue>): ModelValue =
        when (operation) {
            "array.isArray" -> ModelBoolean(receiver is ModelArray)
            "object.toStringTag" -> ModelString(
                when (receiver) {
                    ModelUndefined -> "[object Undefined]"
                    ModelNull -> "[object Null]"
                    is ModelBoolean -> "[object Boolean]"
                    is ModelString -> "[object String]"
                    is ModelNumber -> "[object Number]"
                    is ModelArray -> "[object Array]"
                    is ModelMap -> "[object Map]"
                    is ModelObject -> "[object Object]"
                },
            )
            "object.hasOwn" -> ModelBoolean(
                (receiver as ModelObject).own.containsKey((arguments.single() as ModelString).value),
            )
            "property.in" -> ModelBoolean(
                hasProperty(receiver as ModelObject, (arguments.single() as ModelString).value),
            )
            "map.set" -> (receiver as ModelMap).also { map ->
                val key = arguments[0]
                val existing = map.entries.firstOrNull { sameValueZero(it.key, key) }
                if (existing == null) map.entries += ModelMapEntry(key, arguments[1]) else existing.value = arguments[1]
            }
            "map.get" -> (receiver as ModelMap).entries
                .firstOrNull { sameValueZero(it.key, arguments.single()) }
                ?.value ?: ModelUndefined
            "map.has" -> ModelBoolean((receiver as ModelMap).entries.any { sameValueZero(it.key, arguments.single()) })
            "map.size" -> ModelNumber((receiver as ModelMap).entries.size.toDouble())
            "truthy" -> ModelBoolean(truthy(receiver))
            else -> error("unknown operation $operation")
        }

    private fun hasProperty(subject: ModelObject, key: String): Boolean =
        subject.own.containsKey(key) || subject.prototype?.let { hasProperty(it, key) } == true

    private fun sameValueZero(left: ModelValue, right: ModelValue): Boolean = when {
        left is ModelNumber && right is ModelNumber ->
            left.value == right.value || (left.value.isNaN() && right.value.isNaN())
        left is ModelString && right is ModelString -> left.value == right.value
        left is ModelBoolean && right is ModelBoolean -> left.value == right.value
        left === ModelNull && right === ModelNull -> true
        left === ModelUndefined && right === ModelUndefined -> true
        else -> left === right
    }

    private fun truthy(value: ModelValue): Boolean = when (value) {
        ModelUndefined, ModelNull -> false
        is ModelBoolean -> value.value
        is ModelNumber -> value.value != 0.0 && !value.value.isNaN()
        is ModelString -> value.value.isNotEmpty()
        is ModelArray, is ModelObject, is ModelMap -> true
    }

    private fun decode(encoded: JsonObject): ModelValue = when (encoded.string("kind")) {
        "undefined" -> ModelUndefined
        "null" -> ModelNull
        "boolean" -> ModelBoolean(encoded.getValue("value").jsonPrimitive.boolean)
        "string" -> ModelString(encoded.string("value"))
        "number" -> ModelNumber(decodeNumber(encoded.string("value")))
        "array" -> ModelArray(encoded["elements"]?.jsonArray.orEmpty().map { decode(it.jsonObject) })
        "object" -> ModelObject(
            own = encoded["own"]?.jsonObject.orEmpty().mapValuesTo(mutableMapOf()) { decode(it.value.jsonObject) },
            prototype = encoded["prototype"]?.jsonObject?.let(::decode) as? ModelObject,
        )
        "map" -> ModelMap(
            encoded["entries"]?.jsonArray.orEmpty().mapTo(mutableListOf()) { entryElement ->
                val entry = entryElement.jsonObject
                ModelMapEntry(decode(entry.getValue("key").jsonObject), decode(entry.getValue("value").jsonObject))
            },
        )
        else -> error("unknown encoded value kind ${encoded.string("kind")}")
    }

    private fun decodeNumber(value: String): Double = when (value) {
        "NaN" -> Double.NaN
        "Infinity" -> Double.POSITIVE_INFINITY
        "-Infinity" -> Double.NEGATIVE_INFINITY
        "-0" -> -0.0
        else -> value.toDouble()
    }

    private fun encode(value: ModelValue): JsonObject = when (value) {
        ModelUndefined -> kindOnly("undefined")
        ModelNull -> kindOnly("null")
        is ModelBoolean -> kindAndValue("boolean", JsonPrimitive(value.value))
        is ModelString -> kindAndValue("string", JsonPrimitive(value.value))
        is ModelNumber -> kindAndValue("number", JsonPrimitive(encodeNumber(value.value)))
        else -> error("${value::class.simpleName} is outside the frozen result encoding")
    }

    private fun encodeNumber(value: Double): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        value == 0.0 && value.toRawBits() == (-0.0).toRawBits() -> "-0"
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun format(value: ModelValue): String = when (value) {
        ModelUndefined -> "undefined"
        ModelNull -> "null"
        is ModelBoolean -> "boolean:${value.value}"
        is ModelString -> "string:${JsonPrimitive(value.value)}"
        is ModelNumber -> "number:${encodeNumber(value.value)}"
        else -> error("${value::class.simpleName} cannot occur in a frozen trace result")
    }

    private fun kindOnly(kind: String) = JsonObject(mapOf("kind" to JsonPrimitive(kind)))

    private fun kindAndValue(kind: String, value: JsonPrimitive) = JsonObject(
        linkedMapOf("kind" to JsonPrimitive(kind), "value" to value),
    )
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
