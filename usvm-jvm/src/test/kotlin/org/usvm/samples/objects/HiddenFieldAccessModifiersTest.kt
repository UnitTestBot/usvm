package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class HiddenFieldAccessModifiersTest : JavaMethodTestRunner() {
    @Test
    fun testCheckSuperFieldEqualsOne() {
        checkDiscoveredProperties(
            HiddenFieldAccessModifiersExample::checkSuperFieldEqualsOne,
            ignoreNumberOfAnalysisResults,
            { _, o, _ -> o == null },
            { _, _, r -> r != null && r },
            { _, _, r -> r != null && !r },
        )
    }
}