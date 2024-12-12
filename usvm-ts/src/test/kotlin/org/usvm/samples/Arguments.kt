package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

@Disabled // todo: disabled until USVM fix after upgrade on actual jacodb
class Arguments : TSMethodTestRunner() {
    @Test
    fun testNoArgs() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "noArguments",
                argumentsNumber = 0
            ),
            { r -> r?.number == 42.0 }
        )
    }

    @Test
    fun testSingleArg() {
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "singleArgument",
                argumentsNumber = 1
            ),
            { a, r -> a == r }
        )
    }

    @Test
    fun testManyArgs() {
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "Arguments.ts",
                className = "SimpleClass",
                methodName = "manyArguments",
                argumentsNumber = 3
            ),
            { a, _, _, r -> a.number == 1.0 && r == a },
            { _, b, _, r -> b.number == 2.0 && r == b },
            { _, _, c, r -> c.number == 3.0 && r == c },
            { a, b, c, r ->
                a.number != 1.0 && b.number != 2.0 && c.number != 3.0 && r?.number == 100.0
            },
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
