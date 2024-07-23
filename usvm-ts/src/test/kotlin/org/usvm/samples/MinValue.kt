package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

@Disabled("Not yet implemented")
class MinValue : TSMethodTestRunner() {

    @Test
    fun testMinValue() {
        discoverProperties<Any, Any>(
            methodIdentifier = MethodDescriptor(
                fileName = "MinValue",
                className = globalClassName,
                methodName = "findMinValue",
                argumentsNumber = 1
            )
        )
    }
}
