package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.TSObject
import org.usvm.util.MethodDescriptor
import org.usvm.util.TSMethodTestRunner

class TypeCoercion : TSMethodTestRunner() {
    @Test
    fun testArgWithConst() {
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "TypeCoercion.ts",
                className = "TypeCoercion",
                methodName = "argWithConst",
                argumentsNumber = 1
            ),
            { a, r -> a.number == 1.0 && r?.number == 1.0 },
            { a, r -> a.number != 1.0 && r?.number == 0.0 },
        )
    }

    @Test
    fun testArgWithArg() {
        discoverProperties<TSObject.Boolean, TSObject.TSNumber, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "TypeCoercion.ts",
                className = "TypeCoercion",
                methodName = "argWithArg",
                argumentsNumber = 2
            ),
            { a, b, r -> (a.number + b.number == 10.0) && r?.number == 1.0 },
            { a, b, r -> (a.number + b.number != 10.0) && r?.number == 0.0 },
        )
    }

    @Test
    fun testUnreachableByType() {
        discoverProperties<TSObject.TSNumber, TSObject.Boolean, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "TypeCoercion.ts",
                className = "TypeCoercion",
                methodName = "unreachableByType",
                argumentsNumber = 2
            ),
            { a, b, r -> a.number != b.number && r?.number == 2.0 },
            { a, b, r -> (a.number == b.number) && !(a.boolean && !b.value) && r?.number == 1.0 },
            // Unreachable branch matcher
            { _, _, r ->  r?.number != 0.0 },
        )
    }

    @Test
    fun testTransitiveCoercion() {
        discoverProperties<TSObject.TSNumber, TSObject.Boolean, TSObject.TSNumber, TSObject.TSNumber>(
            methodIdentifier = MethodDescriptor(
                fileName = "TypeCoercion.ts",
                className = "TypeCoercion",
                methodName = "transitiveCoercion",
                argumentsNumber = 3
            ),
            { a, b, c, r -> a.number == b.number && b.number == c.number && r?.number == 1.0 },
            { a, b, c, r -> a.number == b.number && (b.number != c.number || !c.boolean) && r?.number == 2.0 },
            { a, b, _, r -> a.number != b.number && r?.number == 3.0 }
        )
    }
}
