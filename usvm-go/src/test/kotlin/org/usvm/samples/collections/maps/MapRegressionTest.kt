package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMap
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.hasLongKey
import org.usvm.samples.longEntry
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class MapRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "mapDeleteLength", fixture = "regressions")
    fun mapDeleteLength() = checkNative(method = "mapDeleteLength")

    @Test
    @GoSample(method = "mapHintLength", fixture = "regressions")
    fun mapHintLength() = checkNative(method = "mapHintLength")

    @Test
    @GoSample(method = "mapInsertLength", fixture = "regressions")
    fun mapInsertLength() = checkNative(method = "mapInsertLength")

    @Test
    @GoSample(method = "mapOverwriteLength", fixture = "regressions")
    fun mapOverwriteLength() = checkNative(method = "mapOverwriteLength")

    @Test
    @GoSample(method = "nilMapLookup", fixture = "regressions")
    fun nilMapLookup() = checkNative(method = "nilMapLookup")

    @Test
    @GoSample(method = "nilMapLookupComma", fixture = "regressions")
    fun nilMapLookupComma() = checkNative(method = "nilMapLookupComma")

    @Test
    @GoSample(method = "nilMapDelete", fixture = "regressions")
    fun nilMapDelete() = checkNative(method = "nilMapDelete")

    @Test
    @GoSample(method = "nilMapAssignment", fixture = "regressions")
    fun nilMapAssignment() = checkNative(method = "nilMapAssignment")

    @Test
    @GoSample(method = "nilNamedMapLookup", fixture = "regressions")
    fun nilNamedMapLookup() = checkNative(method = "nilNamedMapLookup")

    @Test
    @GoSample(method = "nilNamedMapDelete", fixture = "regressions")
    fun nilNamedMapDelete() = checkNative(method = "nilNamedMapDelete")

    @Test
    @GoSample(method = "missingMapLookupCommaValue", fixture = "regressions")
    fun missingMapLookupCommaValue() = checkNative(method = "missingMapLookupCommaValue")

    @Test
    @GoSample(method = "nilMapRange", fixture = "regressions")
    fun nilMapRange() {
        // The loop body is unreachable for a nil map.
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkNative(method = "nilMapRange")
    }

    @Test
    @GoSample(method = "nilNamedMapRange", fixture = "regressions")
    fun nilNamedMapRange() {
        // The loop body is unreachable for a nil map.
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkNative(method = "nilNamedMapRange")
    }

    @Test
    @GoSample(method = "symbolicMapLookupComma", fixture = "regressions")
    fun symbolicMapLookupComma() {
        checkDiscoveredProperties(
            method = "symbolicMapLookupComma",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, _: Number, r: GoResult -> m == null && r.list == listOf(0L, false) },
            { m: GoMap, key: Number, r: GoResult ->
                m != null && !m.hasLongKey(key.toLong()) && r.list == listOf(0L, false)
            },
            { m: GoMap, key: Number, r: GoResult ->
                m.hasLongKey(key.toLong()) && r.list == listOf(m.longEntry(key.toLong()), true)
            },
        )
    }
}
