package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longEntry
import org.usvm.test.util.checkers.eq

class MapValueRegressionTest : GoNativeTestRunner() {
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
