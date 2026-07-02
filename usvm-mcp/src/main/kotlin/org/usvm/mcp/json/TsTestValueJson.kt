package org.usvm.mcp.json

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue

/**
 * Converts [TsTestValue] trees (produced by `TsTestResolver`) into JSON
 * suitable for an LLM: primitives map to JSON primitives, special JS values
 * are tagged objects, so nothing is ambiguous.
 */
fun TsTestValue.toJson(): JsonElement = when (this) {
    is TsTestValue.TsNull -> JsonNull
    is TsTestValue.TsUndefined -> tagged("undefined")
    is TsTestValue.TsAny -> tagged("any")
    is TsTestValue.TsUnknown -> tagged("unknown")
    is TsTestValue.TsBoolean -> JsonPrimitive(value)
    is TsTestValue.TsString -> JsonPrimitive(value)
    is TsTestValue.TsBigInt -> buildJsonObject {
        put(KIND, "bigint")
        put("value", value)
    }

    is TsTestValue.TsNumber.TsInteger -> JsonPrimitive(value)
    is TsTestValue.TsNumber.TsDouble -> doubleToJson(value)

    is TsTestValue.TsClass -> buildJsonObject {
        put(KIND, "object")
        put("class", name)
        put(
            "properties",
            buildJsonObject {
                properties.forEach { (key, propValue) -> put(key, propValue.toJson()) }
            },
        )
    }

    is TsTestValue.TsArray<*> -> buildJsonArray {
        values.forEach { add(it.toJson()) }
    }

    is TsTestValue.TsException -> toExceptionDto().let { dto ->
        buildJsonObject {
            put(KIND, "exception")
            put("type", dto.type)
            dto.message?.let { put("message", it) }
            dto.value?.let { put("value", it) }
        }
    }
}

fun TsTestValue.TsException.toExceptionDto(): ExceptionDto = when (this) {
    is TsTestValue.TsException.StringException -> ExceptionDto(type = "string", message = message)
    is TsTestValue.TsException.ObjectException -> ExceptionDto(type = "object", value = value.toJson())
    is TsTestValue.TsException.UnknownException -> ExceptionDto(type = "unknown")
}

/** Renders a resolved symbolic state as a test case DTO. */
fun TsTest.toTestCaseDto(): TestCaseDto {
    val result = returnValue
    return TestCaseDto(
        kind = if (result is TsTestValue.TsException) "EXCEPTION" else "SUCCESS",
        thisInstance = before.thisInstance?.toJson(),
        parameters = before.parameters.map { it.toJson() },
        returnValue = if (result is TsTestValue.TsException) null else result.toJson(),
        exception = (result as? TsTestValue.TsException)?.toExceptionDto(),
    )
}

fun unresolvedTestCase(error: String): TestCaseDto =
    TestCaseDto(kind = "UNRESOLVED", resolutionError = error)

private const val KIND = "\$kind"

private fun tagged(kind: String): JsonElement = buildJsonObject { put(KIND, kind) }

/**
 * JS numbers include NaN and Infinities, which are not representable in JSON.
 * They are encoded as tagged objects so the client can tell them apart from strings.
 */
private fun doubleToJson(value: Double): JsonElement =
    if (value.isFinite()) {
        JsonPrimitive(value)
    } else {
        buildJsonObject {
            put(KIND, "number")
            put("value", value.toString()) // "NaN", "Infinity", "-Infinity"
        }
    }
