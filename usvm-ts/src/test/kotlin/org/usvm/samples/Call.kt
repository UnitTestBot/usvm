package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class Call : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Call.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun `test simpleCall`() {
        val method = getMethod("Call", "simpleCall")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }

    @Test
    fun `test fib`() {
        val method = getMethod("Call", "fib")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method = method,
            { n, r -> n.number.isNaN() && r.number == 0.0 },
            { n, r -> n.number < 0.0 && r.number == -1.0 },
            { n, r -> n.number > 10.0 && r.number == -2.0 },
            { n, r -> n.number == 0.0 && r.number == 1.0 },
            { n, r -> n.number == 1.0 && r.number == 1.0 },
            { n, r -> n.number > 0 && n.number != 1.0 && n.number <= 10.0 && fib(n.number) == r.number },
            invariants = arrayOf(
                { n, r -> fib(n.number) == r.number }
            )
        )
    }
}

fun fib(n: Double): Double {
    if (n.isNaN()) return 0.0
    if (n < 0) return -1.0
    if (n > 10) return -2.0
    if (n == 0.0) return 1.0
    if (n == 1.0) return 1.0
    return fib(n - 1.0) + fib(n - 2.0)
}
