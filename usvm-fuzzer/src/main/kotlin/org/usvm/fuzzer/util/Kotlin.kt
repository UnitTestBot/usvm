package org.usvm.fuzzer.util

import java.util.*
import kotlin.collections.HashSet
import kotlin.math.roundToInt
import kotlin.random.Random

fun Random.getTrueWithProb(prob: Int) = nextInt(101) in 0..prob

fun <T> Iterable<T>.toIdentityHashSet(): MutableSet<T> {
    val collectionSize = if (this is Collection<*>) (this.size * 1.25).roundToInt() else 32
    return toCollection(Collections.newSetFromMap(IdentityHashMap(collectionSize)))
}
