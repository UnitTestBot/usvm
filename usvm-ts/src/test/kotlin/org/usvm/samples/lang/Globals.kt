package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq
import kotlin.test.Test

class Globals : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Globals.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test getValue`() {
        val method = getMethod("getValue")
        discoverProperties<TsTestValue.TsNumber>(
            method,
            { r -> r eq 42 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test setValue`() {
        val method = getMethod("setValue")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> a.isNaN() && r.isNaN() },
            { a, r -> (a eq 0) && (r eq 0) },
            { a, r -> !a.isNaN() && (a neq 0) && (a eq r) },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Test
    fun `test useValue`() {
        val method = getMethod("useValue")
        discoverProperties<TsTestValue.TsNumber>(
            method,
            { r -> r eq 142 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }
}
