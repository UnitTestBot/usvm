package org.usvm.samples.numbers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


// example from Apache common-numbers
internal class ArithmeticUtilsTest : JavaMethodTestRunner() {
    @Test
    fun testPow() {
        checkDiscoveredProperties(
            ArithmeticUtils::pow,
            ignoreNumberOfAnalysisResults,
            { _, _, e, _ -> e < 0 }, // IllegalArgumentException
            { _, k, e, r -> k == 0 && e == 0 && r == 1 },
            { _, k, e, r -> k == 0 && e != 0 && r == 0 },
            { _, k, _, r -> k == 1 && r == 1 },
            { _, k, e, r -> k == -1 && e and 1 == 0 && r == 1 },
            { _, k, e, r -> k == -1 && e and 1 != 0 && r == -1 },
            { _, _, e, _ -> e >= 31 }, // ArithmeticException
            { _, k, e, r -> k !in -1..1 && e in 0..30 && r == pow(k, e) },

            // And 2 additional branches here with ArithmeticException reg integer overflow
            { _, k, e, _ -> k !in -1..1 && e in 0..30 },
        )
    }
}

fun pow(k: Int, e: Int): Int {
    var exp = e
    var result = 1
    var k2p = k
    while (true) {
        if (exp and 0x1 != 0) {
            result = Math.multiplyExact(result, k2p)
        }
        exp = exp shr 1
        if (exp == 0) {
            break
        }
        k2p = Math.multiplyExact(k2p, k2p)
    }
    return result
}