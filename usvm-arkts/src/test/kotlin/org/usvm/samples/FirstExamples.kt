package org.usvm.samples

import org.junit.jupiter.api.Test

// TODO rename it
class FirstExamplesTest : PandaMethodTestRunner() {
    @Test
    fun testDataFlowSecurity() {
        discoverProperties(
            methodIdentifier = MethodDescriptor(
                className = "DataFlowSecurity",
                methodName = "validate",
                argumentsNumber = 1
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testBasicSamples() {
        discoverProperties(
            methodIdentifier = MethodDescriptor(
                className = "BasicSamples",
                methodName = "add",
                argumentsNumber = 0
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testSomeOps() {
        discoverProperties(
            methodIdentifier = MethodDescriptor(
                className = "BasicSamples",
                methodName = "someOps",
                argumentsNumber = 2
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testBasicIf() {
        discoverProperties(
            methodIdentifier = MethodDescriptor(
                className = "BasicSamples",
                methodName = "basicIf",
                argumentsNumber = 1
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testMinValue() {
        discoverProperties(
            methodIdentifier = MethodDescriptor(
                className = "MinValue",
                methodName = "findMinValue",
                argumentsNumber = 1
            ),
            analysisResultMatchers = emptyArray()
        )
    }

    @Test
    fun testMethodCollision() {
        discoverProperties(
            methodIdentifier = MethodDescriptor(
                className = "MethodCollision",
                methodName = "main",
                argumentsNumber = 0
            ),
            analysisResultMatchers = emptyArray()
        )
    }
}
