package org.usvm.test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.usvm.util.log2
import kotlin.math.floor
import kotlin.test.assertEquals

class MathUtilsTests {
    @ParameterizedTest
    @ValueSource(strings = ["0", "1", "2", "3", "4", "64", "100", "2048", "11111111", "4294967294", "4294967295"])
    fun log2Test(nStr: String) {
        val n = nStr.toUInt()

        if (n == UInt.MAX_VALUE) {
            assertEquals(32u, log2(n))
            return
        }

        if (n == 0u) {
            assertEquals(0u, log2(n))
            return
        }

        assertEquals(floor(kotlin.math.log2(nStr.toDouble())).toInt(), log2(n).toInt())
    }
}
