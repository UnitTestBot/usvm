package org.usvm

import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.usvm.api.targets.CrashReproductionExceptionTarget
import org.usvm.api.targets.CrashReproductionLocationTarget
import org.usvm.api.targets.reproduceCrash
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.TraceSample
import kotlin.test.Test
import kotlin.test.assertEquals

class CrashReproductionTest : JavaMethodTestRunner() {
    @Test
    fun testSimple() {
        val cls = cp.findClass<TraceSample>()
        val cls2 = cp.findClass<TraceSample.TraceSampleImpl>()
        val exception = cp.findClass<IllegalStateException>()
        val entrypointMethod = cls.methods.single { it.name == "entryPoint" }
        val m1Method = cls2.methods.single { it.name == "method1" }

        val initialTarget = CrashReproductionLocationTarget(entrypointMethod.instList.first())
        val otherTarget = CrashReproductionLocationTarget(m1Method.instList.first()).also { initialTarget.addChild(it) }
        CrashReproductionExceptionTarget(exception).also { otherTarget.addChild(it) }

        val states = reproduceCrash(cp, initialTarget)

        assertEquals(1, states.size)
    }
}
