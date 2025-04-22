package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class Equality : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test eqBoolWithBool`() {
        val method = getMethod(className, "eqBoolWithBool")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, r -> a.value && r.number == 1.0 },
            { a, r -> !a.value && r.number == 2.0 },
            invariants = arrayOf(
                { _, r -> r.number != -1.0 },
            )
        )
    }

    @Test
    fun `test eqNumberWithNumber`() {
        val method = getMethod(className, "eqNumberWithNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> a.number.isNaN() && (r.number == 1.0) },
            { a, r -> (a.number == 42.0) && (r.number == 2.0) },
            { a, r -> !(a.number.isNaN() || a.number == 42.0) && (r.number == 3.0) },
        )
    }

    @Test
    @Disabled("Unsupported string")
    fun `test eqStringWithString`() {
        val method = getMethod(className, "eqStringWithString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method,
            { a, r -> (a.value == "123") && (r.number == 1.0) },
            { a, r -> (a.value != "123") && (r.number == 2.0) },
        )
    }

    @Test
    @Disabled("Unsupported bigint")
    fun `test eqBigintWithBigint`() {
        val method = getMethod(className, "eqBigintWithBigint")
        discoverProperties<TsTestValue.TsBigInt, TsTestValue.TsNumber>(
            method,
            { a, r -> (a.value == "42") && (r.number == 1.0) },
            { a, r -> (a.value != "42") && (r.number == 2.0) },
        )
    }

    @Disabled("Could not resolve unique constructor")
    @Test
    fun `test eqObjectWithObject`() {
        val method = getMethod(className, "eqObjectWithObject")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { a, r -> r.number == 1.0 },
            invariants = arrayOf(
                { _, r -> r.number != -1.0 },
            )
        )
    }

    @Disabled("Argument unexpectedly becomes TsUndefined")
    @Test
    fun `test eqArrayWithArray`() {
        val method = getMethod(className, "eqArrayWithArray")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsNumber>(
            method,
            { a, r -> r.number == 1.0 },
            invariants = arrayOf(
                { _, r -> r.number != -1.0 },
            )
        )
    }

    @Disabled("Unsupported loose equality for Object and Boolean")
    @Test
    fun `test eqArrayWithBoolean`() {
        val method = getMethod(className, "eqArrayWithBoolean")
        discoverProperties<TsTestValue.TsNumber>(
            method,
            { r -> r.number == 0.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 },
            )
        )
    }
}
