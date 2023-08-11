package org.usvm.samples.invokes

import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest
import kotlin.math.max

internal class StaticInvokeExampleTest : JavaMethodTestRunner() {
    @UsvmTest([Options([PathSelectionStrategy.BFS], coverageZone = CoverageZone.CLASS)])
    fun testMaxForThree(options: UMachineOptions) {
        withOptions(options) {
            val method = StaticInvokeExample::maxForThree
            checkDiscoveredProperties(
                method,
                ignoreNumberOfAnalysisResults,
                { x, y, _, _ -> x > y },
                { x, y, _, _ -> x <= y },
                { x, y, z, _ -> max(x, y.toInt()) > z },
                { x, y, z, _ -> max(x, y.toInt()) <= z },
            )
        }
    }
}
