package org.usvm.samples

import org.junit.jupiter.api.Disabled
import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner
import kotlin.test.Test

@Disabled // todo: disabled until USVM fix after upgrade on actual jacodb
class StaticMethods : TSMethodTestRunner() {
    @Test
    fun testNoArgsStaticMethod() {
        discoverProperties<TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "StaticMethods.ts",
                className = "StaticMethods",
                methodName = "noArguments",
                argumentsNumber = 0
            ),
            { r -> r?.number == 42.0 }
        )
    }

    @Test
    fun testSingleArgStaticMethod() {
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "StaticMethods.ts",
                className = "StaticMethods",
                methodName = "singleArgument",
                argumentsNumber = 1
            ),
            { a, r -> a.number == 1.0 && r?.number == 100.0 },
            { a, r -> a.number != 1.0 && r?.number == 0.0 },
        )
    }

    @Test
    fun testManyArgsStaticMethod() {
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "StaticMethods.ts",
                className = "StaticMethods",
                methodName = "manyArguments",
                argumentsNumber = 4
            ),
            { a, _, _, _, r -> a.number == 1.0 && r == a },
            { _, b, _, _, r -> b.number == 2.0 && r == b },
            { _, _, c, _, r -> c.number == 3.0 && r == c },
            { _, _, _, d, r -> d.number == 4.0 && r == d },
            { a, b, c, d, r ->
                a.number != 1.0 && b.number != 2.0 && c.number != 3.0 && d.number != 4.0 && r?.number == 100.0
            },
        )
    }
}
