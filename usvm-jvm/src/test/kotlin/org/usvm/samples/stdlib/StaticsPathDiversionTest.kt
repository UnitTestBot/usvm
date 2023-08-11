package org.usvm.samples.stdlib

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge
import org.usvm.util.disableTest
import java.io.File

internal class StaticsPathDiversionTest : JavaMethodTestRunner() {
    @Test
    fun testJavaIOFile() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            StaticsPathDiversion::separatorEquality,
            ge(2), // We cannot guarantee the exact number of branches without minimization

            // In the matchers below we check that the symbolic does not change the static field `File.separator` - we should
            // change the parameter, not the static field
            { _, s, separator -> separator == File.separator && s == separator },
            { _, s, separator -> separator == File.separator && s != separator },
        )
    }
}
