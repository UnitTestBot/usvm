package org.usvm.util

/**
 * Unsigned integer logarithm base 2. Zero evaluates to zero.
 */
fun log2(n: UInt): UInt {
    if (n == UInt.MAX_VALUE) {
        return 32u
    }

    if (n == 0u) {
        return 0u
    }

    var ret = 0u
    var m = n
    while (m > 1u) {
        m = m shr 1
        ret++
    }
    return ret
}
