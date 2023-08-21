package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

internal class PrivateFieldsTest : JavaMethodTestRunner() {
    @Test
    fun testAccessWithGetter() {
        checkDiscoveredPropertiesWithExceptions(
            PrivateFields::accessWithGetter,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x == null && r.isException<NullPointerException>() },
            { _, x, r -> x.a == 1 && r.getOrNull() == true },
            { _, x, r -> x.a != 1 && r.getOrNull() == false },
        )
    }
}