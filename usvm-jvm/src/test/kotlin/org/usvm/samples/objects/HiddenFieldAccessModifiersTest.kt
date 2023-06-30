package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class HiddenFieldAccessModifiersTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
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