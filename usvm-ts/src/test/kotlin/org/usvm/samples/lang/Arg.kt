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

    @Test
    fun `shadowed Infinity reads its parameter`() {
        val method = getMethod(methodName = "shadowInfinity")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { argument, result -> argument.number == 3.0 && result.number == 3.0 },
            { argument, result -> argument.number == -2.0 && result.number == -2.0 },
            invariants = arrayOf({ argument, result ->
                if (argument.isNaN()) result.isNaN() else result.number == argument.number
            }),
        )
    }

    @Test
    fun `shadowed NaN reads its parameter`() {
        val method = getMethod(methodName = "shadowNaN")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { argument, result -> argument.number == 4.0 && result.number == 4.0 },
            { argument, result -> argument.number == -5.0 && result.number == -5.0 },
            invariants = arrayOf({ argument, result ->
                if (argument.isNaN()) result.isNaN() else result.number == argument.number
            }),
        )
    }

    @Test
    fun `builtin nonfinite values remain numeric constants`() {
        val infinityMethod = getMethod(methodName = "builtinInfinity")
        val nanMethod = getMethod(methodName = "builtinNaN")

        discoverProperties<TsTestValue.TsNumber>(
            method = infinityMethod,
            { result -> result.number == Double.POSITIVE_INFINITY },
            invariants = arrayOf({ result -> result.number == Double.POSITIVE_INFINITY }),
        )
        discoverProperties<TsTestValue.TsNumber>(
            method = nanMethod,
            { result -> result.isNaN() },
            invariants = arrayOf({ result -> result.isNaN() }),
        )
    }
}
