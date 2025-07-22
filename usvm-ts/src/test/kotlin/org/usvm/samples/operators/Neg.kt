package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN

class Neg : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Neg.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test negateNumber`() {
        val method = getMethod(className, "negateNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r -> r.isNaN() && x.isNaN() },
            { x, r -> (r eq 0) && (x eq 0) },
            { x, r -> (r.number == -x.number) && (x.number > 0) },
            { x, r -> (r.number == -x.number) && (x.number < 0) },
            invariants = arrayOf(
                { x, r -> (x.isNaN() && r.isNaN()) || r.number == -x.number },
            )
        )
    }

    @Test
    fun `test negateBoolean`() {
        val method = getMethod(className, "negateBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (r eq -1) && x.value },
            { x, r -> (r eq -0.0) && !x.value },
        )
    }

    @Test
    fun `test negateUndefined`() {
        val method = getMethod(className, "negateUndefined")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.isNaN() },
        )
    }
}
