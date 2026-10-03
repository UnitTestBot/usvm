package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class TypeOf : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/TypeOf.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test typeOfString`() {
        val method = getMethod("typeOfString")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "string" },
        )
    }

    @Test
    fun `test typeOfNumber`() {
        val method = getMethod("typeOfNumber")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "number" },
        )
    }

    @Test
    fun `test typeOfBoolean`() {
        val method = getMethod("typeOfBoolean")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "boolean" },
        )
    }

    @Test
    fun `test typeOfUndefined`() {
        val method = getMethod("typeOfUndefined")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "undefined" },
        )
    }

    @Test
    fun `test typeOfNull`() {
        val method = getMethod("typeOfNull")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfObject`() {
        val method = getMethod("typeOfObject")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfArray`() {
        val method = getMethod("typeOfArray")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfFunction`() {
        val method = getMethod("typeOfFunction")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "function" },
        )
    }

    @Test
    fun `test typeOfArrow`() {
        val method = getMethod("typeOfArrow")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "function" },
            invariants = arrayOf({ r -> r.value == "function" }),
        )
    }

    @Test
    fun `test typeOfFunctionExpression`() {
        val method = getMethod("typeOfFunctionExpression")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "function" },
            invariants = arrayOf({ r -> r.value == "function" }),
        )
    }

    @Test
    fun `test typeOfStoredArrow`() {
        val method = getMethod("typeOfStoredArrow")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "function" },
            invariants = arrayOf({ _, r -> r.value == "function" }),
        )
    }

    @Test
    fun `test typeOfCallableInObject`() {
        val method = getMethod("typeOfCallableInObject")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "function" },
            invariants = arrayOf({ r -> r.value == "function" }),
        )
    }

    @Test
    fun `test typeOfNamedFunction`() {
        val method = getMethod("typeOfNamedFunction")

        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "function" },
            invariants = arrayOf({ r -> r.value == "function" }),
        )
    }

    @Test
    fun `test typeOfConditionalCallable`() {
        val method = getMethod("typeOfConditionalCallable")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method,
            { flag, r -> flag.value && r.value == "function" },
            { flag, r -> !flag.value && r.value == "object" },
            invariants = arrayOf({ flag, r -> r.value == if (flag.value) "function" else "object" }),
        )
    }

    @Test
    fun `test typeOfInputString`() {
        val method = getMethod("typeOfInputString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "string" },
        )
    }

    @Test
    fun `test typeOfInputNumber`() {
        val method = getMethod("typeOfInputNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "number" },
        )
    }

    @Test
    fun `test typeOfInputBoolean`() {
        val method = getMethod("typeOfInputBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "boolean" },
        )
    }

    @Test
    fun `test typeOfInputUndefined`() {
        val method = getMethod("typeOfInputUndefined")
        discoverProperties<TsTestValue.TsUndefined, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "undefined" },
        )
    }

    @Test
    fun `test typeOfInputNull`() {
        val method = getMethod("typeOfInputNull")
        discoverProperties<TsTestValue.TsNull, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfInputObject`() {
        val method = getMethod("typeOfInputObject")
        discoverProperties<TsTestValue, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfInputArray`() {
        val method = getMethod("typeOfInputArray")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "object" },
        )
    }

    @Disabled("Function-typed parameters are unsupported during initial-state construction")
    @Test
    fun `test typeOfInputFunction`() {
        val method = getMethod("typeOfInputFunction")
        discoverProperties<TsTestValue, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "function" },
        )
    }
}
