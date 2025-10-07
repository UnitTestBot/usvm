package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class NullableFields : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/NullableFields.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test useNullableArg`() {
        val method = getMethod("useNullableArg")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method,
            { a, r ->
                (r eq 1) && (a is TsTestValue.TsNull)
            },
            { a, r ->
                (r eq 1) && (a is TsTestValue.TsBoolean) && a.value
            },
            { a, r ->
                (r eq 2) && (a is TsTestValue.TsBoolean) && !a.value
            },
            invariants = arrayOf(
                { _, r -> (r eq 1) or (r eq 2) },
            )
        )
    }

    @Test
    fun `test useOptions`() {
        val method = getMethod("useOptions")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { a, r ->
                val f = a.properties.getValue("isVisible")
                (r eq 1) && f is TsTestValue.TsNull
            },
            { a, r ->
                val f = a.properties.getValue("isVisible")
                (r eq 1) && f is TsTestValue.TsBoolean && f.value
            },
            { a, r ->
                val f = a.properties.getValue("isVisible")
                (r eq 2) && f is TsTestValue.TsBoolean && !f.value
            },
            invariants = arrayOf(
                { _, r -> (r eq 0) or (r eq 1) },
            )
        )
    }
}
