package org.usvm.ts.pbt.semantics.iterator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
 * Independent executable model for the frozen synchronous iterator contract.
 *
 * It deliberately does not call the concrete EtsIR interpreter or any USVM
 * symbolic implementation. Integration packages must make those layers agree
 * with this model and the independent Node oracle.
 */
class IteratorSemanticsSpecTest {
    private val specPath: Path = getResourcePath("/semantics/iterator/iterator-semantics-v1.json")
    private val spec: JsonObject = Json.parseToJsonElement(specPath.toFile().readText()).jsonObject
    private val cases: JsonArray get() = spec.getValue("cases").jsonArray

    @Test
    fun `frozen iterator contract is complete explicit and closed`() {
        assertEquals(1, spec.getValue("schemaVersion").jsonPrimitive.content.toInt())
        assertEquals("usvm-ts-pbt.iterator.exact.v1", spec.string("contractId"))
        assertEquals(26, cases.size)
        val ids = cases.map { it.jsonObject.string("id") }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.containsAll(EXACT_BOUNDARY_CASES))

        val requiredOperations = spec.getValue("requiredOperations").jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()
        assertEquals(OPERATIONS, requiredOperations)
        cases.map(JsonElement::jsonObject).forEach { case ->
            assertTrue(case.string("sourceCallableId").startsWith(SOURCE_ID_PREFIX), case.string("id"))
            assertTrue(case.string("operation") in OPERATIONS, case.string("id"))
            validateCapability(case.string("id"), case.getValue("capability").jsonObject)
            assertEquals("exact", case.getValue("capability").jsonObject.string("expectedOutcome"))
        }

        val targets = spec.getValue("realTargets").jsonArray.map(JsonElement::jsonObject)
        assertEquals(9, targets.size)
        assertEquals(9, targets.map { it.getValue("etsIr").jsonObject.string("branchId") }.distinct().size)
        assertEquals(
            7,
            targets.count { it.getValue("expectedTerminalOutcome").jsonObject.string("status") == "replay_confirmed" },
        )
        assertEquals(
            2,
            targets.count {
                it.getValue("expectedTerminalOutcome").jsonObject.string("status") ==
                    "exact_capability_mismatch"
            },
        )
        targets.forEach { target ->
            validateCapability(target.string("id"), target.getValue("capability").jsonObject)
            val frozen = target.getValue("frozenOutcome").jsonObject
            assertEquals("reached_not_replayed", frozen.string("status"), target.string("id"))
            assertTrue(frozen.getValue("reached").jsonPrimitive.boolean, target.string("id"))
            assertFalse(frozen.getValue("replayConfirmed").jsonPrimitive.boolean, target.string("id"))
        }

        val eventEvidence = spec.getValue("collectionUnsupportedEvidence").jsonObject
        val runs = eventEvidence.getValue("runs").jsonArray.map(JsonElement::jsonObject)
        assertEquals(2, runs.size)
        runs.forEach { run ->
            assertEquals(25, run.int("executions"), run.string("id"))
            assertEquals(25, run.int("unsupported"), run.string("id"))
            assertEquals(25, run.int("reasonEvents"), run.string("id"))
            assertEquals("pbt_execution_outcome", run.string("countUnit"), run.string("id"))
            assertFalse(run.getValue("isUniqueTargetCount").jsonPrimitive.boolean, run.string("id"))
        }

        val unsupported = spec.getValue("unsupported").jsonArray.map(JsonElement::jsonObject)
        assertEquals(10, unsupported.size)
        unsupported.forEach { item ->
            validateCapability(item.string("id"), item.getValue("capability").jsonObject)
            assertTrue(item.string("terminalOutcome") in TERMINAL_OUTCOMES, item.string("id"))
            assertTrue(item.string("reason").isNotBlank(), item.string("id"))
        }
        val yieldBoundaries = unsupported.filter { item ->
            val tags = item.getValue("capability").jsonObject.strings("semanticTags")
            "generator_yield" in tags || "yield_star" in tags
        }
        assertEquals(2, yieldBoundaries.size)
        yieldBoundaries.forEach { item ->
            val labels = item.getValue("capability").jsonObject.strings("labels")
            assertTrue("iterator" in labels && "spread_yield" in labels, item.string("id"))
            assertFalse(item.getValue("capability").jsonObject.string("expectedStatus") == "supported")
        }

        val source = getResourcePath("/semantics/iterator/IteratorSemanticsFixture.ts").toFile().readText()
        listOf(
            "subject[Symbol.iterator]()",
            "iterator.next()",
            "for (const value of subject)",
            "iterator.return()",
            "for (const num of nums)",
            "for (const item of array)",
            "for (const element of array)",
        ).forEach { expression -> assertTrue(source.contains(expression), expression) }
    }

    @Test
    fun `Kotlin iterator model matches every frozen Node result and trace`() {
        cases.map(JsonElement::jsonObject).forEach { case ->
            assertEquals(case.getValue("expected"), IteratorSpecModel.run(case), case.string("id"))
        }
    }

    @Test
    fun `nine real targets and 25 event provenance resolve without silent drops`() {
        val root = repositoryRoot()
        val baseline = spec.getValue("baselineEvidence").jsonObject
        val observationsPath = root.resolve(baseline.string("observationsPath"))
        assertEquals(baseline.string("observationsSha256"), sha256(observationsPath))
        val reports = Json.parseToJsonElement(observationsPath.toFile().readText()).jsonObject
            .getValue("reports").jsonArray
            .map(JsonElement::jsonObject)

        val hybridReports = reports.filter { it.string("scenario") == "internal-pbt-usvm" }
        val allReachedNotReplayed = hybridReports.flatMap(::reachedNotReplayed)
        assertEquals(baseline.int("broadReachedNotReplayed"), allReachedNotReplayed.size)

        val iteratorMethods = baseline.strings("iteratorMethodIds").toSet()
        val observedIteratorKeys = hybridReports.flatMap { report ->
            report.getValue("methods").jsonArray.map(JsonElement::jsonObject)
                .filter { it.string("methodId") in iteratorMethods }
                .flatMap { method ->
                    reachedNotReplayed(method).map { target ->
                        "${report.string("projectId")}\t${target.string("branchId")}"
                    }
                }
        }.toSet()
        val frozenTargets = spec.getValue("realTargets").jsonArray.map(JsonElement::jsonObject)
        val frozenIteratorKeys = frozenTargets.map { target ->
            "${target.string("projectId")}\t${target.getValue("etsIr").jsonObject.string("branchId")}"
        }.toSet()
        assertEquals(9, observedIteratorKeys.size)
        assertEquals(observedIteratorKeys, frozenIteratorKeys)

        val manifests = baseline.getValue("targetManifests").jsonObject
        frozenTargets.forEach { target ->
            val projectId = target.string("projectId")
            val manifestEvidence = manifests.getValue(projectId).jsonObject
            val manifestPath = root.resolve(manifestEvidence.string("path"))
            assertEquals(manifestEvidence.string("sha256"), sha256(manifestPath), target.string("id"))
            val manifest = Json.parseToJsonElement(manifestPath.toFile().readText()).jsonObject
            val etsIr = target.getValue("etsIr").jsonObject
            val method = manifest.getValue("methods").jsonArray.map(JsonElement::jsonObject)
                .single { it.string("methodId") == etsIr.string("methodId") }
            val branch = method.getValue("branches").jsonArray.map(JsonElement::jsonObject)
                .single { it.string("branchId") == etsIr.string("branchId") }
            assertEquals(etsIr.int("ifStmtIndex"), branch.int("ifStmtIndex"), target.string("id"))
            assertEquals(etsIr.int("successorOrdinal"), branch.int("successorOrdinal"), target.string("id"))
            assertEquals(etsIr.int("successorStmtIndex"), branch.int("successorStmtIndex"), target.string("id"))
            assertEquals(
                target.getValue("source").jsonObject.getValue("conditionOrigin"),
                branch.getValue("conditionOrigin"),
                target.string("id"),
            )

            target.strings("observationScenarios").forEach { scenario ->
                val observations = reports
                    .filter { it.string("projectId") == projectId && it.string("scenario") == scenario }
                    .flatMap { it.getValue("methods").jsonArray.map(JsonElement::jsonObject) }
                    .filter { it.string("methodId") == etsIr.string("methodId") }
                    .flatMap(::symbolicTargets)
                    .filter { it.string("branchId") == etsIr.string("branchId") }
                assertEquals(1, observations.size, "${target.string("id")}:$scenario")
                assertTrue(observations.single().getValue("reached").jsonPrimitive.boolean)
                assertFalse(observations.single().getValue("replayConfirmed").jsonPrimitive.boolean)
            }
        }

        val eventRuns = spec.getValue("collectionUnsupportedEvidence").jsonObject
            .getValue("runs").jsonArray
            .map(JsonElement::jsonObject)
        eventRuns.forEach { event ->
            val report = reports.single {
                it.string("projectId") == event.string("projectId") &&
                    it.string("scenario") == event.string("scenario")
            }
            assertEquals(event.string("sourceReport"), report.string("sourceReport"), event.string("id"))
            assertEquals(event.string("sourceReportSha256"), report.string("sourceReportSha256"), event.string("id"))
            val method = report.getValue("methods").jsonArray.map(JsonElement::jsonObject)
                .single { it.string("methodId") == event.string("methodId") }
            val pbt = method.getValue("pbt").jsonObject
            assertEquals(event.int("executions"), pbt.int("executions"), event.string("id"))
            assertEquals(event.int("unsupported"), pbt.int("unsupported"), event.string("id"))
            assertEquals(
                event.int("reasonEvents"),
                pbt.getValue("unsupportedReasons").jsonObject.int(event.string("reason")),
                event.string("id"),
            )
        }
    }

    private fun validateCapability(id: String, capability: JsonObject) {
        val labels = capability.strings("labels")
        val tags = capability["semanticTags"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        assertTrue(labels.isNotEmpty(), id)
        assertTrue(labels.all { it in CAPABILITY_LABELS }, "$id: $labels")
        assertTrue(tags.all { it in SEMANTIC_TAGS }, "$id: $tags")
        assertTrue(labels.intersect(tags.toSet()).isEmpty(), id)
        assertTrue(capability.string("expectedStatus") in CAPABILITY_STATUSES, id)
    }

    private fun reachedNotReplayed(reportOrMethod: JsonObject): List<JsonObject> =
        if ("methods" in reportOrMethod) {
            reportOrMethod.getValue("methods").jsonArray.map(JsonElement::jsonObject)
                .flatMap(::reachedNotReplayed)
        } else {
            symbolicTargets(reportOrMethod).filter { target ->
                target.getValue("reached").jsonPrimitive.boolean &&
                    !target.getValue("replayConfirmed").jsonPrimitive.boolean
            }
        }

    private fun symbolicTargets(method: JsonObject): List<JsonObject> =
        method["symbolic"]?.jsonObject
            ?.get("targets")?.jsonArray
            ?.map(JsonElement::jsonObject)
            .orEmpty()

    private fun repositoryRoot(): Path = generateSequence(
        Path.of(System.getProperty("user.dir")).toAbsolutePath(),
        Path::getParent,
    ).firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
        ?: error("repository root is not reachable from ${System.getProperty("user.dir")}")

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE_ID_PREFIX = "ts:semantic/iterator/IteratorSemanticsFixture.ts::free:"

        val OPERATIONS = setOf(
            "iterator.nextSequence",
            "iterator.self",
            "forOf.collect",
            "forOf.collectTracked",
            "forOf.break",
            "forOf.return",
            "forOf.throw",
            "iterator.return",
        )

        val EXACT_BOUNDARY_CASES = setOf(
            "array-next-hole-yields-undefined",
            "string-next-unicode-code-points",
            "map-next-overwrite-retains-order",
            "set-next-order-and-same-value-zero",
            "for-of-break-closes-custom-iterator",
            "for-of-function-return-closes-custom-iterator",
            "for-of-throw-closes-custom-iterator-before-catch",
            "for-of-break-without-return-method",
            "for-of-empty-break-path-does-not-close",
        )

        val CAPABILITY_STATUSES = setOf(
            "supported",
            "supported_with_flag",
            "external_only",
            "unsupported",
            "needs_dynamic_probe",
        )

        // Closed WP-CAP v1 taxonomy. Fine-grained concepts belong to SEMANTIC_TAGS.
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
            "symbol_iterator",
            "iterator_next",
            "done_stability",
            "iterator_self",
            "array_iteration",
            "sparse_array",
            "string_code_points",
            "map_entries",
            "set_values",
            "insertion_order",
            "same_value_zero",
            "for_of",
            "iterator_close",
            "iterator_return",
            "abrupt_completion",
            "custom_iterable_subset",
            "async_iterator",
            "generator_yield",
            "yield_star",
            "mutation_during_iteration",
            "iterator_reentrancy",
            "proxy_trap",
            "invalid_iterator_result",
            "iterator_helpers",
        )

        val TERMINAL_OUTCOMES = setOf(
            "replay_confirmed",
            "exact_capability_mismatch",
            "exact_unsupported",
            "needs_dynamic_probe",
            "external_only",
        )
    }
}

private sealed interface IterValue
private object IterUndefined : IterValue
private object IterNull : IterValue
private data class IterBoolean(val value: Boolean) : IterValue
private data class IterString(val value: String) : IterValue
private data class IterNumber(val value: Double) : IterValue
private data class IterArray(val elements: List<IterValue>) : IterValue
private data class IterMap(val entries: MutableList<IterMapEntry>) : IterValue
private data class IterMapEntry(val key: IterValue, var value: IterValue)
private data class IterSet(val values: MutableList<IterValue>) : IterValue
private data class IterCustom(
    val values: List<IterValue>,
    val hasReturn: Boolean,
    var iteratorGets: Int = 0,
    var nextCalls: Int = 0,
    var returnCalls: Int = 0,
) : IterValue
private data class IterResult(val value: IterValue, val done: Boolean) : IterValue
private data class IterRecord(val fields: LinkedHashMap<String, IterValue>) : IterValue

private class ModelIterator(
    private val values: List<IterValue>,
    private val custom: IterCustom?,
) {
    private var index = 0
    private var closed = false

    fun next(): IterResult {
        if (custom != null) custom.nextCalls += 1
        if (closed || index >= values.size) return IterResult(IterUndefined, done = true)
        return IterResult(values[index++], done = false)
    }

    fun close(trace: MutableList<String>): IterResult {
        require(custom?.hasReturn == true) { "iterator has no return method" }
        custom.returnCalls += 1
        closed = true
        return IterResult(IterString("closed"), done = true).also { result ->
            trace += "return() -> ${IteratorSpecModel.format(result)}"
        }
    }
}

private object IteratorSpecModel {
    fun run(case: JsonObject): JsonObject {
        val subject = decode(case.getValue("iterable").jsonObject)
        val inputKind = case.getValue("iterable").jsonObject.string("kind")
        val trace = mutableListOf("Symbol.iterator($inputKind) -> iterator")
        val result = when (case.string("operation")) {
            "iterator.nextSequence" -> nextSequence(subject, case.getValue("arguments").jsonObject.int("count"), trace)
            "iterator.self" -> IterBoolean(true).also {
                trace += "iterator[Symbol.iterator]() === iterator -> boolean:true"
            }
            "forOf.collect" -> collect(subject, trace)
            "forOf.collectTracked" -> collectTracked(subject as IterCustom, trace)
            "forOf.break" -> breakTracked(subject as IterCustom, trace)
            "forOf.return" -> returnTracked(subject as IterCustom, trace)
            "forOf.throw" -> throwTracked(subject as IterCustom, trace)
            "iterator.return" -> directReturn(subject as IterCustom, trace)
            else -> error("unknown operation ${case.string("operation")}")
        }
        return buildJsonObject {
            put("result", encode(result))
            put("trace", buildJsonArray { trace.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun nextSequence(subject: IterValue, count: Int, trace: MutableList<String>): IterValue {
        val iterator = acquire(subject)
        val results = buildList {
            repeat(count) {
                val result = iterator.next()
                add(result)
                trace += "next() -> ${format(result)}"
            }
        }
        return IterArray(results)
    }

    private fun collect(subject: IterValue, trace: MutableList<String>): IterValue {
        val values = consume(acquire(subject), trace)
        trace += "for-of complete"
        return IterArray(values)
    }

    private fun collectTracked(subject: IterCustom, trace: MutableList<String>): IterValue {
        val values = consume(acquire(subject), trace)
        trace += "for-of complete"
        return record("values" to IterArray(values), "stats" to stats(subject))
    }

    private fun breakTracked(subject: IterCustom, trace: MutableList<String>): IterValue {
        val iterator = acquire(subject)
        val first = iterator.next()
        val values = if (first.done) {
            trace += "for-of complete"
            emptyList()
        } else {
            trace += "for-of value -> ${format(first.value)}"
            if (subject.hasReturn) iterator.close(trace)
            trace += "for-of break"
            listOf(first.value)
        }
        return record(
            "values" to IterArray(values),
            "broke" to IterBoolean(!first.done),
            "stats" to stats(subject),
        )
    }

    private fun returnTracked(subject: IterCustom, trace: MutableList<String>): IterValue {
        val iterator = acquire(subject)
        val first = iterator.next()
        if (!first.done) {
            trace += "for-of value -> ${format(first.value)}"
            if (subject.hasReturn) iterator.close(trace)
        }
        trace += "for-of function return"
        return record("value" to first.value, "stats" to stats(subject))
    }

    private fun throwTracked(subject: IterCustom, trace: MutableList<String>): IterValue {
        val iterator = acquire(subject)
        val first = iterator.next()
        if (!first.done) {
            trace += "for-of value -> ${format(first.value)}"
            if (subject.hasReturn) iterator.close(trace)
        }
        trace += "for-of throw -> string:\"boom\""
        return record("error" to IterString("boom"), "stats" to stats(subject))
    }

    private fun directReturn(subject: IterCustom, trace: MutableList<String>): IterValue {
        val iterator = acquire(subject)
        val first = iterator.next()
        trace += "next() -> ${format(first)}"
        val close = iterator.close(trace)
        return record("first" to first, "close" to close, "stats" to stats(subject))
    }

    private fun consume(iterator: ModelIterator, trace: MutableList<String>): List<IterValue> = buildList {
        while (true) {
            val result = iterator.next()
            if (result.done) break
            add(result.value)
            trace += "for-of value -> ${format(result.value)}"
        }
    }

    private fun acquire(subject: IterValue): ModelIterator = when (subject) {
        is IterArray -> ModelIterator(subject.elements, custom = null)
        is IterString -> ModelIterator(
            subject.value.codePoints().toArray().map { codePoint ->
                IterString(String(Character.toChars(codePoint)))
            },
            custom = null,
        )
        is IterMap -> ModelIterator(
            subject.entries.map { entry -> IterArray(listOf(entry.key, entry.value)) },
            custom = null,
        )
        is IterSet -> ModelIterator(subject.values, custom = null)
        is IterCustom -> ModelIterator(subject.values, subject).also { subject.iteratorGets += 1 }
        else -> error("${subject::class.simpleName} is not iterable")
    }

    private fun stats(subject: IterCustom): IterRecord = record(
        "iteratorGets" to IterNumber(subject.iteratorGets.toDouble()),
        "nextCalls" to IterNumber(subject.nextCalls.toDouble()),
        "returnCalls" to IterNumber(subject.returnCalls.toDouble()),
    )

    private fun decode(encoded: JsonObject): IterValue = when (encoded.string("kind")) {
        "undefined", "hole" -> IterUndefined
        "null" -> IterNull
        "boolean" -> IterBoolean(encoded.getValue("value").jsonPrimitive.boolean)
        "string" -> IterString(encoded.string("value"))
        "number" -> IterNumber(decodeNumber(encoded.string("value")))
        "array" -> IterArray(encoded["elements"]?.jsonArray.orEmpty().map { decode(it.jsonObject) })
        "map" -> decodeMap(encoded)
        "set" -> decodeSet(encoded)
        "customIterable" -> IterCustom(
            values = encoded["values"]?.jsonArray.orEmpty().map { decode(it.jsonObject) },
            hasReturn = encoded.getValue("hasReturn").jsonPrimitive.boolean,
        )
        else -> error("unknown encoded value kind ${encoded.string("kind")}")
    }

    private fun decodeMap(encoded: JsonObject): IterMap {
        val entries = mutableListOf<IterMapEntry>()
        encoded["entries"]?.jsonArray.orEmpty().forEach { element ->
            val entry = element.jsonObject
            val key = normalizeCollectionKey(decode(entry.getValue("key").jsonObject))
            val value = decode(entry.getValue("value").jsonObject)
            val existing = entries.firstOrNull { sameValueZero(it.key, key) }
            if (existing == null) entries += IterMapEntry(key, value) else existing.value = value
        }
        return IterMap(entries)
    }

    private fun decodeSet(encoded: JsonObject): IterSet {
        val values = mutableListOf<IterValue>()
        encoded["values"]?.jsonArray.orEmpty().forEach { element ->
            val value = normalizeCollectionKey(decode(element.jsonObject))
            if (values.none { sameValueZero(it, value) }) values += value
        }
        return IterSet(values)
    }

    private fun normalizeCollectionKey(value: IterValue): IterValue =
        if (value is IterNumber && value.value == 0.0) IterNumber(0.0) else value

    private fun sameValueZero(left: IterValue, right: IterValue): Boolean = when {
        left is IterNumber && right is IterNumber ->
            left.value == right.value || (left.value.isNaN() && right.value.isNaN())
        left is IterString && right is IterString -> left.value == right.value
        left is IterBoolean && right is IterBoolean -> left.value == right.value
        left === IterNull && right === IterNull -> true
        left === IterUndefined && right === IterUndefined -> true
        else -> left === right
    }

    private fun decodeNumber(value: String): Double = when (value) {
        "NaN" -> Double.NaN
        "Infinity" -> Double.POSITIVE_INFINITY
        "-Infinity" -> Double.NEGATIVE_INFINITY
        "-0" -> -0.0
        else -> value.toDouble()
    }

    private fun encode(value: IterValue): JsonObject = when (value) {
        IterUndefined -> kindOnly("undefined")
        IterNull -> kindOnly("null")
        is IterBoolean -> kindAndValue("boolean", JsonPrimitive(value.value))
        is IterString -> kindAndValue("string", JsonPrimitive(value.value))
        is IterNumber -> kindAndValue("number", JsonPrimitive(encodeNumber(value.value)))
        is IterArray -> buildJsonObject {
            put("kind", "array")
            put("elements", buildJsonArray { value.elements.forEach { add(encode(it)) } })
        }
        is IterResult -> buildJsonObject {
            put("kind", "iteratorResult")
            put("value", encode(value.value))
            put("done", value.done)
        }
        is IterRecord -> buildJsonObject {
            put("kind", "record")
            put("fields", buildJsonObject { value.fields.forEach { (key, child) -> put(key, encode(child)) } })
        }
        is IterMap, is IterSet, is IterCustom -> error("${value::class.simpleName} cannot be a frozen result")
    }

    fun format(value: IterValue): String = when (value) {
        IterUndefined -> "undefined"
        IterNull -> "null"
        is IterBoolean -> "boolean:${value.value}"
        is IterString -> "string:${JsonPrimitive(value.value)}"
        is IterNumber -> "number:${encodeNumber(value.value)}"
        is IterArray -> "array:[${value.elements.joinToString(", ", transform = ::format)}]"
        is IterResult -> "{value:${format(value.value)}, done:${value.done}}"
        is IterRecord -> formatRecord(value)
        is IterMap, is IterSet, is IterCustom -> error("${value::class.simpleName} cannot occur in a trace")
    }

    private fun formatRecord(value: IterRecord): String {
        val fields = value.fields.entries.joinToString(", ") { (key, child) ->
            "$key=${format(child)}"
        }
        return "record:{$fields}"
    }

    private fun encodeNumber(value: Double): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        value == 0.0 && value.toRawBits() == (-0.0).toRawBits() -> "-0"
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun record(vararg fields: Pair<String, IterValue>): IterRecord = IterRecord(linkedMapOf(*fields))

    private fun kindOnly(kind: String) = JsonObject(mapOf("kind" to JsonPrimitive(kind)))

    private fun kindAndValue(kind: String, value: JsonPrimitive) = JsonObject(
        linkedMapOf("kind" to JsonPrimitive(kind), "value" to value),
    )
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.content.toInt()

private fun JsonObject.strings(key: String): List<String> =
    getValue(key).jsonArray.map { it.jsonPrimitive.content }

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
