package org.usvm.samples.collections.maps

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMap
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.hasLongKey
import org.usvm.samples.longEntry
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class MapLookupTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "mapLookup")
    fun mapLookup() {
        checkDiscoveredProperties(
            method = "mapLookup",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, key: Number, r: GoResult -> m.longEntry(key.toLong()) == 123L && r.long == -1L },
            { m: GoMap, key: Number, r: GoResult ->
                m.longEntry(key.toLong()) != 123L &&
                    r.long == m.longEntry(key.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "mapCustomLookup")
    fun mapCustomLookup() {
        checkDiscoveredProperties(
            method = "mapCustomLookup",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, key: Number, r: GoResult -> m.longEntry(key.toLong()) == 123L && r.long == -1L },
            { m: GoMap, key: Number, r: GoResult ->
                m.longEntry(key.toLong()) != 123L &&
                    r.long == m.longEntry(key.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "mapLookupComma")
    fun mapLookupComma() {
        checkDiscoveredProperties(
            method = "mapLookupComma",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, key: Number, r: GoResult -> m.longEntry(key.toLong()) == 123L && r.long == -1L },
            { m: GoMap, key: Number, r: GoResult ->
                m.longEntry(key.toLong()) != 123L &&
                    r.long == m.longEntry(key.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "mapCustomLookupComma")
    fun mapCustomLookupComma() {
        checkDiscoveredProperties(
            method = "mapCustomLookupComma",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, key: Number, r: GoResult -> m.longEntry(key.toLong()) == 123L && r.long == -1L },
            { m: GoMap, key: Number, r: GoResult ->
                m.longEntry(key.toLong()) != 123L &&
                    r.long == m.longEntry(key.toLong())
            },
        )
    }

    @Test
    @GoSample(method = "mapLookupCommaReturn")
    fun mapLookupCommaReturn() {
        checkDiscoveredProperties(
            method = "mapLookupCommaReturn",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, key: Number, r: GoResult -> !m.hasLongKey(key.toLong()) && r.list == listOf(-1L, false) },
            { m: GoMap, key: Number, r: GoResult ->
                m.hasLongKey(key.toLong()) &&
                    r.list == listOf(m.longEntry(key.toLong()), true)
            },
        )
    }

    @Test
    @GoSample(method = "mapCustomLookupCommaReturn")
    fun mapCustomLookupCommaReturn() {
        checkDiscoveredProperties(
            method = "mapCustomLookupCommaReturn",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { m: GoMap, key: Number, r: GoResult -> !m.hasLongKey(key.toLong()) && r.list == listOf(-1L, false) },
            { m: GoMap, key: Number, r: GoResult ->
                m.hasLongKey(key.toLong()) &&
                    r.list == listOf(m.longEntry(key.toLong()), true)
            },
        )
    }
}
