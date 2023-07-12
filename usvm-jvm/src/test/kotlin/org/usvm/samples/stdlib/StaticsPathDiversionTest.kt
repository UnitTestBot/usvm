package org.usvm.samples.stdlib

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge

import java.io.File

internal class StaticsPathDiversionTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testJavaIOFile() {
        // TODO Here we have a path diversion example - the static field `java.io.File#separator` is considered as not meaningful,
        //  so it is not passed to the concrete execution because of absence in the `stateBefore` models.
        //  So, the symbolic engine has 2 results - true and false, as expected, but the concrete executor may produce 1 or 2,
        //  depending on the model for the argument of the MUT produced by the solver.
        //  Such diversion was predicted to some extent - see `org.utbot.common.WorkaroundReason.IGNORE_STATICS_FROM_TRUSTED_LIBRARIES`
        //  and the corresponding issue https://github.com/UnitTestBot/UTBotJava/issues/716
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
