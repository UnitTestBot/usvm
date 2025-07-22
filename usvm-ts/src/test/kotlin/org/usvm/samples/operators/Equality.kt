package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import org.usvm.util.neq

class Equality : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Equality.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test eqBoolWithBool`() {
        val method = getMethod(className, "eqBoolWithBool")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && a.value },
            { a, r -> (r eq 2) && !a.value },
            invariants = arrayOf(
                { _, r -> r neq -1 },
            )
        )
    }

    @Test
    fun `test eqNumberWithNumber`() {
        val method = getMethod(className, "eqNumberWithNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && a.isNaN() },
            { a, r -> (r eq 2) && (a eq 42) },
            { a, r -> (r eq 3) && !(a.isNaN() || (a eq 42)) },
        )
    }

    @Test
    @Disabled("Unsupported string")
    fun `test eqStringWithString`() {
        val method = getMethod(className, "eqStringWithString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && (a.value == "123") },
            { a, r -> (r eq 2) && (a.value != "123") },
        )
    }

    @Test
    @Disabled("Unsupported bigint")
    fun `test eqBigintWithBigint`() {
        val method = getMethod(className, "eqBigintWithBigint")
        discoverProperties<TsTestValue.TsBigInt, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && (a.value == "42") },
            { a, r -> (r eq 2) && (a.value != "42") },
        )
    }

    @Disabled("Could not resolve unique constructor")
    @Test
    fun `test eqObjectWithObject`() {
        val method = getMethod(className, "eqObjectWithObject")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { _, r -> r eq 1 },
            invariants = arrayOf(
                { _, r -> r neq -1 },
            )
        )
    }

    @Disabled("Argument unexpectedly becomes TsUndefined")
    @Test
    fun `test eqArrayWithArray`() {
        val method = getMethod(className, "eqArrayWithArray")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsNumber>(
            method,
            { _, r -> r eq 1 },
            invariants = arrayOf(
                { _, r -> r neq -1 },
            )
        )
    }

    @Disabled("Unsupported loose equality for Object and Boolean")
    @Test
    fun `test eqArrayWithBoolean`() {
        val method = getMethod(className, "eqArrayWithBoolean")
        discoverProperties<TsTestValue.TsNumber>(
            method,
            { r -> r eq 0 },
            invariants = arrayOf(
                { r -> r neq -1 },
            )
        )
    }
}
