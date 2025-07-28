package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq
import kotlin.test.Test

class Arg : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Arg.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Disabled("Forking void intercept is not implemented yet")
    @Test
    fun `test number arg`() {
        val method = getMethod("numberArg")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r -> a.isNaN() },
            { a, r -> a eq 0 },
            { a, r -> a eq 1 },
            { a, r -> !a.isNaN() && (a neq 0) && (a neq 1) },
            invariants = arrayOf(
                { a, r ->
                    if (a.isNaN()) r.isNaN() else (a eq r)
                }
            )
        )
    }
}
