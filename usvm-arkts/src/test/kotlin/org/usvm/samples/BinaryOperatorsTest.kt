package org.usvm.samples

import kotlin.test.Test

class BinaryOperatorsForLiteralsTest : PandaMethodTestRunner() {
    @Test
    fun testSumNumber() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumNumber",
                argumentsNumber = 0
            ),
            { r -> r == 2.0 }
        )
    }
}