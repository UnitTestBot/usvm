package org.usvm.machine.types.prioritization

import org.usvm.language.*
import org.usvm.machine.PythonExecutionState
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.inference.TypeInferenceEdgeWithBound
import org.utbot.python.newtyping.inference.TypeInferenceNode
import org.utbot.python.newtyping.pythonAnyType

class TypeGraph(
    private val state: PythonExecutionState,
    rootSymbol: SymbolForCPython
) {
    private val root = TypeGraphNode(rootSymbol)
    private fun generateSuccessors(node: TypeGraphNode): List<TypeGraphNode> {
        state.getMocksForSymbol(node.symbol).forEach { (mockHeader, _ /*resultSymbol*/) ->
            // val newNode = TypeGraphNode(resultSymbol)
            when (mockHeader.method) {
                MpAssSubscriptMethod -> TODO()
                MpSubscriptMethod -> TODO()
                NbAddMethod -> TODO()
                NbBoolMethod -> TODO()
                NbIntMethod -> TODO()
                NbMatrixMultiplyMethod -> TODO()
                NbMultiplyMethod -> TODO()
                SqLengthMethod -> TODO()
                TpIterMethod -> TODO()
                is TpRichcmpMethod -> TODO()
            }
        }
        TODO()
    }

    private fun generateNodes(node: TypeGraphNode) {
        generateSuccessors(node).forEach {
            generateNodes(it)
        }
    }

    private fun propagateBounds() {
        dfs(root) { edge ->
            edge.from.upperBounds.forEach {
                val newBounds = edge.dependency(it)
                edge.to.upperBounds += newBounds
            }
        }
    }

    private fun dfs(node: TypeGraphNode, onEdge: (TypeGraphEdge) -> Unit) {
        node.ingoingEdges.forEach { edge ->
            dfs(edge.from, onEdge)
            onEdge(edge)
        }
    }

    val boundsForRoot: List<UtType>
        get() = root.upperBounds

    init {
        generateNodes(root)
        propagateBounds()
    }
}

class TypeGraphNode(val symbol: SymbolForCPython): TypeInferenceNode {
    override val partialType: UtType = pythonAnyType
    override val ingoingEdges = mutableListOf<TypeGraphEdge>()
    override val outgoingEdges = mutableListOf<TypeGraphEdge>()
    val upperBounds = mutableListOf<UtType>()
}

class TypeGraphEdge(
    override val from: TypeGraphNode,
    override val to: TypeGraphNode,
    override val dependency: (UtType) -> List<UtType>
): TypeInferenceEdgeWithBound {
    override val boundType: TypeInferenceEdgeWithBound.BoundType = TypeInferenceEdgeWithBound.BoundType.Upper
}