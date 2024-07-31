package org.usvm.samples

import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

class StaticMethods : TSMethodTestRunner() {
    @Test
    fun testNoArgsStaticMethod() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "StaticMethods.ts",
                className = "StaticMethods",
                methodName = "noArguments",
                argumentsNumber = 0
            )
        )
    }

    @Test
    fun testSingleArgStaticMethod() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "StaticMethods.ts",
                className = "StaticMethods",
                methodName = "singleArgument",
                argumentsNumber = 1
            )
        )
    }

    @Test
    fun testManyArgsStaticMethod() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "StaticMethods.ts",
                className = "StaticMethods",
                methodName = "manyArguments",
                argumentsNumber = 4
            )
        )
    }
}
