package org.usvm.samples.statics

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class StaticsTypesTest : JavaMethodTestRunner() {
    @Test
    fun test() {
        checkDiscoveredPropertiesWithExceptions(
            StaticsTypesExample::virtualInvokeOnInputFieldArrayReading,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.isSuccess },
        )
    }
}
