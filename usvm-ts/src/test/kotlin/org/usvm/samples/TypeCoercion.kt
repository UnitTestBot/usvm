package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class TypeCoercion : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "TypeCoercion.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testArgWithConst() {
        val method = getMethod("TypeCoercion", "argWithConst")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, r -> a.number == 1.0 && r.number == 1.0 },
            { a, r -> a.number != 1.0 && r.number == 0.0 },
        )
    }

    @Test
    fun testDualBoolean() {
        val method = getMethod("TypeCoercion", "dualBoolean")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, r -> a.number == 0.0 && r.number == -1.0 },
            { a, r -> a.number == 1.0 && r.number == 2.0 },
            { a, r -> a.number != 0.0 && a.number != 1.0 && r.number == 3.0 }
        )
    }

    @Test
    fun testDualBooleanWithoutTypes() {
        val method = getMethod("TypeCoercion", "dualBooleanWithoutTypes")
        discoverProperties<TSObject, TSObject.TSNumber>(
            method,
        )
    }

    @Test
    fun testArgWithArg() {
        val method = getMethod("TypeCoercion", "argWithArg")
        discoverProperties<TSObject.Boolean, TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, b, r -> (a.number + b.number == 10.0) && r.number == 1.0 },
            { a, b, r -> (a.number + b.number != 10.0) && r.number == 0.0 },
        )
    }

    @Test
    fun testUnreachableByType() {
        val method = getMethod("TypeCoercion", "unreachableByType")
        discoverProperties<TSObject.TSNumber, TSObject.Boolean, TSObject.TSNumber>(
            method,
            { a, b, r -> a.number != b.number && r.number == 2.0 },
            { a, b, r -> (a.number == b.number) && !(a.boolean && !b.value) && r.number == 1.0 },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 }
            )
        )
    }

    @Test
    fun testTransitiveCoercion() {
        val method = getMethod("TypeCoercion", "transitiveCoercion")
        discoverProperties<TSObject.TSNumber, TSObject.Boolean, TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, b, c, r -> a.number == b.number && b.number == c.number && r.number == 1.0 },
            { a, b, c, r -> a.number == b.number && (b.number != c.number || !c.boolean) && r.number == 2.0 },
            { a, b, _, r -> a.number != b.number && r.number == 3.0 }
        )
    }

    @Test
    fun testTransitiveCoercionNoTypes() {
        val method = getMethod("TypeCoercion", "transitiveCoercionNoTypes")
        discoverProperties<TSObject.Unknown, TSObject.Unknown, TSObject.Unknown, TSObject.TSNumber>(
            method,
            // Too complicated to write property matchers, examine run log to verify the test.
        )
    }
}
