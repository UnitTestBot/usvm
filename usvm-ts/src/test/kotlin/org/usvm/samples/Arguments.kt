package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

class Arguments : TSMethodTestRunner() {
    @Test
    fun testNoArgs() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "noArguments",
                argumentsNumber = 0
            )
        )
    }

    @Test
    fun testSingleArg() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "singleArgument",
                argumentsNumber = 1
            )
        )
    }

    @Test
    fun testManyArgs() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "manyArguments",
                argumentsNumber = 3
            )
        )
    }

    @Test
    @Disabled
    fun testThisArg() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "thisArgument",
                argumentsNumber = 0
            )
        )
    }
}
