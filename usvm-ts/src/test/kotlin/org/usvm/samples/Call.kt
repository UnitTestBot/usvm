package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
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

    @Test
    fun `test overloading number`() {
        val method = getMethod(className, "callOverloadedNumber")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 }
        )
    }

    @Test
    fun `test overloading string`() {
        val method = getMethod(className, "callOverloadedString")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 }
        )
    }

    @Disabled("Namespaces are not supported")
    @Test
    fun `test namespace`() {
        val method = getMethod(className, "callNamespace")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 30.0 }
        )
    }

    @Disabled("Static calls are broken in IR")
    @Test
    fun `test static`() {
        val method = getMethod(className, "callStatic")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 50.0 }
        )
    }

    @Disabled("Inheritance is broken")
    @Test
    fun `test virtual call`() {
        val method = getMethod(className, "callVirtual")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            { obj, r ->
                when (obj.name) {
                    "Parent" -> r.number == 100.0
                    "Child" -> r.number == 200.0
                    else -> false
                }
            },
        )
    }

    @Test
    fun `test virtual parent`() {
        val method = getMethod(className, "callVirtualParent")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 100.0 },
        )
    }

    @Disabled("Calls to super are broken in IR")
    @Test
    fun `test virtual child`() {
        val method = getMethod(className, "callVirtualChild")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 200.0 },
        )
    }

    @Disabled("Too complex")
    @Test
    fun `test virtual dispatch`() {
        val method = getMethod(className, "virtualDispatch")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            { obj, r -> obj.name == "Parent" && r.number == 100.0 },
            { obj, r -> obj.name == "Child" && r.number == 200.0 },
            invariants = arrayOf(
                { _, r -> r.number != -1.0 },
            )
        )
    }

    @Disabled("Default parameters are not supported in ArkIR")
    @Test
    fun `test default`() {
        val method = getMethod(className, "callDefault")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun `test default pass`() {
        val method = getMethod(className, "callDefaultPass")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 8.0 },
        )
    }

    @Disabled("Default parameters are not supported in ArkIR")
    @Test
    fun `test default undefined`() {
        val method = getMethod(className, "callDefaultUndefined")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun `test constructor with param`() {
        val method = getMethod(className, "callConstructorWithParam")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Disabled("Public parameters in constructors are not supported in ArkIR")
    @Test
    fun `test constructor with public param`() {
        val method = getMethod(className, "callConstructorWithPublicParam")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
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
