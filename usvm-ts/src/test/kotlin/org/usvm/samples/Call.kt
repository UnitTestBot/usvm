package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner

class Call : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test simple`() {
        val method = getMethod(className, "callSimple")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }

    @Test
    fun `test fib`() {
        val method = getMethod(className, "fib")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method = method,
            { n, r -> n.number.isNaN() && r.number == 0.0 },
            { n, r -> n.number < 0.0 && r.number == -1.0 },
            { n, r -> n.number > 10.0 && r.number == -100.0 },
            { n, r -> n.number == 0.0 && r.number == 1.0 },
            { n, r -> n.number == 1.0 && r.number == 1.0 },
            { n, r -> n.number > 0 && n.number != 1.0 && n.number <= 10.0 && fib(n.number) == r.number },
            invariants = arrayOf(
                { n, r -> fib(n.number) == r.number }
            )
        )
    }

    @Test
    fun `test concrete`() {
        val method = getMethod(className, "callConcrete")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 10.0 },
        )
    }

    @Test
    fun `test hidden`() {
        val method = getMethod(className, "callHidden")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 20.0 },
        )
    }

    @Test
    fun `test no vararg`() {
        val method = getMethod(className, "callNoVararg")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
        )
    }

    @Test
    fun `test vararg 1`() {
        val method = getMethod(className, "callVararg1")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 },
        )
    }

    @Test
    fun `test vararg 2`() {
        val method = getMethod(className, "callVararg2")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 3.0 },
        )
    }

    @Test
    fun `test vararg array`() {
        val method = getMethod(className, "callVarargArray")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 },
        )
    }

    @Test
    fun `test callNormal`() {
        val method = getMethod(className, "callNormal")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 },
        )
    }

    @Test
    fun `test single`() {
        val method = getMethod(className, "callSingle")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
        )
    }

    @Test
    fun `test none`() {
        val method = getMethod(className, "callNone")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 0.0 },
        )
    }

    @Test
    fun `test undefined`() {
        val method = getMethod(className, "callUndefined")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 0.0 },
        )
    }

    @Test
    fun `test extra`() {
        val method = getMethod(className, "callExtra")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 },
        )
    }
}

fun fib(n: Double): Double {
    if (n.isNaN()) return 0.0
    if (n < 0) return -1.0
    if (n > 10) return -100.0
    if (n == 0.0) return 1.0
    if (n == 1.0) return 1.0
    return fib(n - 1.0) + fib(n - 2.0)
}
