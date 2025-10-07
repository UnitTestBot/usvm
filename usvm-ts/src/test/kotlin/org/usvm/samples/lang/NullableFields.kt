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
        discoverProperties<TsTestValue, TsTestValue.TsBoolean>(
            method,
            { a, r ->
                // `a is null => r is true`
                (a is TsTestValue.TsNull) && r.value
            },
            { a, r ->
                // `a is true => r is true`
                (a is TsTestValue.TsBoolean) && a.value && r.value
            },
            { a, r ->
                // `a is false => r is false`
                (a is TsTestValue.TsBoolean) && !a.value && !r.value
            },
            invariants = arrayOf(
                // r is Boolean
                { _, _ -> true },
            )
        )
    }

    @Test
    fun `test useOptions`() {
        val method = getMethod("useOptions")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsBoolean>(
            method,
            { a, r ->
                val f = a.properties.getValue("isVisible")
                r.value && f is TsTestValue.TsNull
            },
            { a, r ->
                val f = a.properties.getValue("isVisible")
                r.value && f is TsTestValue.TsBoolean && f.value
            },
            { a, r ->
                val f = a.properties.getValue("isVisible")
                !r.value && f is TsTestValue.TsBoolean && !f.value
            },
            invariants = arrayOf(
                // r is Boolean
                { _, _ -> true },
            )
        )
    }
}
