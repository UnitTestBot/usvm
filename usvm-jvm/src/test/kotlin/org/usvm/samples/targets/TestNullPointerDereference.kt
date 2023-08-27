package org.usvm.samples.targets

import io.mockk.internalSubstitute
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcLocationTarget
import org.usvm.api.targets.JcNullPointerDereferenceTarget
import org.usvm.machine.JcMachine
import org.usvm.samples.JacoDBContainer
import org.usvm.samples.samplesKey
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestNullPointerDereference {

    @Test
    fun `Null Pointer Dereference with intermediate target`() {
        val cp = JacoDBContainer(samplesKey).cp
        val declaringClassName = requireNotNull(NullPointerDereference::twoPathsToNPE.javaMethod?.declaringClass?.name)
        val jcClass = cp.findClass(declaringClassName).toType()
        val jcMethod = jcClass.declaredMethods.first { it.name == NullPointerDereference::twoPathsToNPE.name }.method

        val target = JcLocationTarget(jcMethod, jcMethod.instList[5]).apply {
            addChild(JcNullPointerDereferenceTarget(jcMethod, jcMethod.instList[8]))
        }

        val states = JcMachine(cp, UMachineOptions()).use { machine ->
            machine.reproduceTargets(jcMethod, listOf(target))
        }
        assertEquals(1, states.size)
        assertNotNull(states.single().reversedPath.asSequence().singleOrNull { it ==  jcMethod.instList[8] })
        assertNotNull(states.single().reversedPath.asSequence().singleOrNull { it ==  jcMethod.instList[5] })
    }
}
