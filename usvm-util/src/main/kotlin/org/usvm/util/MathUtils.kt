package org.usvm.util

/**
 * Unsigned integer logarithm base 2 (more effective version than floor(log2(n.toDouble()))).
 * Zero evaluates to zero, UInt.MAX_VALUE evaluates to 32u.
 */
fun log2(n: UInt): UInt {
    if (n == UInt.MAX_VALUE) {
        return 32u
    }

    if (n == 0u) {
        return 0u
    }

    return 31u - Integer.numberOfLeadingZeros(n.toInt()).toUInt()
}
