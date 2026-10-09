package org.usvm.samples.algorithms

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoSlice
import org.usvm.samples.longValues
import org.usvm.samples.matchesNearbyDuplicate
import org.usvm.samples.validTwoSum
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class AlgorithmsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "twoSum")
    fun twoSum() {
        checkDiscoveredProperties(
            method = "twoSum",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { nums: GoSlice, target: Number, r: GoResult -> validTwoSum(nums.longValues(), target.toLong(), r) },
        )
    }

    @Test
    @GoSample(method = "containsNearbyDuplicate")
    fun containsNearbyDuplicate() {
        checkDiscoveredProperties(
            method = "containsNearbyDuplicate",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { nums: GoSlice, k: Number, r: GoResult -> matchesNearbyDuplicate(nums.longValues(), k.toLong(), r) },
        )
    }
}
