package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

class Undefined : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Undefined.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test isUndefined`() {
        val method = getMethod("isUndefined")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                (r eq 1) && (a is TsTestValue.TsUndefined)
            },
            { a, r ->
                (r eq 2) && (a !is TsTestValue.TsUndefined)
            },
        )
    }

    @Test
    fun `test isUndefinedOrNull`() {
        val method = getMethod("isUndefinedOrNull")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method,
            { a, r ->
                (r eq 1) && (a is TsTestValue.TsUndefined)
            },
            { a, r ->
                (r eq 2) && (a is TsTestValue.TsNull)
            },
            { a, r ->
                (r eq 3) && (a !is TsTestValue.TsUndefined) && (a !is TsTestValue.TsNull)
            },
        )
    }
}
