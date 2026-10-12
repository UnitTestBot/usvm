package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.longEntry
import org.usvm.test.util.checkers.eq

class MapValueRegressionTest : GoNativeTestRunner() {
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
