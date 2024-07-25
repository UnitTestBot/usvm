package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

class Arguments : TSMethodTestRunner() {
    @Test
    @Disabled
    fun testMinValue() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "noArguments",
                argumentsNumber = 0
            )
        )
    }
}
