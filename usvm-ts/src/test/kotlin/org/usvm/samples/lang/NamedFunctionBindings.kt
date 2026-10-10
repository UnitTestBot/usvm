package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

class NamedFunctionBindings : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene(resourcePath = "/samples/lang/NamedFunctionBindings.ts")

    @Test
    fun `function declaration is hoisted before the source call`() {
        assertResult(methodName = "hoistedCall", expected = 3)
    }

    @Test
    fun `named function and alias have stable identity`() {
        val method = getMethod(methodName = "identity")

        discoverProperties<TsTestValue.TsBoolean>(
            method = method,
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `alias retains its function while direct calls read the replacement`() {
        assertResult(methodName = "aliasAfterReassignment", expected = 42)
    }

    @Test
    fun `call selects the named function before an argument reassigns it`() {
        assertResult(methodName = "selectedBeforeArguments", expected = 312)
    }

    @Test
    fun `parameter and block bindings shadow the scope function`() {
        assertResult(methodName = "shadows", expected = 10)
    }

    @Test
    fun `named function aliases retain live module state and nested captures`() {
        assertResult(methodName = "liveStateAndNestedClosure", expected = 13)
    }

    @Test
    fun `repeated calls update the same captured environment`() {
        assertResult(methodName = "repeatedCapturedUpdates", expected = 1314)
    }

    @Test
    fun `unknown captured value retains its runtime type after assignment`() {
        val method = getMethod(methodName = "unknownCapturedValue")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { result -> result.value == "number" },
            invariants = arrayOf({ result -> result.value == "number" }),
        )
    }

    @Test
    fun `unknown captured boolean retains its runtime type after assignment`() {
        val method = getMethod(methodName = "unknownCapturedBoolean")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { result -> result.value == "boolean" },
            invariants = arrayOf({ result -> result.value == "boolean" }),
        )
    }

    @Test
    fun `unknown captured function retains its runtime type after assignment`() {
        val method = getMethod(methodName = "unknownCapturedCallable")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { result -> result.value == "function" },
            invariants = arrayOf({ result -> result.value == "function" }),
        )
    }

    @Test
    fun `typeof preserves both runtime kinds of an unknown capture`() {
        val method = getMethod(methodName = "unknownCapturedChoice")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method,
            { flag, result -> flag.value && result.value == "number" },
            { flag, result -> !flag.value && result.value == "boolean" },
            invariants = arrayOf({ flag, result ->
                result.value == if (flag.value) "number" else "boolean"
            }),
        )
    }

    @Test
    fun `overload value resolves the implementation body`() {
        assertResult(methodName = "overloadImplementation", expected = 2)
    }

    private fun assertResult(methodName: String, expected: Int) {
        val method = getMethod(methodName = methodName)

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq expected },
            invariants = arrayOf({ result -> result eq expected }),
        )
    }
}
