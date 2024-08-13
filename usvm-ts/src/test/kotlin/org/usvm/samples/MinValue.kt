package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

class MinValue : TSMethodTestRunner() {
    @Test
    @Disabled
    fun testMinValue() {
        discoverProperties<TSObject.Array, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "MinValue.ts",
                className = globalClassName,
                methodName = "findMinValue",
                argumentsNumber = 1
            )
        )
    }
}
