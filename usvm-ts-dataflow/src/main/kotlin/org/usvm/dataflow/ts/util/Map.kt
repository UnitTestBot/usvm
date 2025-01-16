package org.usvm.dataflow.ts.util

import org.usvm.dataflow.ts.infer.AccessPathBase

fun <K, V> Iterable<Map.Entry<K, V>>.toMap(): Map<K, V> {
    return associate { it.toPair() }
}

inline fun <K, V, R : Comparable<R>> Map<K, V>.sortedBy(
    crossinline selector: (Map.Entry<K, V>) -> R,
): Map<K, V> {
    return entries.sortedBy(selector).toMap()
}

fun <K, V> Map<K, V>.sortedWith(
    comparator: Comparator<in Map.Entry<K, V>>,
): Map<K, V> {
    return entries.sortedWith(comparator).toMap()
}

fun <V> Map<AccessPathBase, V>.sortedByBase(): Map<AccessPathBase, V> =
    sortedWith(
        compareBy<Map.Entry<AccessPathBase, V>> {
            when (val key = it.key) {
                is AccessPathBase.This -> -1
                is AccessPathBase.Arg -> key.index
                is AccessPathBase.Local -> 1_000_000 + (key.tryGetOrdering() ?: -1)
                else -> Int.MAX_VALUE
            }
        }.thenBy {
            it.key.toString()
        }
    )
