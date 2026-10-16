package org.usvm.samples.reflection

import org.junit.jupiter.api.Test
import org.usvm.machine.JcMachine
import org.usvm.machine.state.JcMethodResult
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.getJcMethodByName
import kotlin.test.assertTrue

internal class ClassIsInstanceTest : JavaMethodTestRunner() {
    init {
        options = options.copy(throwExceptionOnStepFailure = true)
    }

    @Test
    fun `symbolic input Class metadata uses the normal fallback`() {
        val method = cp.getJcMethodByName(ClassIsInstance::inputClass).method

        val states = JcMachine(cp, options).use { machine -> machine.analyze(method) }

        assertTrue(states.any { it.methodResult is JcMethodResult.Success })
    }

    @Test
    fun `literal class accepts matching objects arrays and interface implementations`() {
        checkDiscoveredProperties(ClassIsInstance::matchingReference, eq(1), { _, result -> result == true })
        checkDiscoveredProperties(ClassIsInstance::matchingArray, eq(1), { _, result -> result == true })
        checkDiscoveredProperties(ClassIsInstance::matchingInterface, eq(1), { _, result -> result == true })
    }

    @Test
    fun `literal class rejects unrelated objects`() {
        checkDiscoveredProperties(ClassIsInstance::unrelatedReference, eq(1), { _, result -> result == false })
    }

    @Test
    fun `null object is not an instance of a reference class`() {
        checkDiscoveredProperties(ClassIsInstance::nullObject, eq(1), { _, result -> result == false })
    }

    @Test
    fun `primitive and void Class mirrors have no object instances`() {
        checkDiscoveredProperties(ClassIsInstance::primitiveClass, eq(1), { _, result -> result == false })
        checkDiscoveredProperties(ClassIsInstance::voidClass, eq(1), { _, result -> result == false })
    }
}
