package org.usvm.samples.strings

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcTypedMethod
import org.usvm.api.JcTest
import org.usvm.api.util.JcTestInterpreter
import org.usvm.machine.JcMachine
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.util.JcTestExecutor
import org.usvm.util.JcTestResolverType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringConcatSamplesTest : JavaMethodTestRunner() {
    @Test
    fun testStringConcatEq() {
        val method = findMethod("stringConcatEq")
        val states = executeMethod(method)
        val successStates = states.filter { it.result.isSuccess }
        assertEquals(1, successStates.size)
        assertTrue(successStates.single().result.getOrThrow() as Boolean)
    }

    @Test
    fun testStringConcatStrangeEq() {
        val method = findMethod("stringConcatStrangeEq")
        val states = executeMethod(method)
        val successStates = states.filter { it.result.isSuccess }
        assertEquals(1, successStates.size)
        assertTrue(successStates.single().result.getOrThrow() as Boolean)
    }

    private fun executeMethod(method: JcTypedMethod): List<JcTest> {
        val testResolver = when (resolverType) {
            JcTestResolverType.INTERPRETER -> JcTestInterpreter()
            JcTestResolverType.CONCRETE_EXECUTOR -> JcTestExecutor(classpath = cp)
        }

        return JcMachine(cp, options).use { machine ->
            val states = machine.analyze(method.method)
            states.map { testResolver.resolve(method, it) }
        }
    }

    private fun findMethod(methodName: String): JcTypedMethod =
        (cp.findTypeOrNull(SAMPLES_CLASS) as? JcClassType)
            ?.declaredMethods?.singleOrNull { it.name == methodName }
            ?: error("Cannot find method $methodName in $SAMPLES_CLASS")

    companion object {
        const val SAMPLES_CLASS = "org.usvm.samples.strings.StringConcatSamples"
    }
}
