package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class SimpleClassMultiInstanceExampleTest : JavaMethodTestRunner() {
    @Test
    fun singleObjectChangeTest() {
        checkDiscoveredProperties(
            SimpleClassMultiInstanceExample::singleObjectChange,
            ignoreNumberOfAnalysisResults,
            { _, first, _, _ -> first == null }, // NPE
            { _, first, _, r -> first.a < 5 && r == 3 },
            { _, first, _, r -> first.a >= 5 && r == first.b },
        )
    }
}