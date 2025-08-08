package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq
import kotlin.test.Test

class InstanceMethods : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/InstanceMethods.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test noArguments`() {
        val method = getMethod("noArguments")
        discoverProperties<TsTestValue.TsNumber>(
            method,
            { r -> r eq 42 },
        )
    }

    @Test
    fun `test singleArgument`() {
        val method = getMethod("singleArgument")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> a.isNaN() && r.isNaN() },
            { a, r -> (a eq 1) && (r eq a) },
            { a, r -> (a eq 2) && (r eq a) },
            { a, r ->
                val neq1 = a neq 1
                val neq2 = a neq 2
                !a.isNaN() && neq1 && neq2 && (r eq 100)
            },
        )
    }

    @Test
    fun `test manyArguments`() {
        val method = getMethod("manyArguments")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, _, _, _, r -> (a eq 1) && (r eq a) },
            { _, b, _, _, r -> (b eq 2) && (r eq b) },
            { _, _, c, _, r -> (c eq 3) && (r eq c) },
            { _, _, _, d, r -> (d eq 4) && (r eq d) },
            { a, b, c, d, r -> !((a eq 1) || (b eq 2) || (c eq 3) || (d eq 4)) && (r eq 100) },
        )
    }
}
