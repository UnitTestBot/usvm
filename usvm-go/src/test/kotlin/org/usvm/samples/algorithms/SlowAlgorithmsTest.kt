package org.usvm.samples.algorithms

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoSlice
import org.usvm.samples.matchesRooms
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class SlowAlgorithmsTest : GoMethodTestRunner() {
    @Tag(value = "manual")
    @Test
    @GoSample(method = "canVisitAllRooms")
    fun canVisitAllRooms() {
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkDiscoveredProperties(
            method = "canVisitAllRooms",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { rooms: GoSlice, r: GoResult -> matchesRooms(rooms, r) },
        )
    }
}
