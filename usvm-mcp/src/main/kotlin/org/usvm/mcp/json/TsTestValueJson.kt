package org.usvm.mcp.json

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.usvm.api.TsParametersState
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.mcp.exec.ResolvedCase
import java.util.IdentityHashMap

/** Converts a value graph to JSON, annotating only values that need identity preservation. */
fun TsTestValue.toJson(): JsonElement = WitnessJsonEncoder(listOf(this)).encode(this)

fun TsTestValue.TsException.toExceptionDto(): ExceptionDto = WitnessJsonEncoder(listOf(this)).encodeException(this)

/** Renders one case independently, so an unsupported graph does not discard sibling witnesses. */
fun ResolvedCase.toTestCaseDto(): TestCaseDto {
    val resolvedTest = test
    if (resolvedTest != null) return resolvedTest.toTestCaseDto()

    val resolvedInputs = inputs
    if (resolvedInputs != null) return encodeWitness { resolvedInputs.toInputsDto() }

    return unresolvedTestCase(error ?: "unknown resolution error")
}

/** Before and return values describe different snapshots and have independent identity contexts. */
fun TsTest.toTestCaseDto(): TestCaseDto = encodeWitness {
    val inputs = before.toInputsDto()
    val result = returnValue
    val encoder = WitnessJsonEncoder(listOf(result))

    inputs.copy(
        kind = if (result is TsTestValue.TsException) "EXCEPTION" else "SUCCESS",
        returnValue = if (result is TsTestValue.TsException) null else encoder.encode(result),
        exception = (result as? TsTestValue.TsException)?.let(encoder::encodeException),
    )
}

fun unresolvedTestCase(error: String): TestCaseDto =
    TestCaseDto(kind = "UNRESOLVED", resolutionError = error)

private fun TsParametersState.toInputsDto(): TestCaseDto {
    val roots = listOfNotNull(thisInstance) + parameters
    val encoder = WitnessJsonEncoder(roots)
    return TestCaseDto(
        kind = "INPUTS",
        thisInstance = thisInstance?.let(encoder::encode),
        parameters = parameters.map(encoder::encode),
    )
}

private inline fun encodeWitness(block: () -> TestCaseDto): TestCaseDto =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        unresolvedTestCase("${e::class.simpleName}: ${e.message}")
    } catch (e: StackOverflowError) {
        unresolvedTestCase("Witness graph is too deep to encode: ${e::class.simpleName}")
    }

private class WitnessJsonEncoder(roots: List<TsTestValue>) {
    private val occurrences = IdentityHashMap<TsTestValue, Int>()
    private val referenceIds = IdentityHashMap<TsTestValue, Int>()

    init {
        roots.forEach(::countReferences)
    }

    private fun countReferences(value: TsTestValue) {
        val isReference = value is TsTestValue.TsClass || value is TsTestValue.TsArray<*>
        if (isReference) {
            val previousCount = occurrences[value] ?: 0
            occurrences[value] = previousCount + 1
            if (previousCount > 0) return
        }

        when (value) {
            is TsTestValue.TsClass -> value.properties.values.forEach(::countReferences)
            is TsTestValue.TsArray<*> -> value.values.forEach(::countReferences)
            is TsTestValue.TsException.ObjectException -> countReferences(value.value)
            else -> Unit
        }
    }

    fun encode(value: TsTestValue): JsonElement {
        val existingId = referenceIds[value]
        if (existingId != null) {
            return buildJsonObject { put("\$ref", existingId) }
        }

        val needsIdentity = (occurrences[value] ?: 0) > 1
        val id = if (needsIdentity) {
            (referenceIds.size + 1).also { referenceIds[value] = it }
        } else {
            null
        }

        return when (value) {
            is TsTestValue.TsNull -> JsonNull
            is TsTestValue.TsUndefined -> tagged("undefined")
            is TsTestValue.TsAny -> tagged("any")
            is TsTestValue.TsUnknown -> tagged("unknown")
            is TsTestValue.TsBoolean -> JsonPrimitive(value.value)
            is TsTestValue.TsString -> JsonPrimitive(value.value)
            is TsTestValue.TsBigInt -> buildJsonObject {
                put(KIND, "bigint")
                put("value", value.value)
            }

            is TsTestValue.TsNumber.TsInteger -> JsonPrimitive(value.value)
            is TsTestValue.TsNumber.TsDouble -> doubleToJson(value.value)
            is TsTestValue.TsClass -> buildJsonObject {
                put(KIND, "object")
                put("class", value.name)
                id?.let { put("\$id", it) }
                put(
                    "properties",
                    buildJsonObject {
                        value.properties.forEach { (key, property) -> put(key, encode(property)) }
                    },
                )
            }

            is TsTestValue.TsArray<*> -> encodeArray(value, id)

            is TsTestValue.TsException -> encodeException(value).let { dto ->
                buildJsonObject {
                    put(KIND, "exception")
                    put("type", dto.type)
                    dto.message?.let { put("message", it) }
                    dto.value?.let { put("value", it) }
                }
            }
        }
    }

    private fun encodeArray(value: TsTestValue.TsArray<*>, id: Int?): JsonElement {
        val elements = buildJsonArray { value.values.forEach { add(encode(it)) } }
        if (id == null) return elements

        return buildJsonObject {
            put(KIND, "array")
            put("\$id", id)
            put("values", elements)
        }
    }

    fun encodeException(value: TsTestValue.TsException): ExceptionDto = when (value) {
        is TsTestValue.TsException.StringException -> ExceptionDto(type = "string", message = value.message)
        is TsTestValue.TsException.ObjectException -> ExceptionDto(type = "object", value = encode(value.value))
        is TsTestValue.TsException.UnknownException -> ExceptionDto(type = "unknown")
    }
}

private const val KIND = "\$kind"

private fun tagged(kind: String): JsonElement = buildJsonObject { put(KIND, kind) }

/** JS numbers include NaN and infinities, which require tags in JSON. */
private fun doubleToJson(value: Double): JsonElement =
    if (value.isFinite()) {
        JsonPrimitive(value)
    } else {
        buildJsonObject {
            put(KIND, "number")
            put("value", value.toString())
        }
    }
