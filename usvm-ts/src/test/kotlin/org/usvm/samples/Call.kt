package org.usvm.samples

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class Call : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Call.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun `test simpleCall`() {
        val method = getMethod("Call", "simpleCall")
        discoverProperties<TSObject.TSNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }

    @Test
    fun `test fib`() {
        val method = getMethod("Call", "fib")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { n, r -> n.number < 0.0 && r.number == -1.0 },
            { n, r -> n.number > 10.0 && r.number == -2.0 },
            { n, r -> n.number == 0.0 && r.number == 1.0 },
            { n, r -> n.number == 1.0 && r.number == 1.0 },
            { n, r -> n.number > 0 && n.number != 1.0 && fib(n.number) == r.number },
        )
    }
}

fun fib(n: Double): Double {
    if (n < 0) return -1.0
    if (n > 10) return -2.0
    if (n == 0.0) return 1.0
    if (n == 1.0) return 1.0

    return fib(n - 1.0) + fib(n - 2.0)
}
