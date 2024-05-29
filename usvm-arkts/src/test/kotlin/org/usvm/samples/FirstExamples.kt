package org.usvm.samples

import org.junit.jupiter.api.Test

// TODO rename it
class FirstExamplesTest : PandaMethodTestRunner() {
    @Test
    fun testDataFlowSecurity() {
        discoverProperties<Any, Any>(
            methodIdentifier = MethodDescriptor(
                className = "DataFlowSecurity",
                methodName = "validate",
                argumentsNumber = 1
            ),
            { arg, result -> TODO() }
        )
    }

    @Test
    fun testBadDataFlowSecurity() {
        discoverProperties<Any>(
            methodIdentifier = MethodDescriptor(
                className = "DataFlowSecurity",
                methodName = "bad",
                argumentsNumber = 0
            ),
            { result -> TODO() }
        )
    }

    @Test
    fun testBasicSamples() {
        discoverProperties<Double>(
            methodIdentifier = MethodDescriptor(
                className = "BasicSamples",
                methodName = "add",
                argumentsNumber = 0
            ),
            { result -> result == 4.0 }
        )
    }

    @Test
    fun testSomeOps() {
        discoverProperties<Double, Double, Double>(
            methodIdentifier = MethodDescriptor(
                className = "BasicSamples",
                methodName = "someOps",
                argumentsNumber = 2
            ),
            { a, b, result -> result == (a + b) * a - 1 }
        )
    }

    @Test
    fun testBasicIf() {
        discoverProperties<Double, Boolean>(
            methodIdentifier = MethodDescriptor(
                className = "BasicSamples",
                methodName = "basicIf",
                argumentsNumber = 1
            ),
            { a, result -> a + 2 - 3 > 10 && result == true },
            { a, result -> !(a + 2 - 3 > 10) && result == false }
        )
    }

    @Test
    fun testMinValue() {
        discoverProperties<Any, Any>(
            methodIdentifier = MethodDescriptor(
                className = "MinValue",
                methodName = "findMinValue",
                argumentsNumber = 1
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testPhi() {
        discoverProperties<Any, Any>(
            methodIdentifier = MethodDescriptor(
                className = "Phi",
                methodName = "foo",
                argumentsNumber = 1
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testMethodCollision() {
        discoverProperties<Any, Any>(
            methodIdentifier = MethodDescriptor(
                className = "MethodCollision",
                methodName = "main",
                argumentsNumber = 0
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testPasswordExposure() {
        discoverProperties<Any, Any>(
            methodIdentifier = MethodDescriptor(
                className = "passwordExposureFP",
                methodName = "usage1",
                argumentsNumber = 0
            ),
            analysisResultMatchers = emptyArray()
        )
    }
}
