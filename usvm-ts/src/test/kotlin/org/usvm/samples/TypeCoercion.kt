package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import org.usvm.util.isTruthy

class TypeCoercion : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "TypeCoercion.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    private val TsValue.TsBoolean.number: Double
        get() = if (value) 1.0 else 0.0

    @Test
    fun testArgWithConst() {
        val method = getMethod("TypeCoercion", "argWithConst")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, r -> a.number == 1.0 && r.number == 1.0 },
            { a, r -> a.number != 1.0 && r.number == 0.0 },
        )
    }

    @Test
    fun testDualBoolean() {
        val method = getMethod("TypeCoercion", "dualBoolean")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, r -> a.number == 0.0 && r.number == -1.0 },
            { a, r -> a.number == 1.0 && r.number == 2.0 },
            { a, r -> a.number != 0.0 && a.number != 1.0 && r.number == 3.0 },
            invariants = arrayOf(
                { _, r -> r.number != 1.0 }
            )
        )
    }

    @Test
    @Disabled("Unsupported string")
    fun testDualBooleanWithoutTypes() {
        val method = getMethod("TypeCoercion", "dualBooleanWithoutTypes")
        discoverProperties<TsValue.TsUnknown, TsValue.TsNumber>(
            method,
        )
    }

    @Test
    fun testArgWithArg() {
        val method = getMethod("TypeCoercion", "argWithArg")
        discoverProperties<TsValue.TsBoolean, TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, b, r -> (a.number + b.number == 10.0) && r.number == 1.0 },
            { a, b, r -> (a.number + b.number != 10.0) && r.number == 0.0 },
        )
    }

    @Test
    fun testUnreachableByType() {
        val method = getMethod("TypeCoercion", "unreachableByType")
        discoverProperties<TsValue.TsNumber, TsValue.TsBoolean, TsValue.TsNumber>(
            method,
            { a, b, r -> a.number != b.number && r.number == 2.0 },
            { a, b, r -> (a.number == b.number) && !(isTruthy(a) && !b.value) && r.number == 1.0 },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 },
            )
        )
    }

    @Test
    @Disabled("Wrong IR, incorrect handling of NaN value")
    fun testTransitiveCoercion() {
        val method = getMethod("TypeCoercion", "transitiveCoercion")
        discoverProperties<TsValue.TsNumber, TsValue.TsBoolean, TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, b, c, r -> a.number == b.number && b.number == c.number && r.number == 1.0 },
            { a, b, c, r -> a.number == b.number && (b.number != c.number || !isTruthy(c)) && r.number == 2.0 },
            { a, b, _, r -> a.number != b.number && r.number == 3.0 },
        )
    }

    @Test
    fun testTransitiveCoercionNoTypes() {
        val method = getMethod("TypeCoercion", "transitiveCoercionNoTypes")
        discoverProperties<TsValue.TsUnknown, TsValue.TsUnknown, TsValue.TsUnknown, TsValue.TsNumber>(
            method,
            // Too complicated to write property matchers, examine run log to verify the test.
        )
    }
}
