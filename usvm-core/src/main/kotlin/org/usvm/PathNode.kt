package org.usvm

import org.usvm.algorithms.findLcaLinear
import org.usvm.merging.UMergeable
import java.util.NoSuchElementException

class PathNode<Statement> private constructor(
    val parent: PathNode<Statement>?,
    private val _statement: Statement?,
    depth: Int,
) : UMergeable<PathNode<Statement>, Unit> {
    var depth: Int = depth
        private set

    val statement: Statement get() = requireNotNull(_statement)

    operator fun plus(statement: Statement): PathNode<Statement> {
        return PathNode(this, statement, depth + 1)
    }

    val allStatements
        get(): Iterable<Statement> = Iterable {
            object : Iterator<Statement> {
                var cur = this@PathNode
                override fun hasNext(): Boolean = cur._statement != null

                override fun next(): Statement {
                    if (!hasNext()) {
                        throw NoSuchElementException()
                    }
                    val element = requireNotNull(cur._statement)
                    cur = requireNotNull(cur.parent)
                    return element
                }

            }
        }

    companion object {
        private val EMPTY = PathNode(parent = null, _statement = null, depth = 0)

        @Suppress("UNCHECKED_CAST")
        fun <Statement> root(): PathNode<Statement> = EMPTY as PathNode<Statement>
    }

    override fun mergeWith(other: PathNode<Statement>, by: Unit): PathNode<Statement>? {
        if (_statement != other._statement) {
            return null
        }
        val (lca, _, _) = findLcaLinear(this, other, { it.parent!! }, { it.depth }, { it.statement })
        return lca + statement
    }
}
