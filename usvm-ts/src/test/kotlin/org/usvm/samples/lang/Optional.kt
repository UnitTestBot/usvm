package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class Optional : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Optional.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test nullableArgument`() {
        val method = getMethod("nullableArgument")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { x, r -> (r eq 0) && (x is TsTestValue.TsUndefined) },
            { x, r -> (r eq 0) && (x is TsTestValue.TsNull) },
            { x, r -> (r eq 1) && (x is TsTestValue.TsNumber) && (x eq 1) },
            { x, r -> (r eq 2) && (x is TsTestValue.TsNumber) && (x eq 2) },
            { x, r -> (r eq 10) && (x is TsTestValue.TsNumber) },
        )
    }
}
