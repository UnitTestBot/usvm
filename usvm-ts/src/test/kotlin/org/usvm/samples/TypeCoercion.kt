package org.usvm.samples

import org.junit.jupiter.api.Disabled
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
            { a, b, r -> (a.number + b.number == 10.0) && r?.number == 1.0 },
            { a, b, r -> (a.number + b.number != 10.0) && r?.number == 0.0 },
        )
    }
}
