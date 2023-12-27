package org.usvm.fuzzer.position

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.JcType

class PositionTrie {

    val roots = mutableListOf<Root>()

    fun addRoot(jcType: JcType): Root {
        roots.find { it.jcType == jcType }?.let { return it }
        val root = Root(mutableListOf(), jcType)
        roots.add(root)
        return root
    }

    fun addPosition(rootClassType: JcType, fieldChain: List<JcField>) {
        var parentNode: Node = roots.find { it.jcType == rootClassType } ?: error("Cant find root")
        for (field in fieldChain) {
            val fieldFromTree = parentNode.children.find { it.field == field }
            if (fieldFromTree == null) {
                val newNode = Position(parentNode, mutableListOf(), field, 0.0)
                parentNode.children.add(newNode)
                parentNode = newNode
            } else {
                parentNode = fieldFromTree
            }
        }
    }

    sealed interface Node {
        val children: MutableList<Position>
    }

    data class Root(
        override val children: MutableList<Position>,
        val jcType: JcType
    ) : Node {
        override fun toString(): String {
            return "${this.hashCode()} Root"
        }
    }

    data class Position(
        var parent: Node,
        override val children: MutableList<Position>,
        val field: JcField,
        var score: Double
    ) : Node {
        override fun toString(): String {
            return "${this.hashCode()} Position"
        }
    }

    //inner class Position(field: JcField, var score: Double)


}