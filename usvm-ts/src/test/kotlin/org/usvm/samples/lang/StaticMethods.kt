package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import kotlin.test.Test

class StaticMethods : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/StaticMethods.ts"

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
            { a, r -> r.isNaN() && a.isNaN() },
            { a, r -> (r eq a) && (a eq 1) },
            { a, r -> (r eq a) && (a eq 2) },
            { a, r -> (r eq 100) && !(a.isNaN() || (a eq 1) || (a eq 2)) },
        )
    }

    @Test
    fun `test manyArguments`() {
        val method = getMethod("manyArguments")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, _, _, _, r -> (r eq a) && (a eq 1) },
            { _, b, _, _, r -> (r eq b) && (b eq 2) },
            { _, _, c, _, r -> (r eq c) && (c eq 3) },
            { _, _, _, d, r -> (r eq d) && (d eq 4) },
            { a, b, c, d, r -> (r eq 100) && !((a eq 1) || (b eq 2) || (c eq 3) || (d eq 4)) },
        )
    }
}
