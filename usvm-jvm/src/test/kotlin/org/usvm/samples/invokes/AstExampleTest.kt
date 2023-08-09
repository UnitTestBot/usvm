package org.usvm.samples.invokes

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.ast.AstExample
import org.usvm.samples.ast.Constant
import org.usvm.test.util.checkers.ge

class AstExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Jacodb Method Builder issue with instanceOf")
    fun testSubstituteAndEvaluate() {
        checkDiscoveredPropertiesWithExceptions(
            AstExample::replaceLeafAndCheck,
            ge(4),
            { _, ast, r -> ast == null && r.exceptionOrNull() is NullPointerException },
            { _, ast, r -> r.getOrNull() == -1 && ast is Constant && AstExample().replaceLeafAndCheck(ast) == -1 },
            { _, ast, r -> r.getOrNull() == 0 && ast !is Constant && AstExample().replaceLeafAndCheck(ast) == 0 },
            { _, ast, r -> r.getOrNull() == 1 && AstExample().replaceLeafAndCheck(ast) == 1 },
        )
    }
}