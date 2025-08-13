package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq

class Call : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Call.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test simple`() {
        val method = getMethod("callSimple")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
        )
    }

    @Test
    fun `test fib`() {
        val method = getMethod("fib")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { n, r -> n.isNaN() && (r eq 0) },
            { n, r -> n.number < 0.0 && (r eq -1) },
            { n, r -> n.number > 10.0 && (r eq -100) },
            { n, r -> (n eq 0) && (r eq 1) },
            { n, r -> (n eq 1) && (r eq 1) },
            { n, r -> n.number > 0 && (n neq 1.0) && n.number <= 10.0 && fib(n.number) == r.number },
            invariants = arrayOf(
                { n, r -> fib(n.number) == r.number }
            )
        )
    }

    @Test
    fun `test concrete`() {
        val method = getMethod("callConcrete")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 10 },
        )
    }

    @Test
    fun `test hidden`() {
        val method = getMethod("callHidden")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 20 },
        )
    }

    @Test
    fun `test no vararg`() {
        val method = getMethod("callNoVararg")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
        )
    }

    @Test
    fun `test vararg 1`() {
        val method = getMethod("callVararg1")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 2 },
        )
    }

    @Test
    fun `test vararg 2`() {
        val method = getMethod("callVararg2")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 3 },
        )
    }

    @Test
    fun `test vararg array`() {
        val method = getMethod("callVarargArray")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 2 },
        )
    }

    @Test
    fun `test callNormal`() {
        val method = getMethod("callNormal")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 2 },
        )
    }

    @Test
    fun `test single`() {
        val method = getMethod("callSingle")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
        )
    }

    @Test
    fun `test none`() {
        val method = getMethod("callNone")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 0 },
        )
    }

    @Test
    fun `test undefined`() {
        val method = getMethod("callUndefined")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 0 },
        )
    }

    @Test
    fun `test extra`() {
        val method = getMethod("callExtra")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 2 },
        )
    }

    @Test
    fun `test overloading number`() {
        val method = getMethod("callOverloadedNumber")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 }
        )
    }

    @Test
    fun `test overloading string`() {
        val method = getMethod("callOverloadedString")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 2 }
        )
    }

    @Disabled("Namespaces are not supported")
    @Test
    fun `test namespace`() {
        val method = getMethod("callNamespace")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 30 }
        )
    }

    @Disabled("Static calls are broken in IR")
    @Test
    fun `test static`() {
        val method = getMethod("callStatic")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 50 }
        )
    }

    @Disabled("Inheritance is broken")
    @Test
    fun `test virtual call`() {
        val method = getMethod("callVirtual")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { obj, r ->
                when (obj.name) {
                    "Parent" -> r eq 100
                    "Child" -> r eq 200
                    else -> false
                }
            },
        )
    }

    @Test
    fun `test virtual parent`() {
        val method = getMethod("callVirtualParent")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 100 },
        )
    }

    @Test
    fun `test virtual child`() {
        val method = getMethod("callVirtualChild")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 200 },
        )
    }

    @Disabled("Non-overridden virtual calls are not supported yet")
    @Test
    fun `test base call`() {
        val method = getMethod("callBaseMethod")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
        )
    }

    @Test
    fun `test virtual dispatch`() {
        val method = getMethod("virtualDispatch")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { obj, r -> obj.name == "Parent" && (r eq 100) },
            { obj, r -> obj.name == "Child" && (r eq 200) },
            invariants = arrayOf(
                { _, r -> r neq -1.0 },
            )
        )
    }

    @Disabled("Default parameters are not supported in ArkIR")
    @Test
    fun `test default`() {
        val method = getMethod("callDefault")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Test
    fun `test default pass`() {
        val method = getMethod("callDefaultPass")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 8 },
        )
    }

    @Disabled("Default parameters are not supported in ArkIR")
    @Test
    fun `test default undefined`() {
        val method = getMethod("callDefaultUndefined")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Test
    fun `test constructor with param`() {
        val method = getMethod("callConstructorWithParam")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Disabled("Public parameters in constructors are not supported in ArkIR")
    @Test
    fun `test constructor with public param`() {
        val method = getMethod("callConstructorWithPublicParam")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Test
    fun `test structural equality trickery`() {
        val method = getMethod("structuralEqualityTrickery")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 20 },
        )
    }

    @Test
    fun `test call lambda`() {
        val method = getMethod("callLambda")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r eq 42 },
            )
        )
    }

    @Test
    fun `test call closure capturing local`() {
        val method = getMethod("callClosureCapturingLocal")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r eq 42 },
            )
        )
    }

    @Test
    fun `test call closure capturing arguments`() {
        val method = getMethod("callClosureCapturingArguments")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                (a.value && b.value) && (r eq 1)
            },
            { a, b, r ->
                !(a.value && b.value) && (r eq 2)
            },
            invariants = arrayOf(
                { _, _, r -> r.number in listOf(1.0, 2.0) },
            )
        )
    }

    @Test
    fun `test call nested lambda`() {
        val method = getMethod("callNestedLambda")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r eq 42 },
            )
        )
    }

    @Test
    fun `test call nested closure capturing outer local`() {
        val method = getMethod("callNestedClosureCapturingOuterLocal")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r eq 42 },
            )
        )
    }

    @Test
    fun `test call nested closure capturing inner local`() {
        val method = getMethod("callNestedClosureCapturingInnerLocal")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r eq 42 },
            )
        )
    }

    @Test
    fun `test call nested closure capturing local and argument`() {
        val method = getMethod("callNestedClosureCapturingLocalAndArgument")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, r -> a.value && (r eq 1) },
            { a, r -> !a.value && (r eq 2) },
            invariants = arrayOf(
                { _, r -> r.number in listOf(1.0, 2.0) }
            )
        )
    }

    @Disabled("Capturing mutable locals is not properly supported in ArkIR")
    // Note: This test is disabled because ArkIR cannot properly represent the mutation
    // of a captured mutable local (`let` or `var`) inside a closure.
    // Due to this, `x += 100` instruction has no effect, and the result is 145 (120+20) instead of 225 (120+125).
    // A possible solution would be to represent LHS in `x += 100` with `ClosureFieldRef` instead of `Local`.
    @Test
    fun `test call closure capturing mutable local`() {
        val method = getMethod("callClosureCapturingMutableLocal")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r eq 245 },
            )
        )
    }

    @Disabled("Capturing mutable locals is not properly supported in ArkIR")
    // Note: See above.
    // This test incorrectly produces 20 instead of 120.
    @Test
    fun `test call closure mutating captured local`() {
        val method = getMethod("callClosureMutatingCapturedLocal")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r eq 120 },
            )
        )
    }

    private fun fib(n: Double): Double {
        if (n.isNaN()) return 0.0
        if (n < 0) return -1.0
        if (n > 10) return -100.0
        if (n == 0.0) return 1.0
        if (n == 1.0) return 1.0
        return fib(n - 1.0) + fib(n - 2.0)
    }
}
