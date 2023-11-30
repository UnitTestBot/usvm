package org.usvm.types

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.usvm.algorithms.cached
import java.util.PriorityQueue

class ScorerExtension<Result : Comparable<Result>>(
    val cp: JcClasspath,
    val key: Any,
) {
    private val scorer by lazy {
        @Suppress("UNCHECKED_CAST")
        cp.db.features.first { it is ClassScorer<*> && it.key == key } as ClassScorer<Result>
    }

    fun getScore(jcClass: JcClassOrInterface): Result? =
        scorer.getScore(jcClass.declaration.location, jcClass.name)

    val allClassesSorted: Sequence<JcClassOrInterface> by lazy {
        data class Node<Result : Comparable<Result>>(
            val result: Result,
            val className: String,
            val other: Iterator<Pair<Result, String>>,
        ) : Comparable<Node<Result>> {
            override fun compareTo(other: Node<Result>): Int = -result.compareTo(other.result)
        }

        sequence {
            val queue = PriorityQueue<Node<Result>>()

            fun advance(iterator: Iterator<Pair<Result, String>>) {
                if (!iterator.hasNext()) {
                    return
                }
                val (result, className) = iterator.next()
                queue.add(Node(result, className, iterator))
            }

            for (location in cp.registeredLocations) {
                val iterator = scorer.sortedClasses(location).iterator()
                advance(iterator)
            }

            while (queue.isNotEmpty()) {
                val top = queue.poll()
                val (_, className, iterator) = top
                yield(cp.findClass(className))
                advance(iterator)
            }
        }.cached()
    }
}
