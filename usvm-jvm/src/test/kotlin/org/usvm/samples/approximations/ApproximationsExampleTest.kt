package org.usvm.samples.approximations

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest
import org.usvm.util.isException

class ApproximationsExampleTest : ApproximationsTestRunner() {

    @UsvmTest([Options([PathSelectionStrategy.DFS])])
    fun testArrayListModification(options: UMachineOptions) = withOptions(options) {
        with(FixedExecutionVerifier(5, exceptionalExecutions = setOf(0))) {
            checkDiscoveredPropertiesWithExceptions(
                ApproximationsExample::modifyList,
                ignoreNumberOfAnalysisResults,
                { _, o, r -> o == 0 && onExecution(0, r.isException<IndexOutOfBoundsException>()) },
                { _, o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testOptionalDouble() {
        with(FixedExecutionVerifier(3)) {
            checkDiscoveredPropertiesWithExceptions(
                ApproximationsExample::testOptionalDouble,
                ignoreNumberOfAnalysisResults,
                { _, o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }
}
