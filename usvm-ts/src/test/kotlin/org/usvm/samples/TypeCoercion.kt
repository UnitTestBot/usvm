package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.isTruthy

private val TsValue.TsBoolean.number: Double
    get() = if (value) 1.0 else 0.0

class TypeCoercion : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test argWithConst`() {
        val method = getMethod(className, "argWithConst")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, r -> (a.number == 1.0) && (r.number == 1.0) },
            { a, r -> (a.number != 1.0) && (r.number == 0.0) },
        )
    }

    @Test
    fun `test dualBoolean`() {
        val method = getMethod(className, "dualBoolean")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
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
        val method = getMethod(className, "dualBooleanWithoutTypes")
        discoverProperties<TsValue.TsUnknown, TsValue.TsNumber>(
            method,
        )
    }

    @Test
    fun `test argWithArg`() {
        val method = getMethod(className, "argWithArg")
        discoverProperties<TsValue.TsBoolean, TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, b, r -> (a.number + b.number == 10.0) && (r.number == 1.0) },
            { a, b, r -> (a.number + b.number != 10.0) && (r.number == 0.0) },
        )
    }

    @Test
    fun `test unreachableByType`() {
        val method = getMethod(className, "unreachableByType")
        discoverProperties<TsValue.TsNumber, TsValue.TsBoolean, TsValue.TsNumber>(
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
        val method = getMethod(className, "transitiveCoercion")
        discoverProperties<TsValue.TsNumber, TsValue.TsBoolean, TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, b, c, r -> (a.number == b.number) && (b.number == c.number) && (r.number == 1.0) },
            { a, b, c, r -> (a.number == b.number) && (b.number != c.number || !isTruthy(c)) && (r.number == 2.0) },
            { a, b, _, r -> (a.number != b.number) && (r.number == 3.0) },
        )
    }

    @Test
    fun `test transitiveCoercionNoTypes`() {
        val method = getMethod(className, "transitiveCoercionNoTypes")
        discoverProperties<TsValue.TsUnknown, TsValue.TsUnknown, TsValue.TsUnknown, TsValue.TsNumber>(
            method,
            // Too complicated to write property matchers, examine run log to verify the test.
        )
    }
}
