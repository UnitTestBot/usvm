package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class Neg : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Neg.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun `test negateNumber`() {
        val method = getMethod("Neg", "negateNumber")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method = method,
            { x, r -> x.number.isNaN() && r.number.isNaN() },
            { x, r -> x.number == 0.0 && r.number == 0.0 },
            { x, r -> x.number > 0 && r.number == -x.number },
            { x, r -> x.number < 0 && r.number == -x.number },
            invariants = arrayOf(
                { x, r -> (x.number.isNaN() && r.number.isNaN()) || r.number == -x.number },
            )
        )
    }

    @Test
    fun `test negateBoolean`() {
        val method = getMethod("Neg", "negateBoolean")
        discoverProperties<TsValue.TsBoolean, TsValue.TsNumber>(
            method = method,
            { x, r -> x.value && r.number == -1.0 },
            { x, r -> !x.value && r.number == -0.0 },
        )
    }

    @Test
    fun `test negateUndefined`() {
        val method = getMethod("Neg", "negateUndefined")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }
}
