package org.usvm.samples

import kotlin.test.Test
import org.junit.jupiter.api.Disabled
import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner

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
