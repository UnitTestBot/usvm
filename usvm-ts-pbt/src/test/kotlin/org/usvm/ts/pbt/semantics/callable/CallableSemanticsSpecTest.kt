package org.usvm.ts.pbt.semantics.callable

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.external.ExternalTestCorpusCodec
import org.usvm.ts.pbt.external.ExternalValueCodec
import org.usvm.ts.pbt.external.ExternalValueConversionException
import org.usvm.ts.pbt.util.getResourcePath
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Independent executable model for the frozen callable contract.
 *
 * It deliberately does not call the concrete EtsIR interpreter, symbolic
 * runtime, or any production callable dispatcher. Integration agents must make
 * those layers agree independently with this model and the Node oracle.
 */
class CallableSemanticsSpecTest {
    private val specPath: Path = getResourcePath("/semantics/callable/callable-semantics-v1.json")
    private val spec: JsonObject = Json.parseToJsonElement(specPath.toFile().readText()).jsonObject
    private val cases: JsonArray get() = spec.getValue("cases").jsonArray

    @Test
    fun `frozen callable contract is complete closed and source addressable`() {
        assertEquals(1, spec.int("schemaVersion"))
        assertEquals("usvm-ts-pbt.callable.exact.v1", spec.string("contractId"))
        assertEquals(2, spec.int("etcSchemaVersion"))
        assertEquals(20, cases.size)

        val ids = cases.map { it.jsonObject.string("id") }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.containsAll(EXACT_CASE_IDS))
        assertEquals(13, cases.count { it.jsonObject.plan().string("outcome") == "materialized" })
        assertEquals(7, cases.count { it.jsonObject.plan().string("outcome") == "rejected" })

        val dispatchKinds = cases.mapNotNull { element ->
            element.jsonObject.plan()["dispatchKind"]?.jsonPrimitive?.content
        }.toSet()
        assertEquals(DISPATCH_KINDS, dispatchKinds)
        assertEquals(
            DISPATCH_KINDS,
            spec.getValue("requiredDispatchKinds").jsonArray.map { it.jsonPrimitive.content }.toSet(),
        )

        cases.forEach { element ->
            val case = element.jsonObject
            val id = case.string("id")
            assertTrue(case.string("sourceCallableId").startsWith(SOURCE_ID_PREFIX), id)
            val etcCase = case.getValue("etcCase").jsonObject
            assertEquals("etc-$id", etcCase.string("id"), id)
            assertEquals(case.string("sourceCallableId"), etcCase.string("methodId"), id)
            assertEquals(0, etcCase.int("generatedAtMs"), id)
            assertEquals("callable-contract:$id", etcCase.string("path"), id)
            assertEquals(spec.string("contractId"), etcCase.getValue("metadata").jsonObject.string("callableContract"))

            val plan = case.plan()
            val capability = case.getValue("capability").jsonObject
            val labels = capability.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
            val semanticTags = capability.getValue("semanticTags").jsonArray.map { it.jsonPrimitive.content }
            val flags = capability.getValue("requiredFlags").jsonArray.map { it.jsonPrimitive.content }
            assertTrue(labels.isNotEmpty() && labels.all { it in CAPABILITY_LABELS }, "$id: $labels")
            assertTrue(semanticTags.all { it in SEMANTIC_TAGS }, "$id: $semanticTags")
            assertTrue(labels.intersect(semanticTags.toSet()).isEmpty(), id)
            assertTrue(flags.all { it in REQUIRED_FLAGS }, "$id: $flags")
            assertTrue(capability.string("expectedStatus") in CAPABILITY_STATUSES, id)
            assertEquals(
                if (plan.string("outcome") == "materialized") "exact" else "stable_reject",
                capability.string("expectedOutcome"),
                id,
            )
        }

        val unsupported = spec.getValue("unsupported").jsonArray.map(JsonElement::jsonObject)
        assertEquals(7, unsupported.size)
        unsupported.forEach { boundary ->
            val case = cases.map(JsonElement::jsonObject).single { it.string("id") == boundary.string("caseId") }
            assertEquals("rejected", case.plan().string("outcome"))
            assertEquals(boundary.string("reasonCode"), case.plan().string("reasonCode"))
            assertEquals(
                boundary.string("expectedStatus"),
                case.getValue("capability").jsonObject.string("expectedStatus"),
            )
            assertTrue(boundary.string("reason").isNotBlank())
        }

        val fixture = getResourcePath("/semantics/callable/CallableSemanticsFixture.ts").toFile().readText()
        val library = getResourcePath("/semantics/callable/CallableSemanticsLibrary.ts").toFile().readText()
        listOf(
            "const topLevelArrow = (value) => value * 2",
            "receiver[fieldName](...args)",
            "callable.call(receiver, ...args)",
            "recursiveFactorial(value - 1)",
            "arguments.length",
            "new Proxy",
            "async function asyncIdentity",
            "function* generatorIdentity",
        ).forEach { expression -> assertTrue(fixture.contains(expression), expression) }
        assertTrue(library.contains("const importedArrow = (left, right) => left * right"))
    }

    @Test
    fun `embedded ETC v2 cases decode and generic conversion never erases callable values`() {
        cases.forEach { element ->
            val case = element.jsonObject
            val jsonLines = buildString {
                appendLine("""{"schemaVersion":2,"producer":"callable-semantics@1"}""")
                appendLine(case.getValue("etcCase").toString())
            }
            val decoded = ExternalTestCorpusCodec.decode(jsonLines, "${case.string("id")}.jsonl")
            assertEquals(2, decoded.schemaVersion, case.string("id"))
            assertEquals("callable-semantics@1", decoded.producer, case.string("id"))
            assertTrue(decoded.rejections.isEmpty(), "${case.string("id")}: ${decoded.rejections}")
            assertEquals(1, decoded.cases.size, case.string("id"))
            assertEquals(case.string("sourceCallableId"), decoded.cases.single().methodId, case.string("id"))
        }

        val direct = cases.map(JsonElement::jsonObject).single { it.string("id") == "direct-function" }
        val directDecoded = decodeSingleEtcCase(direct)
        val callable = directDecoded.arguments.first()
        assertEquals("callable", callable.kind)
        assertEquals("directAdd", callable.callableReference?.exportName)
        val callableFailure = assertThrows(ExternalValueConversionException::class.java) {
            ExternalValueCodec.toVValue(callable)
        }
        assertTrue(callableFailure.message.orEmpty().contains("scene-aware decoder"))

        val constructorCase = cases.map(JsonElement::jsonObject)
            .single { it.string("id") == "constructor-instance-method" }
        val constructorReceiver = decodeSingleEtcCase(constructorCase).receiver
        assertEquals("ReceiverBox", constructorReceiver.className)
        assertEquals("class", constructorReceiver.constructorPlan?.callable?.callableKind)
        val constructorFailure = assertThrows(ExternalValueConversionException::class.java) {
            ExternalValueCodec.toVValue(constructorReceiver)
        }
        assertTrue(constructorFailure.message.orEmpty().contains("scene-aware decoder"))
    }

    @Test
    fun `Kotlin callable model matches every frozen Node result and trace`() {
        cases.forEach { element ->
            val case = element.jsonObject
            assertEquals(case.getValue("expected"), CallableSpecModel.run(case), case.string("id"))
        }
    }

    @Test
    fun `exact 11 shared residuals resolve without silent drops or duplicate union IDs`() {
        val root = repositoryRoot()
        val evidence = spec.getValue("baselineEvidence").jsonObject
        val observationsPath = root.resolve(evidence.string("observationsPath"))
        val manifestPath = root.resolve(evidence.string("targetManifestPath"))
        assertEquals(evidence.string("observationsSha256"), sha256(observationsPath))
        assertEquals(evidence.string("targetManifestSha256"), sha256(manifestPath))
        assertEquals("semantics/module/module-semantics-v1.json", evidence.string("crossReference"))
        assertEquals("branchId", evidence.string("deduplicationKey"))

        val observations = Json.parseToJsonElement(observationsPath.toFile().readText()).jsonObject
        val report = observations.getValue("reports").jsonArray
            .map(JsonElement::jsonObject)
            .single {
                it.string("projectId") == evidence.string("projectId") &&
                    it.string("scenario") == evidence.string("scenario") &&
                    it.string("denominatorScope") == evidence.string("denominatorScope") &&
                    it.string("sourceReport") == evidence.string("sourceReport") &&
                    it.string("sourceReportSha256") == evidence.string("sourceReportSha256")
            }
        val manifest = Json.parseToJsonElement(manifestPath.toFile().readText()).jsonObject
        val blockers = spec.getValue("residualBlockers").jsonArray.map(JsonElement::jsonObject)
        assertEquals(11, blockers.size)
        val branchIds = blockers.map { it.getValue("etsIr").jsonObject.string("branchId") }
        assertEquals(branchIds.size, branchIds.distinct().size)
        assertEquals(FROZEN_BRANCH_IDS, branchIds.toSet())

        blockers.forEach { blocker ->
            val etsIr = blocker.getValue("etsIr").jsonObject
            assertEquals("shared_module_callable", blocker.string("provenanceScope"), blocker.string("id"))
            assertEquals("module_bound_callable_dispatch", blocker.string("semanticClass"), blocker.string("id"))
            assertEquals(
                "materialized_function_value_callable_dispatch",
                blocker.string("ownershipClaim"),
                blocker.string("id"),
            )
            assertEquals("WP-SEM-MODULE", blocker.string("sharedWith"), blocker.string("id"))
            assertEquals("branchId", blocker.string("unionKey"), blocker.string("id"))
            assertEquals(etsIr.string("branchId"), blocker.string("etsIrOriginId"), blocker.string("id"))
            assertEquals("exact", blocker.string("sourceBindingStatus"), blocker.string("id"))
            assertEquals("unmapped", blocker.string("etsIrMappingStatus"), blocker.string("id"))
            assertTrue(blocker.string("mappingEvidence").isNotBlank(), blocker.string("id"))
            val outcome = blocker.getValue("frozenOutcome").jsonObject
            assertEquals("semantic_mismatch", outcome.string("status"), blocker.string("id"))
            assertTrue(outcome.boolean("reached"), blocker.string("id"))
            assertFalse(outcome.boolean("replayConfirmed"), blocker.string("id"))

            val method = manifest.getValue("methods").jsonArray
                .map(JsonElement::jsonObject)
                .single { it.string("methodId") == etsIr.string("methodId") }
            val branch = method.getValue("branches").jsonArray
                .map(JsonElement::jsonObject)
                .single { it.string("branchId") == etsIr.string("branchId") }
            listOf("ifStmtIndex", "successorOrdinal", "successorStmtIndex").forEach { field ->
                assertEquals(etsIr.int(field), branch.int(field), "${blocker.string("id")}: $field")
            }
            assertEquals(etsIr.getValue("conditionOrigin"), branch.getValue("conditionOrigin"), blocker.string("id"))
            assertEquals(etsIr["successorOrigin"], branch["successorOrigin"], blocker.string("id"))

            val methodReport = report.getValue("methods").jsonArray
                .map(JsonElement::jsonObject)
                .single { it.string("methodId") == etsIr.string("methodId") }
            val target = methodReport.getValue("symbolic").jsonObject.getValue("targets").jsonArray
                .map(JsonElement::jsonObject)
                .single { it.string("branchId") == etsIr.string("branchId") }
            assertTrue(target.boolean("reached"), blocker.string("id"))
            assertFalse(target.boolean("replayConfirmed"), blocker.string("id"))
            val observedFailure = methodReport.getValue("pbt").jsonObject.getValue("failures").jsonArray
                .first().jsonObject.string("description")
            assertEquals(evidence.string("observedFailure"), observedFailure, blocker.string("id"))
        }
    }

    private fun decodeSingleEtcCase(case: JsonObject) = ExternalTestCorpusCodec.decode(
        buildString {
            appendLine("""{"schemaVersion":2,"producer":"callable-semantics@1"}""")
            appendLine(case.getValue("etcCase").toString())
        },
        "${case.string("id")}.jsonl",
    ).cases.single()

    private fun repositoryRoot(): Path = generateSequence(
        Path.of(System.getProperty("user.dir")).toAbsolutePath(),
        Path::getParent,
    ).firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
        ?: error("repository root is not reachable from ${System.getProperty("user.dir")}")

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE_ID_PREFIX = "ts:semantic/callable/"

        val DISPATCH_KINDS = setOf("direct", "field", "imported", "call")
        val EXACT_CASE_IDS = setOf(
            "direct-function",
            "top-level-arrow",
            "imported-arrow-direct",
            "imported-function-direct",
            "function-in-field",
            "field-receiver-binding",
            "constructor-instance-method",
            "static-method",
            "explicit-call-receiver",
            "direct-recursion",
            "callback-extra-arguments",
            "callback-missing-argument",
            "imported-arrow-in-field",
        )
        val FROZEN_BRANCH_IDS = setOf(
            "arrays.ts::%dflt::indexOf/3#s9:0->10",
            "arrays.ts::%dflt::indexOf/3#s9:1->18",
            "arrays.ts::%dflt::lastIndexOf/3#s9:0->10",
            "arrays.ts::%dflt::lastIndexOf/3#s9:1->18",
            "arrays.ts::%dflt::remove/3#s6:0->7",
            "arrays.ts::%dflt::frequency/3#s10:0->11",
            "arrays.ts::%dflt::frequency/3#s10:1->20",
            "arrays.ts::%dflt::equals/3#s9:0->10",
            "arrays.ts::%dflt::equals/3#s9:1->12",
            "arrays.ts::%dflt::equals/3#s15:0->16",
            "arrays.ts::%dflt::equals/3#s15:1->26",
        )
        val CAPABILITY_STATUSES = setOf(
            "supported",
            "supported_with_flag",
            "external_only",
            "unsupported",
            "needs_dynamic_probe",
        )
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
        val REQUIRED_FLAGS = setOf("callableValueModel", "moduleRuntimeModel")
        val SEMANTIC_TAGS = setOf(
            "etc_v2_callable_reference",
            "etc_v2_constructor_plan",
            "direct_dispatch",
            "top_level_arrow",
            "imported_callable",
            "function_field",
            "receiver_binding",
            "instance_method",
            "static_method",
            "explicit_call",
            "recursion",
            "callback_arity",
            "extra_arguments",
            "missing_arguments",
            "stable_reject",
            "captured_environment",
            "closure",
            "bound_callable",
            "proxy_callable",
            "async_callable",
            "generator_callable",
            "dynamic_callable",
            "shared_module_callable",
            "materialized_function_value",
            "callable_dispatch",
        )
    }
}

private sealed interface ModelValue
private data object ModelUndefined : ModelValue
private data object ModelNull : ModelValue
private data class ModelNumber(val value: Double) : ModelValue
private data class ModelBoolean(val value: Boolean) : ModelValue
private data class ModelString(val value: String) : ModelValue
private data class ModelObject(
    val className: String?,
    val fields: MutableMap<String, ModelValue>,
) : ModelValue

private data class ModelCallable(
    val referenceKey: String,
    val stableCallableId: String,
    val arity: Int,
    val invoke: (ModelValue, List<ModelValue>) -> ModelValue,
    val construct: ((List<ModelValue>) -> ModelObject)? = null,
) : ModelValue

private object CallableSpecModel {
    private const val FIXTURE_MODULE = "semantics/callable/CallableSemanticsFixture.ts"
    private const val LIBRARY_MODULE = "semantics/callable/CallableSemanticsLibrary.ts"

    private val registry: Map<String, ModelCallable> by lazy {
        val receiverAdd = callable(
            FIXTURE_MODULE,
            "ReceiverBox.prototype.add",
            "instanceMethod",
            "ts:semantic/callable/CallableSemanticsFixture.ts::instance:ReceiverBox.add/1",
            1,
        ) { receiver, arguments ->
            val base = (receiver.objectField("base") as ModelNumber).value
            ModelNumber(base + (arguments.single() as ModelNumber).value)
        }
        val values = listOf(
            callable(
                FIXTURE_MODULE,
                "directAdd",
                "function",
                "ts:semantic/callable/CallableSemanticsFixture.ts::free:directAdd/2",
                2,
            ) { _, arguments -> ModelNumber(arguments.numbers().sum()) },
            callable(
                FIXTURE_MODULE,
                "topLevelArrow",
                "arrow",
                "ts:semantic/callable/CallableSemanticsFixture.ts::arrow:topLevelArrow/1",
                1,
            ) { _, arguments -> ModelNumber(arguments.numbers().single() * 2) },
            callable(
                LIBRARY_MODULE,
                "importedArrow",
                "arrow",
                "ts:semantic/callable/CallableSemanticsLibrary.ts::arrow:importedArrow/2",
                2,
            ) { _, arguments -> ModelNumber(arguments.numbers().reduce(Double::times)) },
            callable(
                LIBRARY_MODULE,
                "importedOffset",
                "function",
                "ts:semantic/callable/CallableSemanticsLibrary.ts::free:importedOffset/1",
                1,
            ) { _, arguments -> ModelNumber(arguments.numbers().single() + 40) },
            callable(
                FIXTURE_MODULE,
                "fieldMultiply",
                "function",
                "ts:semantic/callable/CallableSemanticsFixture.ts::free:fieldMultiply/2",
                2,
            ) { _, arguments -> ModelNumber(arguments.numbers().reduce(Double::times)) },
            callable(
                FIXTURE_MODULE,
                "readBase",
                "function",
                "ts:semantic/callable/CallableSemanticsFixture.ts::free:readBase/1",
                1,
            ) { receiver, arguments ->
                val base = (receiver.objectField("base") as ModelNumber).value
                ModelNumber(base + arguments.numbers().single())
            },
            receiverAdd,
            callable(
                FIXTURE_MODULE,
                "ReceiverBox.staticSum",
                "staticMethod",
                "ts:semantic/callable/CallableSemanticsFixture.ts::static:ReceiverBox.staticSum/2",
                2,
            ) { _, arguments -> ModelNumber(arguments.numbers().sum()) },
            callable(
                FIXTURE_MODULE,
                "recursiveFactorial",
                "function",
                "ts:semantic/callable/CallableSemanticsFixture.ts::free:recursiveFactorial/1",
                1,
            ) { _, arguments -> ModelNumber(factorial(arguments.numbers().single().toInt()).toDouble()) },
            callable(
                FIXTURE_MODULE,
                "arityPair",
                "function",
                "ts:semantic/callable/CallableSemanticsFixture.ts::free:arityPair/2",
                2,
            ) { _, arguments ->
                val first = formatJs(arguments.firstOrNull() ?: ModelUndefined)
                val second = formatJs(arguments.getOrNull(1) ?: ModelUndefined)
                ModelString("${arguments.size}:$first:$second")
            },
            ModelCallable(
                referenceKey = referenceKey(FIXTURE_MODULE, "ReceiverBox", "class"),
                stableCallableId = "ts:semantic/callable/CallableSemanticsFixture.ts::class:ReceiverBox/1",
                arity = 1,
                invoke = { _, _ -> error("ReceiverBox is constructor-only") },
                construct = { arguments ->
                    ModelObject(
                        className = "ReceiverBox",
                        fields = linkedMapOf(
                            "base" to arguments.single(),
                            "add" to receiverAdd,
                        ),
                    )
                },
            ),
        )
        values.associateBy(ModelCallable::referenceKey)
    }

    fun run(case: JsonObject): JsonObject {
        val plan = case.plan()
        val etcCase = case.getValue("etcCase")
        if (plan.string("outcome") == "rejected") {
            val encoded = pointer(etcCase, plan.string("valuePath")).jsonObject
            require(encoded.string("kind") == "unrepresentable")
            require(encoded.string("unrepresentableKind") == "function")
            val reasonCode = plan.string("reasonCode")
            return buildJsonObject {
                put("outcome", "rejected")
                put("reasonCode", reasonCode)
                put(
                    "trace",
                    buildJsonArray {
                        add(JsonPrimitive("reject callable ${plan.string("valuePath")} kind=function -> $reasonCode"))
                    },
                )
            }
        }

        val callablePath = plan.string("callablePath")
        val encodedCallable = pointer(etcCase, callablePath).jsonObject
        val callable = decode(encodedCallable) as ModelCallable
        require(callable.stableCallableId == plan.string("stableCallableId"))
        require(callable.arity == plan.int("declaredArity"))
        val receiverPath = plan["receiverPath"]?.jsonPrimitive?.content
        val encodedReceiver = receiverPath?.let { pointer(etcCase, it).jsonObject }
            ?: JsonObject(mapOf("kind" to JsonPrimitive("undefined")))
        val receiver = if (receiverPath == null) ModelUndefined else decode(encodedReceiver)
        val arguments = plan.getValue("argumentPaths").jsonArray.map {
            decode(pointer(etcCase, it.jsonPrimitive.content).jsonObject)
        }

        if (plan.string("dispatchKind") == "field") {
            val field = receiver.objectField(plan.string("fieldName")) as ModelCallable
            require(field.stableCallableId == callable.stableCallableId)
        }
        val result = callable.invoke(receiver, arguments)
        val encodedResult = encode(result)
        val traces = mutableListOf(materializeTrace(callablePath, encodedCallable, callable))
        constructorTrace(receiverPath, encodedReceiver)?.let(traces::add)
        val field = plan["fieldName"]?.jsonPrimitive?.content?.let { " field=$it" }.orEmpty()
        traces += "dispatch ${plan.string("dispatchKind")} ${callable.stableCallableId} " +
            "receiver=${receiverLabel(receiver, encodedReceiver)}$field " +
            "args=[${arguments.joinToString(",", transform = ::format)}] -> ${format(result)}"

        return buildJsonObject {
            put("outcome", "materialized")
            put("result", encodedResult)
            put("trace", buildJsonArray { traces.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private fun decode(encoded: JsonObject): ModelValue = when (encoded.string("kind")) {
        "undefined" -> ModelUndefined
        "null" -> ModelNull
        "number" -> ModelNumber(decodeNumber(encoded.string("value")))
        "boolean" -> ModelBoolean(encoded.string("value") == "true")
        "string" -> ModelString(encoded.string("value"))
        "callable" -> resolve(encoded.getValue("callableReference").jsonObject)
        "object" -> decodeObject(encoded)
        else -> error("ETC kind ${encoded.string("kind")} is outside the exact callable model")
    }

    private fun decodeObject(encoded: JsonObject): ModelObject {
        val constructorPlan = encoded["constructorPlan"]?.jsonObject
        val result = if (constructorPlan == null) {
            ModelObject(className = null, fields = linkedMapOf())
        } else {
            val constructor = resolve(constructorPlan.getValue("callable").jsonObject)
            requireNotNull(constructor.construct) { "constructor plan resolved to non-class" }(
                constructorPlan.getValue("arguments").jsonArray.map { decode(it.jsonObject) },
            )
        }
        encoded.getValue("properties").jsonArray.map(JsonElement::jsonObject).forEach { property ->
            require(!result.fields.containsKey(property.string("key")))
            result.fields[property.string("key")] = decode(property.getValue("value").jsonObject)
        }
        return result
    }

    private fun resolve(reference: JsonObject): ModelCallable {
        val key = referenceKey(
            reference.string("modulePath"),
            reference.string("exportName"),
            reference.string("callableKind"),
        )
        return requireNotNull(registry[key]) {
            "callable reference is outside the exact Kotlin registry: $reference"
        }
    }

    private fun encode(value: ModelValue): JsonObject = when (value) {
        ModelUndefined -> kindOnly("undefined")
        ModelNull -> kindOnly("null")
        is ModelNumber -> kindAndValue("number", encodeNumber(value.value))
        is ModelBoolean -> kindAndValue("boolean", value.value.toString())
        is ModelString -> kindAndValue("string", value.value)
        is ModelCallable -> error("refusing callable-to-undefined fallback for ${value.stableCallableId}")
        is ModelObject -> error("object result is outside the exact result subset")
    }

    private fun materializeTrace(path: String, encoded: JsonObject, callable: ModelCallable): String {
        val reference = encoded.getValue("callableReference").jsonObject
        return "materialize callable $path ${reference.string("modulePath")}#${reference.string("exportName")} " +
            "kind=${reference.string("callableKind")} arity=${callable.arity} -> ${callable.stableCallableId}"
    }

    private fun constructorTrace(receiverPath: String?, receiver: JsonObject): String? {
        val constructorPlan = receiver["constructorPlan"]?.jsonObject ?: return null
        val reference = constructorPlan.getValue("callable").jsonObject
        val arguments = constructorPlan.getValue("arguments").jsonArray.joinToString(",") {
            format(decode(it.jsonObject))
        }
        val referenceId = "${reference.string("modulePath")}#${reference.string("exportName")}"
        return "materialize constructor $receiverPath $referenceId " +
            "args=[$arguments] -> object:${receiver.string("className")}"
    }

    private fun receiverLabel(value: ModelValue, encoded: JsonObject): String = when (value) {
        ModelUndefined -> "undefined"
        is ModelObject -> encoded["className"]?.jsonPrimitive?.content?.let { "object:$it" } ?: "object"
        else -> error("call receiver must be undefined or object")
    }

    private fun format(value: ModelValue): String = when (value) {
        ModelUndefined -> "undefined"
        ModelNull -> "null"
        is ModelNumber -> "number:${encodeNumber(value.value)}"
        is ModelBoolean -> "boolean:${value.value}"
        is ModelString -> "string:${JsonPrimitive(value.value)}"
        else -> error("${value::class.simpleName} cannot occur in a frozen trace argument/result")
    }

    private fun decodeNumber(value: String): Double = when (value) {
        "NaN" -> Double.NaN
        "Infinity" -> Double.POSITIVE_INFINITY
        "-Infinity" -> Double.NEGATIVE_INFINITY
        "-0" -> -0.0
        else -> value.toDouble()
    }

    private fun encodeNumber(value: Double): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        value == 0.0 && value.toRawBits() == (-0.0).toRawBits() -> "-0"
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun pointer(root: JsonElement, pointer: String): JsonElement {
        require(pointer.startsWith('/')) { "invalid JSON pointer $pointer" }
        return pointer.drop(1).split('/').fold(root) { current, rawToken ->
            val token = rawToken.replace("~1", "/").replace("~0", "~")
            when (current) {
                is JsonObject -> current.getValue(token)
                is JsonArray -> current[token.toInt()]
                else -> error("JSON pointer $pointer cannot traverse $token")
            }
        }
    }

    private fun callable(
        modulePath: String,
        exportName: String,
        callableKind: String,
        stableCallableId: String,
        arity: Int,
        invoke: (ModelValue, List<ModelValue>) -> ModelValue,
    ) = ModelCallable(referenceKey(modulePath, exportName, callableKind), stableCallableId, arity, invoke)

    private fun referenceKey(modulePath: String, exportName: String, callableKind: String): String =
        "$modulePath#$exportName#$callableKind"

    private fun factorial(value: Int): Long = if (value <= 1) 1 else value * factorial(value - 1)

    private fun formatJs(value: ModelValue): String = when (value) {
        ModelUndefined -> "undefined"
        is ModelNumber -> encodeNumber(value.value)
        is ModelString -> value.value
        is ModelBoolean -> value.value.toString()
        ModelNull -> "null"
        else -> error("unsupported JS string conversion in arity fixture")
    }

    private fun kindOnly(kind: String) = JsonObject(mapOf("kind" to JsonPrimitive(kind)))

    private fun kindAndValue(kind: String, value: String) = JsonObject(
        linkedMapOf("kind" to JsonPrimitive(kind), "value" to JsonPrimitive(value)),
    )
}

private fun ModelValue.objectField(name: String): ModelValue =
    requireNotNull((this as ModelObject).fields[name]) { "missing receiver field $name" }

private fun List<ModelValue>.numbers(): List<Double> = map { (it as ModelNumber).value }

private fun JsonObject.plan(): JsonObject = getValue("materialization").jsonObject

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.content.toInt()

private fun JsonObject.boolean(key: String): Boolean = getValue(key).jsonPrimitive.content.toBooleanStrict()
