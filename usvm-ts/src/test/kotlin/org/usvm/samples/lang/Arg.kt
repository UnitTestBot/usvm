package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.isNaN
import kotlin.test.Test

class Arg : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Arg.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test number arg`() {
        val method = getMethod("numberArg")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r -> a.isNaN() },
            { a, r -> a.number == 0.0 },
            { a, r -> a.number == 1.0 },
            { a, r ->
                !a.isNaN() && (a.number != 0.0) && (a.number != 1.0)
            },
            invariants = arrayOf(
                { a, r ->
                    if (a.isNaN()) r.isNaN() else (a.number == r.number)
                }
            )
        )
    }
}
