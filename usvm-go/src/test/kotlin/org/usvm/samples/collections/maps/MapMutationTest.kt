package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMap
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.hasLongKey
import org.usvm.samples.longEntry
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class MapMutationTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "mapUpdate")
    fun mapUpdate() {
        checkDiscoveredProperties(
            method = "mapUpdate",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, _: Number, _: Number, r: GoResult -> m == null && r.isPanic },
            { m: GoMap, key: Number, value: Number, r: GoResult ->
                m != null &&
                    r.long == maxOf(m.longEntry(key.toLong()), value.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "mapCustomUpdate")
    fun mapCustomUpdate() {
        checkDiscoveredProperties(
            method = "mapCustomUpdate",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, _: Number, _: Number, r: GoResult -> m == null && r.isPanic },
            { m: GoMap, key: Number, value: Number, r: GoResult ->
                m != null &&
                    r.long == maxOf(m.longEntry(key.toLong()), value.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "mapDeleteSimple")
    fun mapDeleteSimple() {
        checkDiscoveredProperties(
            method = "mapDeleteSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, _: Number, r: GoResult -> m.orEmpty().size < 5 && r.isPanic && r.panicValue == "too smol map" },
            { m: GoMap, key: Number, r: GoResult ->
                m.orEmpty().size >= 5 &&
                    !m.hasLongKey(key.toLong()) &&
                    r.isPanic &&
                    r.panicValue == "not found"
            },
            { m: GoMap, key: Number, r: GoResult ->
                m.orEmpty().size >= 5 &&
                    m.hasLongKey(key.toLong()) &&
                    r.long == m.orEmpty().size.toLong() - 1
            },
        )
    }

    @Test
    @GoSample(method = "mapCustomDeleteSimple")
    fun mapCustomDeleteSimple() {
        checkDiscoveredProperties(
            method = "mapCustomDeleteSimple",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, _: Number, r: GoResult -> m.orEmpty().size < 5 && r.isPanic && r.panicValue == "too smol map" },
            { m: GoMap, key: Number, r: GoResult ->
                m.orEmpty().size >= 5 &&
                    !m.hasLongKey(key.toLong()) &&
                    r.isPanic &&
                    r.panicValue == "not found"
            },
            { m: GoMap, key: Number, r: GoResult ->
                m.orEmpty().size >= 5 &&
                    m.hasLongKey(key.toLong()) &&
                    r.long == m.orEmpty().size.toLong() - 1
            },
        )
    }
}
