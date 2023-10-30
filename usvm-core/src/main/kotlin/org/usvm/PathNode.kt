package org.usvm

import org.usvm.algorithms.findLcaLinear
import org.usvm.merging.UMergeable

sealed interface PathSegment<Statement> {
    val statement: Statement

    data class Single<Statement>(
        override val statement: Statement,
    ) : PathSegment<Statement> {
        override fun toString(): String = "$statement"
    }

    data class Merged<Statement>(
        override val statement: Statement,
        val left: List<PathSegment<Statement>>,
        val right: List<PathSegment<Statement>>,
    ) : PathSegment<Statement> {
        override fun toString(): String = buildString {
            appendLine(statement)
            val targetSize = kotlin.math.max(left.size, right.size)
            val leftStrs = left.map { it.toString() } + List(targetSize - left.size) { "" }
            val rightStrs = right.map { it.toString() } + List(targetSize - right.size) { "" }
            val leftColumnnSize = (leftStrs.maxOfOrNull { it.length } ?: 0) + 8
            leftStrs.zip(rightStrs).forEach { (left, right) ->
                append(left.padEnd(leftColumnnSize))
                appendLine(right)
            }
        }
    }
}

class PathNode<Statement> private constructor(
    val parent: PathNode<Statement>?,
    private val _segment: PathSegment<Statement>?,
    depth: Int,
) : UMergeable<PathNode<Statement>, Unit> {
    var depth: Int = depth
        private set

    val statement: Statement get() = requireNotNull(_segment).statement

    operator fun plus(statement: Statement): PathNode<Statement> {
        return PathNode(this, PathSegment.Single(statement), depth + 1)
    }

    val allStatements
        get(): Iterable<Statement> = Iterable {
            object : Iterator<Statement> {
                var cur = this@PathNode
                override fun hasNext(): Boolean = cur._segment != null

                override fun next(): Statement {
                    if (!hasNext()) {
                        throw NoSuchElementException()
                    }
                    val element = requireNotNull(cur._segment)
                    cur = requireNotNull(cur.parent)
                    return element.statement
                }

            }
        }

    /**
     * Check if this [PathNode] can be merged with [other] path node.
     *
     * TODO: now the only supported case is:
     *  - statements are equal
     *
     * TODO: doesn't save the suffix paths into the result node
     *
     * @return the merged path node.
     */
    override fun mergeWith(other: PathNode<Statement>, by: Unit): PathNode<Statement>? {
        if (_segment != other._segment) {
            return null
        }
        val (lca, suffixLeft, suffixRight) = findLcaLinear(
            this,
            other,
            { it.parent!! },
            { it.depth },
            { it._segment!! }
        )

        val segment = PathSegment.Merged(statement, suffixLeft, suffixRight)

        return PathNode(lca, segment, lca.depth + 1)
    }

    companion object {
        private val EMPTY = PathNode<Nothing?>(parent = null, _segment = null, depth = 0)

        @Suppress("UNCHECKED_CAST")
        fun <Statement> root(): PathNode<Statement> = EMPTY as PathNode<Statement>
    }

    override fun toString(): String =
        buildString {
            appendLine(_segment)
            appendLine(parent)
        }
}
