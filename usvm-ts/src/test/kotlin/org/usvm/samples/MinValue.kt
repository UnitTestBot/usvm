package org.usvm.samples

import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

class MinValue : TSMethodTestRunner() {

    @Test
    fun testMinValue() {
        discoverProperties<Any, Any>(
            methodIdentifier = MethodDescriptor(
                className = globalClassName,
                methodName = "findMinValue",
                argumentsNumber = 1
            )
        )
    }
}
