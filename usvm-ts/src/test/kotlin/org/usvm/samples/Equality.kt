package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class Equality : TSMethodTestRunner() {
    override val scene: EtsScene = run {
        val name = "Equality.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    @Disabled("Unsupported array new")
    fun testTruthyTypes() {
        val method = getMethod("Equality", "truthyTypes")
        discoverProperties<TSObject.TSNumber>(
            method,
        )
    }

    @Test
    fun testEqBoolWithBool() {
        val method = getMethod("Equality", "eqBoolWithBool")
        discoverProperties<TSObject.TSBoolean, TSObject.TSNumber>(
            method,
            { a, r -> a.value && r.number == 1.0 },
            { a, r -> !a.value && r.number == -1.0 },
        )
    }

    @Test
    fun testEqNumberWithNumber() {
        val method = getMethod("Equality", "eqNumberWithNumber")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, r -> a.number == 1.1 && r.number == 1.0 },
            { a, r -> a.number.isNaN() && r.number == 2.0 },
            { a, r -> a.number != 1.1 && !a.number.isNaN() && r.number == 3.0 },
        )
    }

    @Test
    @Disabled("Unsupported string")
    fun testEqStringWithString() {
        val method = getMethod("Equality", "eqStringWithString")
        discoverProperties<TSObject.TSString, TSObject.TSNumber>(
            method,
            { a, r -> a.value == "123" && r.number == 1.0 },
            { a, r -> a.value != "123" && r.number == 2.0 },
        )
    }

    @Test
    @Disabled("Unsupported bigint")
    fun testEqBigintWithBigint() {
        val method = getMethod("Equality", "eqBigintWithBigint")
        discoverProperties<TSObject.BigInt, TSObject.TSNumber>(
            method,
            { a, r -> a.value == "42" && r.number == 1.0 },
            { a, r -> a.value == "9999999999999999999999999999999999999" && r.number == 2.0 },
            { a, r -> a.value != "9999999999999999999999999999999999999" && a.value != "42" && r.number == -1.0 },
        )
    }

    @Test
    @Disabled("Unsupported new construction")
    fun testEqObjectWithObject() {
        val method = getMethod("Equality", "eqObjectWithObject")
        discoverProperties<TSObject.TSClass, TSObject.TSNumber>(
            method,
            { a, r -> r.number == 1.0 },
            invariants = arrayOf(
                { _, r -> r.number != -1.0 },
                { _, r -> r.number != 2.0 },
            )
        )
    }

    @Test
    fun testEqWithItself() {
        val method = getMethod("Equality", "eqWithItself")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, r -> a.number.isNaN() && r.number == 1.0 },
            { a, r -> !a.number.isNaN() && r.number == 2.0 },
        )
    }
}