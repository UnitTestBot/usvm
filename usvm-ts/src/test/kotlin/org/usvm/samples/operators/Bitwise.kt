package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class Bitwise : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Bitwise.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `bitwise NOT`() {
        val method = getMethod("bitwiseNot")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // ~5 = -6
                (a eq 5) && (r eq -6)
            },
            { a, r ->
                // ~(-1) = 0
                (a eq -1) && (r eq 0)
            },
            { a, r ->
                // ~0 = -1
                (a eq 0) && (r eq -1)
            },
            invariants = arrayOf(
                // TODO: implement TS behaviour for bitwise NOT
                { _, r -> true }
            )
        )
    }

    @Test
    fun `bitwise AND`() {
        val method = getMethod("bitwiseAnd")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // 5 & 3 = 1
                (a eq 5) && (b eq 3) && (r eq 1)
            },
            { a, b, r ->
                // 15 & 7 = 7
                (a eq 15) && (b eq 7) && (r eq 7)
            },
            { a, b, r ->
                // 0 & b = 0
                (a eq 0) && (r eq 0)
            },
            invariants = arrayOf(
                // TODO: implement TS behaviour for bitwise AND
                { _, _, r -> true }
            )
        )
    }

    @Test
    fun `bitwise OR`() {
        val method = getMethod("bitwiseOr")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // 5 | 3 = 7
                (a eq 5) && (b eq 3) && (r eq 7)
            },
            { a, b, r ->
                // 0 | 0 = 0
                (a eq 0) && (b eq 0) && (r eq 0)
            },
            { a, b, r ->
                // 15 | 16 = 31
                (a eq 15) && (b eq 16) && (r eq 31)
            },
            invariants = arrayOf(
                // TODO: implement TS behaviour for bitwise OR
                { _, _, r -> true }
            )
        )
    }

    @Test
    fun `bitwise XOR`() {
        val method = getMethod("bitwiseXor")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // 5 ^ 3 = 6
                (a eq 5) && (b eq 3) && (r eq 6)
            },
            { a, b, r ->
                // 7 ^ 7 = 0
                (a eq 7) && (b eq 7) && (r eq 0)
            },
            { a, b, r ->
                // 0 ^ b = b
                (a eq 0) && (r == b)
            },
            invariants = arrayOf(
                // TODO: implement TS behaviour for bitwise XOR
                { _, _, r -> true }
            )
        )
    }

    @Test
    fun `left shift`() {
        val method = getMethod("leftShift")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // 5 << 1 = 10
                (a eq 5) && (b eq 1) && (r eq 10)
            },
            { a, b, r ->
                // 1 << 3 = 8
                (a eq 1) && (b eq 3) && (r eq 8)
            },
            { a, b, r ->
                // a << 0 = a
                (b eq 0) && (r == a)
            },
            invariants = arrayOf(
                // TODO: implement TS behaviour for left shift
                { _, _, r -> true }
            )
        )
    }

    @Test
    fun `right shift`() {
        val method = getMethod("rightShift")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // 10 >> 1 = 5
                (a eq 10) && (b eq 1) && (r eq 5)
            },
            { a, b, r ->
                // -8 >> 2 = -2
                (a eq -8) && (b eq 2) && (r eq -2)
            },
            { a, b, r ->
                // a >> 0 = a
                (b eq 0) && (r eq a)
            },
            invariants = arrayOf(
                // TODO: implement TS behaviour for right shift
                { _, _, r -> true }
            )
        )
    }

    @Test
    fun `unsigned right shift`() {
        val method = getMethod("unsignedRightShift")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // 10 >>> 1 = 5
                (a eq 10) && (b eq 1) && (r eq 5)
            },
            { a, b, r ->
                // -1 >>> 1 = 2147483647
                (a eq -1) && (b eq 1) && (r eq 2147483647)
            },
            { a, b, r ->
                // x >>> 0 = x
                (b eq 0) && (r eq a)
            },
            invariants = arrayOf(
                // TODO: implement TS behaviour for unsigned right shift
                { _, _, r -> true }
            )
        )
    }
}
