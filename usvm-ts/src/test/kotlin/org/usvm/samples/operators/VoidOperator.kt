package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class VoidOperator : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/VoidOperator.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `void operator returns undefined`() {
        val method = getMethod("testVoidOperator")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, r -> r eq 1 },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `void operator with side effects`() {
        val method = getMethod("testVoidWithSideEffect")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }
}
