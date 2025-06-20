package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class Strings : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test typeOfString`() {
        val method = getMethod(className, "typeOfString")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "string" },
        )
    }

    @Test
    fun `test typeOfNumber`() {
        val method = getMethod(className, "typeOfNumber")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "number" },
        )
    }

    @Test
    fun `test typeOfBoolean`() {
        val method = getMethod(className, "typeOfBoolean")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "boolean" },
        )
    }

    @Test
    fun `test typeOfUndefined`() {
        val method = getMethod(className, "typeOfUndefined")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "undefined" },
        )
    }

    @Test
    fun `test typeOfNull`() {
        val method = getMethod(className, "typeOfNull")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfObject`() {
        val method = getMethod(className, "typeOfObject")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfArray`() {
        val method = getMethod(className, "typeOfArray")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "object" },
        )
    }

    @Disabled("Functions are not supported yet")
    @Test
    fun `test typeOfFunction`() {
        val method = getMethod(className, "typeOfFunction")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r.value == "function" },
        )
    }

    @Test
    fun `test typeOfInputString`() {
        val method = getMethod(className, "typeOfInputString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "string" },
        )
    }

    @Test
    fun `test typeOfInputNumber`() {
        val method = getMethod(className, "typeOfInputNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "number" },
        )
    }

    @Test
    fun `test typeOfInputBoolean`() {
        val method = getMethod(className, "typeOfInputBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "boolean" },
        )
    }

    @Test
    fun `test typeOfInputUndefined`() {
        val method = getMethod(className, "typeOfInputUndefined")
        discoverProperties<TsTestValue.TsUndefined, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "undefined" },
        )
    }

    @Test
    fun `test typeOfInputNull`() {
        val method = getMethod(className, "typeOfInputNull")
        discoverProperties<TsTestValue.TsNull, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfInputObject`() {
        val method = getMethod(className, "typeOfInputObject")
        discoverProperties<TsTestValue, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "object" },
        )
    }

    @Test
    fun `test typeOfInputArray`() {
        val method = getMethod(className, "typeOfInputArray")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "object" },
        )
    }

    @Disabled("Functions are not supported yet")
    @Test
    fun `test typeOfInputFunction`() {
        val method = getMethod(className, "typeOfInputFunction")
        discoverProperties<TsTestValue, TsTestValue.TsString>(
            method = method,
            { _, r -> r.value == "function" },
        )
    }
}
