package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class Neg : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "operators")

    @Test
    fun `test negateNumber`() {
        val method = getMethod(className, "negateNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.number.isNaN() && r.number.isNaN() },
            { x, r -> (x.number == 0.0) && (r.number == 0.0) },
            { x, r -> (x.number > 0) && (r.number == -x.number) },
            { x, r -> (x.number < 0) && (r.number == -x.number) },
            invariants = arrayOf(
                { x, r -> (x.number.isNaN() && r.number.isNaN()) || r.number == -x.number },
            )
        )
    }

    @Test
    fun `test negateBoolean`() {
        val method = getMethod(className, "negateBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.value && (r.number == -1.0) },
            { x, r -> !x.value && (r.number == -0.0) },
        )
    }

    @Test
    fun `test negateUndefined`() {
        val method = getMethod(className, "negateUndefined")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }
}