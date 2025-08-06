package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.isTruthy

private val TsTestValue.TsBoolean.number: Double
    get() = if (value) 1.0 else 0.0

class TypeCoercion : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/TypeCoercion.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test argWithConst`() {
        val method = getMethod("argWithConst")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> (a.number == 1.0) && (r.number == 1.0) },
            { a, r -> (a.number != 1.0) && (r.number == 0.0) },
        )
    }

    @Test
    fun `test dualBoolean`() {
        val method = getMethod("dualBoolean")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> (a.number == 0.0) && (r.number == -1.0) },
            { a, r -> (a.number == 1.0) && (r.number == 2.0) },
            { a, r -> (a.number != 0.0) && (a.number != 1.0) && (r.number == 3.0) },
            invariants = arrayOf(
                { _, r -> r.number != 1.0 }
            )
        )
    }

    @Test
    @Disabled("Unsupported string")
    fun `test dualBooleanWithoutTypes`() {
        val method = getMethod("dualBooleanWithoutTypes")
        discoverProperties<TsTestValue.TsUnknown, TsTestValue.TsNumber>(
            method,
        )
    }

    @Test
    fun `test argWithArg`() {
        val method = getMethod("argWithArg")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, b, r -> (a.number + b.number == 10.0) && (r.number == 1.0) },
            { a, b, r -> (a.number + b.number != 10.0) && (r.number == 0.0) },
        )
    }

    @Test
    fun `test unreachableByType`() {
        val method = getMethod("unreachableByType")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, b, r -> (a.number != b.number) && (r.number == 2.0) },
            { a, b, r -> (a.number == b.number) && !(isTruthy(a) && !b.value) && (r.number == 1.0) },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 },
            )
        )
    }

    @Test
    @Disabled("Wrong IR, incorrect handling of NaN value")
    fun `test transitiveCoercion`() {
        val method = getMethod("transitiveCoercion")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, b, c, r -> (a.number == b.number) && (b.number == c.number) && (r.number == 1.0) },
            { a, b, c, r -> (a.number == b.number) && (b.number != c.number || !isTruthy(c)) && (r.number == 2.0) },
            { a, b, _, r -> (a.number != b.number) && (r.number == 3.0) },
        )
    }

    @Test
    fun `test transitiveCoercionNoTypes`() {
        val method = getMethod("transitiveCoercionNoTypes")
        discoverProperties<TsTestValue.TsUnknown, TsTestValue.TsUnknown, TsTestValue.TsUnknown, TsTestValue.TsNumber>(
            method,
            // Too complicated to write property matchers, examine run log to verify the test.
        )
    }
}
