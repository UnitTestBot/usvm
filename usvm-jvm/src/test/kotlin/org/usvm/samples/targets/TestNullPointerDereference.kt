package org.usvm.samples.targets

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcExitTarget
import org.usvm.api.targets.JcLocationTarget
import org.usvm.api.targets.JcNullPointerDereferenceTarget
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.getJcMethod

class TestNullPointerDereference : JavaMethodTestRunner() {

    @Test
    fun `Null Pointer Dereference with intermediate target`() {
        val jcMethod = cp.getJcMethod(NullPointerDereference::twoPathsToNPE)

        val target = JcLocationTarget(jcMethod, jcMethod.instList[5]).apply {
            addChild(JcNullPointerDereferenceTarget(jcMethod, jcMethod.instList[8])).apply {
                addChild(JcExitTarget())
            }
        }

        withOptions(
            UMachineOptions(
                pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
                stopOnTargetsReached = true
            )
        ) {
            withTargets(listOf(target)) {
                // In fact, checking that the first state which was reported has reached the target
                checkDiscoveredPropertiesWithExceptions(
                    NullPointerDereference::twoPathsToNPE,
                    eq(1),
                    { _, n, r -> n <= 100 && r.exceptionOrNull() is NullPointerException }
                )
            }
        }
    }
}
