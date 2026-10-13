package org.usvm.samples.collections.maps

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.usvm.generatedGoFile
import org.usvm.interpreter.GoMapEntries
import org.usvm.interpreter.GoPointer
import org.usvm.samples.GoExecution
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longEntry
import org.usvm.samples.replayWithNativeGo
import org.usvm.test.util.checkers.eq
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MapValueRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "mapKeyHashAfterValueEvaluation", fixture = "regressions")
    fun mapKeyHashAfterValueEvaluation() {
        // The unhashable-key panic makes the normal assignment tail unreachable.
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)
        checkNative(method = "mapKeyHashAfterValueEvaluation")
    }

    @Test
    @GoSample(method = "markMapKeyValue", fixture = "regressions")
    fun markMapKeyValue() = checkParameterMutations(
        method = "markMapKeyValue",
        { execution ->
            val before = execution.arguments.single() as? GoPointer
            val after = execution.argumentsAfter.single() as? GoPointer
            if (before == null) {
                execution.result.isPanic && after == null
            } else {
                execution.result.long == 1L && (after?.value as? Number)?.toLong() == 7L
            }
        },
    )

    @Test
    @GoSample(method = "recoverMapKeyAssignment", fixture = "regressions")
    fun recoverMapKeyAssignment() {
        // Outside a deferred panic, recover is nil; the assignment is covered through its caller.
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)
        checkParameterMutations(
            method = "recoverMapKeyAssignment",
            { execution -> execution.result.isSuccess && execution.arguments == execution.argumentsAfter },
        )
    }

    @Test
    @GoSample(method = "mapStructNaNUncomparablePanics", fixture = "regressions")
    fun mapStructNaNUncomparablePanics() = checkNative(method = "mapStructNaNUncomparablePanics")

    @Test
    @GoSample(method = "inputStringMapSymbolicKeyAndExtra", fixture = "regressions")
    fun inputStringMapSymbolicKeyAndExtra() = replayMapInputs(
        method = "inputStringMapSymbolicKeyAndExtra", observedKey = "", needsAdditionalEntry = true,
    )

    @Test
    @GoSample(method = "inputFloatMapCanonicalGhosts", fixture = "regressions")
    fun inputFloatMapCanonicalGhosts() = replayMapInputs(
        method = "inputFloatMapCanonicalGhosts", observedKey = 0.0, needsAdditionalEntry = true,
    )

    @Test
    @GoSample(method = "floatMapZeroRangeAfterOverwrite", fixture = "regressions")
    fun floatMapZeroRangeAfterOverwrite() = checkNative(method = "floatMapZeroRangeAfterOverwrite")

    @Test
    @GoSample(method = "inputFloatMapZeroRangeSign", fixture = "regressions")
    fun inputFloatMapZeroRangeSign() = replayMapInputs(
        method = "inputFloatMapZeroRangeSign", observedKey = 0.0, expectedOutcomes = setOf(-1L, 0L, 1L),
    )

    @Test
    @GoSample(method = "pointerMapConvertedAlias", fixture = "regressions")
    fun pointerMapConvertedAlias() = checkNative(method = "pointerMapConvertedAlias")

    @Test
    @GoSample(method = "negativeMapZero", fixture = "regressions")
    fun negativeMapZero() = checkDiscoveredProperties<Double>(
        method = "negativeMapZero",
        analysisResultsMatchers = arrayOf({ value: Double, result: GoResult ->
            val actual = result.value as? Double
            result.isSuccess && actual != null &&
                (value.isNaN() && actual.isNaN() || actual.toRawBits() == (-value).toRawBits())
        }),
    )

    @Test
    @GoSample(method = "mapNaN", fixture = "regressions")
    fun mapNaN() = checkDiscoveredProperties<Double>(
        method = "mapNaN",
        analysisResultsMatchers = arrayOf({ value: Double, result: GoResult ->
            val expected = value / value
            val actual = result.value as? Double
            result.isSuccess && actual != null && (actual == expected || actual.isNaN() && expected.isNaN())
        }),
    )

    @Test
    @GoSample(method = "float32MapZeroAndNaN", fixture = "regressions")
    fun float32MapZeroAndNaN() = checkNative(method = "float32MapZeroAndNaN")

    @Test
    @GoSample(method = "interfaceMapUncomparableLookup", fixture = "regressions")
    fun interfaceMapUncomparableLookup() = checkNative(method = "interfaceMapUncomparableLookup")

    @Test
    @GoSample(method = "interfaceMapUncomparableUpdate", fixture = "regressions")
    fun interfaceMapUncomparableUpdate() = checkNative(method = "interfaceMapUncomparableUpdate")

    @Test
    @GoSample(method = "inputFloatMapLookup", fixture = "regressions")
    fun inputFloatMapLookup() = replayMapInputs(method = "inputFloatMapLookup", observedKey = 0.0)

    @Test
    @GoSample(method = "inputStringMapLookup", fixture = "regressions")
    fun inputStringMapLookup() = replayMapInputs(method = "inputStringMapLookup", observedKey = "same")

    @Test
    @GoSample(method = "inputFloatMapExtraEntry", fixture = "regressions")
    fun inputFloatMapExtraEntry() = replayMapInputs(
        method = "inputFloatMapExtraEntry", observedKey = 0.0, needsAdditionalEntry = true,
    )

    @Test
    @GoSample(method = "inputStringMapExtraEntry", fixture = "regressions")
    fun inputStringMapExtraEntry() = replayMapInputs(
        method = "inputStringMapExtraEntry", observedKey = "same", needsAdditionalEntry = true,
    )

    @Test
    @GoSample(method = "floatMapNaNResult", fixture = "regressions")
    fun floatMapNaNResult() {
        val executions = runner("floatMapNaNResult", options)
        assertEquals(expected = 1, actual = executions.size)
        val entries = executions.single().result.value as GoMapEntries
        assertEquals(expected = 2, actual = entries.size)
        assertTrue(entries.entries.all { (it.key as Double).isNaN() })
        assertEquals(setOf(7L, 9L), entries.entries.map { (it.value as Number).toLong() }.toSet())
        assertEquals(expected = 2, actual = entries.entries.map { it.identity }.toSet().size)

        replayMapOutputs(method = "floatMapNaNResult", executions = executions, expectedSummary = 216L)
    }

    @Test
    @GoSample(method = "pointerMapKeyResult", fixture = "regressions")
    fun pointerMapKeyResult() {
        val executions = runner("pointerMapKeyResult", options)
        assertEquals(expected = 1, actual = executions.size)
        val entries = executions.single().result.value as GoMapEntries
        assertEquals(expected = 2, actual = entries.size)
        assertTrue(entries.entries.all { ((it.key as GoPointer).value as Number).toLong() == 0L })
        assertEquals(setOf(7L, 9L), entries.entries.map { (it.value as Number).toLong() }.toSet())
        assertEquals(expected = 2, actual = entries.entries.map { it.identity }.toSet().size)

        replayMapOutputs(method = "pointerMapKeyResult", executions = executions, expectedSummary = 216L)
    }

    @Test
    @GoSample(method = "namedStringMapResult", fixture = "regressions")
    fun namedStringMapResult() {
        val executions = runner("namedStringMapResult", options)
        assertEquals(expected = 1, actual = executions.size)
        assertEquals(mapOf("same" to 9L), executions.single().result.map)

        replayMapOutputs(method = "namedStringMapResult", executions = executions, expectedSummary = 109L)
    }

    @Test
    @GoSample(method = "namedArrayMapResult", fixture = "regressions")
    fun namedArrayMapResult() {
        val executions = runner("namedArrayMapResult", options)
        assertEquals(expected = 1, actual = executions.size)
        assertEquals(mapOf(listOf(1L, 2L) to 7L), executions.single().result.map)

        replayMapOutputs(method = "namedArrayMapResult", executions = executions, expectedSummary = 119L)
    }

    private fun replayMapInputs(
        method: String,
        observedKey: Any,
        needsAdditionalEntry: Boolean = false,
        expectedOutcomes: Set<Long>? = null,
    ) {
        val executions = runner(method, options)
        assertTrue(executions.isNotEmpty())
        assertTrue(executions.all { it.result.isSuccess })
        if (expectedOutcomes != null) assertEquals(expectedOutcomes, executions.map { it.result.long }.toSet())
        if (needsAdditionalEntry) {
            val hasAdditionalEntry = executions.any {
                val input = mapReplayEntries(it.arguments.first())
                val actualObservedKey = it.arguments.getOrNull(1) ?: observedKey
                val observed = input?.firstOrNull { entry ->
                    val numericKey = entry.first as? Double
                    val numericObservedKey = actualObservedKey as? Double
                    if (numericKey != null && numericObservedKey != null) {
                        numericKey == numericObservedKey
                    } else {
                        entry.first == actualObservedKey
                    }
                }
                input?.size == 2 && observed != null &&
                    it.result.long == (observed.second as Number).toLong()
            }
            assertTrue(hasAdditionalEntry, message = "A witness must retain an observed key and an unobserved entry")
        }

        val inputs = buildJsonArray {
            executions.forEach { execution ->
                val input = mapReplayEntries(execution.arguments.first())
                add(buildJsonObject {
                    put("nil", input == null)
                    when (val query = execution.arguments.getOrNull(1)) {
                        is String -> put("lookupKey", query)
                        is Double -> put("lookupFloatBits", query.toRawBits().toULong().toString(radix = 16))
                    }
                    put("entries", buildJsonArray {
                        input.orEmpty().forEach { (key, value) ->
                            add(buildJsonObject {
                                when (key) {
                                    is Double -> put("floatKeyBits", key.toRawBits().toULong().toString(radix = 16))
                                    is String -> put("stringKey", key)
                                    else -> error("Unsupported map replay key $key")
                                }
                                put("value", (value as Number).toLong())
                            })
                        }
                    })
                })
            }
        }
        val outputs = replayMapWitnesses(method = method, inputs = inputs)
        assertEquals(executions.map { it.result.long }, outputs)
    }

    private fun mapReplayEntries(value: Any?): List<Pair<Any?, Any?>>? = when (value) {
        null -> null
        is GoMapEntries -> value.entries.map { it.key to it.value }
        is Map<*, *> -> value.entries.map { it.key to it.value }
        else -> error("Unsupported map witness $value")
    }

    private fun replayMapOutputs(method: String, executions: List<GoExecution>, expectedSummary: Long) {
        assertTrue(executions.single().result.isSuccess)
        val inputs = buildJsonArray { add(buildJsonObject { put("nil", true) }) }
        assertEquals(listOf(expectedSummary), replayMapWitnesses(method = method, inputs = inputs))
    }

    private fun replayMapWitnesses(method: String, inputs: JsonArray): List<Long> {
        val request = buildJsonObject {
            put("method", method)
            put("inputs", inputs)
        }
        return replayWithNativeGo(
            executable = generatedGoFile("native-replay.test"),
            testName = "TestReplayMapKeys",
            request = request,
            timeout = 5.seconds,
        ).jsonArray.map { it.jsonPrimitive.long }
    }

    @Test
    @GoSample(method = "floatMapZeroLookup", fixture = "regressions")
    fun floatMapZeroLookup() = checkNative(method = "floatMapZeroLookup")

    @Test
    @GoSample(method = "floatMapZeroOverwrite", fixture = "regressions")
    fun floatMapZeroOverwrite() = checkNative(method = "floatMapZeroOverwrite")

    @Test
    @GoSample(method = "floatMapZeroDelete", fixture = "regressions")
    fun floatMapZeroDelete() = checkNative(method = "floatMapZeroDelete")

    @Test
    @GoSample(method = "namedFloatMapZeroLookup", fixture = "regressions")
    fun namedFloatMapZeroLookup() = checkNative(method = "namedFloatMapZeroLookup")

    @Test
    @GoSample(method = "floatMapNaNLookup", fixture = "regressions")
    fun floatMapNaNLookup() = checkNative(method = "floatMapNaNLookup")

    @Test
    @GoSample(method = "floatMapNaNRange", fixture = "regressions")
    fun floatMapNaNRange() = checkNative(method = "floatMapNaNRange")

    @Test
    @GoSample(method = "stringMapValueLookup", fixture = "regressions")
    fun stringMapValueLookup() = checkNative(method = "stringMapValueLookup")

    @Test
    @GoSample(method = "namedStringMapValueLookup", fixture = "regressions")
    fun namedStringMapValueLookup() = checkNative(method = "namedStringMapValueLookup")

    @Test
    @GoSample(method = "arrayMapKeySnapshot", fixture = "regressions")
    fun arrayMapKeySnapshot() = checkNative(method = "arrayMapKeySnapshot")

    @Test
    @GoSample(method = "structMapValueLookup", fixture = "regressions")
    fun structMapValueLookup() = checkNative(method = "structMapValueLookup")

    @Test
    @GoSample(method = "arrayMapRangeKeyCopy", fixture = "regressions")
    fun arrayMapRangeKeyCopy() = checkNative(method = "arrayMapRangeKeyCopy")

    @Test
    @GoSample(method = "interfaceMapValueLookup", fixture = "regressions")
    fun interfaceMapValueLookup() = checkNative(method = "interfaceMapValueLookup")

    @Test
    @GoSample(method = "namedScalarMapLookup", fixture = "regressions")
    fun namedScalarMapLookup() = checkNative(method = "namedScalarMapLookup")

    @Test
    @GoSample(method = "namedScalarMapOverwrite", fixture = "regressions")
    fun namedScalarMapOverwrite() = checkNative(method = "namedScalarMapOverwrite")

    @Test
    @GoSample(method = "namedScalarMapDelete", fixture = "regressions")
    fun namedScalarMapDelete() = checkNative(method = "namedScalarMapDelete")

    @Test
    @GoSample(method = "namedScalarMapRange", fixture = "regressions")
    fun namedScalarMapRange() = checkNative(method = "namedScalarMapRange")

    @Test
    @GoSample(method = "namedBooleanMapKeys", fixture = "regressions")
    fun namedBooleanMapKeys() = checkNative(method = "namedBooleanMapKeys")

    @Test
    @GoSample(method = "namedScalarMapResult", fixture = "regressions")
    fun namedScalarMapResult() {
        checkMatches(
            method = "namedScalarMapResult",
            analysisResultsNumberMatcher = eq(count = 1),
            { result: GoResult -> result.map?.size == 1 && result.map.longEntry(key = 1) == 7L },
        )
    }

    @Test
    @GoSample(method = "namedUnsignedMapResult", fixture = "regressions")
    fun namedUnsignedMapResult() {
        checkMatches(
            method = "namedUnsignedMapResult",
            analysisResultsNumberMatcher = eq(count = 1),
            { result: GoResult -> result.map == mapOf(ULong.MAX_VALUE to 7L) },
        )
    }

    @Test
    @GoSample(method = "mapDeletedDuringRange", fixture = "regressions")
    fun mapDeletedDuringRange() = checkNative(method = "mapDeletedDuringRange")

    @Test
    @GoSample(method = "mapRangeCurrentValues", fixture = "regressions")
    fun mapRangeCurrentValues() = checkNative(method = "mapRangeCurrentValues")

    @Test
    @GoSample(method = "mapDeletedReferenceKeysDuringRange", fixture = "regressions")
    fun mapDeletedReferenceKeysDuringRange() = checkNative(method = "mapDeletedReferenceKeysDuringRange")

    @Test
    @GoSample(method = "mapStructCopy", fixture = "regressions")
    fun mapStructCopy() = checkNative(method = "mapStructCopy")

    @Test
    @GoSample(method = "mapArrayCopy", fixture = "regressions")
    fun mapArrayCopy() = checkNative(method = "mapArrayCopy")

    @Test
    @GoSample(method = "mapLookupStructCopy", fixture = "regressions")
    fun mapLookupStructCopy() = checkNative(method = "mapLookupStructCopy")

    @Test
    @GoSample(method = "missingStructLookup", fixture = "regressions")
    fun missingStructLookup() = checkNative(method = "missingStructLookup")

    @Test
    @GoSample(method = "missingNamedLookup", fixture = "regressions")
    fun missingNamedLookup() = checkNative(method = "missingNamedLookup")

    @Test
    @GoSample(method = "missingArrayLookup", fixture = "regressions")
    fun missingArrayLookup() = checkNative(method = "missingArrayLookup")

    @Test
    @GoSample(method = "missingStructLookupComma", fixture = "regressions")
    fun missingStructLookupComma() = checkNative(method = "missingStructLookupComma")

    @Test
    @GoSample(method = "namedMapAssertionZero", fixture = "regressions")
    fun namedMapAssertionZero() = checkNative(method = "namedMapAssertionZero")
}
