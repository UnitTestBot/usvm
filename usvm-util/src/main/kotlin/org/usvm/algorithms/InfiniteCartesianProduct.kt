package org.usvm.algorithms

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

fun <T> listCartesianProduct(listOfSequences: List<Sequence<T>>): Sequence<List<T>> {
    return listOfSequences.foldRight(sequenceOf(persistentListOf())) { sequence, acc -> interleave(
        sequence {
            for (x in sequence) {
                yield(
                    sequence {
                        for (a in acc) {
                            yield(listOf(x) + a)
                        }
                    })
            }
        })
    }
}

fun <Key, Value> mapCartesianProduct(mapOfSequences: Map<Key, Sequence<Value>>): Sequence<Map<Key, Value>> {
    return mapOfSequences.entries.fold(sequenceOf(persistentMapOf())) { acc, entry -> interleave(
        sequence {
            for (x in entry.value) {
                yield(
                    sequence {
                        for (a in acc) {
                            yield(a.put(entry.key, x))
                        }
                    })
            }
        })
    }
}

internal fun <T> interleave(seqs: Sequence<Sequence<T>>): Sequence<T> = sequence {
    val iterators = seqs.map { it.iterator() }.iterator()
    val activeIterators = mutableListOf<Iterator<T>?>()

    do {
        val hasNextIterator = iterators.hasNext()
        if (hasNextIterator) {
            activeIterators.add(iterators.next())
        }

        var hasMore = hasNextIterator
        activeIterators.forEachIndexed { i, iterator ->
            if (iterator != null) {
                if (iterator.hasNext()) {
                    yield(iterator.next())
                    hasMore = true
                } else {
                    activeIterators[i] = null
                }
            }
        }

        if (activeIterators.isEmpty()) {
            break
        }
    } while (hasMore)
}

