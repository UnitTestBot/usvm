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
        val method = getMethod("eqBoolWithBool")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && a.value },
            { a, r -> (r eq 2) && !a.value },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `test eqNumberWithNumber`() {
        val method = getMethod("eqNumberWithNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && a.isNaN() },
            { a, r -> (r eq 2) && (a eq 42) },
            { a, r -> (r eq 3) && !a.isNaN() && !(a eq 42) },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `test eqNumberWithBool`() {
        val method = getMethod("eqNumberWithBool")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && (a eq 1) },
            { a, r -> (r eq 2) && (a eq 0) },
            { a, r -> (r eq 3) && (a neq 1) && (a neq 0) },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `test eqBoolWithNumber`() {
        val method = getMethod("eqBoolWithNumber")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && !a.value },
            { a, r -> (r eq 2) && a.value },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    @Disabled("Unsupported string")
    fun `test eqStringWithString`() {
        val method = getMethod("eqStringWithString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && (a.value == "123") },
            { a, r -> (r eq 2) && (a.value != "123") },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    @Disabled("Unsupported bigint")
    fun `test eqBigintWithBigint`() {
        val method = getMethod("eqBigintWithBigint")
        discoverProperties<TsTestValue.TsBigInt, TsTestValue.TsNumber>(
            method,
            { a, r -> (r eq 1) && (a.value == "42") },
            { a, r -> (r eq 2) && (a.value != "42") },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `test eqObjectWithObject`() {
        val method = getMethod("eqObjectWithObject")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method,
            { _, r -> r eq 1 },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Disabled("Argument unexpectedly becomes TsUndefined")
    @Test
    fun `test eqArrayWithArray`() {
        val method = getMethod("eqArrayWithArray")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsNumber>(
            method,
            { _, r -> r eq 1 },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Disabled("Unsupported loose equality for Object and Boolean")
    @Test
    fun `test eqArrayWithBoolean`() {
        val method = getMethod("eqArrayWithBoolean")
        discoverProperties<TsTestValue.TsNumber>(
            method,
            { r -> r eq 0 },
            invariants = arrayOf(
                { r -> r neq -1 },
            )
        )
    }
}
