package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class LocalClassExampleTest : JavaMethodTestRunner() {
    @Test
    fun testLocalClassFieldExample() {
        checkDiscoveredProperties(
            LocalClassExample::localClassFieldExample,
            eq(1),
            { _, y, r -> r == y + 42 }
        )
    }
}
