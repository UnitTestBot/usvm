package org.usvm.samples.invokes

import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.ast.AstExample
import org.usvm.samples.ast.Constant
import org.usvm.test.util.checkers.ge
import org.usvm.util.Options
import org.usvm.util.UsvmTest

class AstExampleTest : JavaMethodTestRunner() {
    @UsvmTest([Options(solverType = SolverType.Z3, strategies = [PathSelectionStrategy.FORK_DEPTH])])
    fun testSubstituteAndEvaluate(options: UMachineOptions) = withOptions(options) {
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