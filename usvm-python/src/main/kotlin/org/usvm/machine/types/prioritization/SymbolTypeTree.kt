package org.usvm.machine.types.prioritization

import org.usvm.language.*
import org.usvm.machine.PythonExecutionState
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.utbot.python.newtyping.PythonTypeHintsStorage
import org.utbot.python.newtyping.createBinaryProtocol
import org.utbot.python.newtyping.createUnaryProtocol
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.inference.TypeInferenceEdgeWithBound
import org.utbot.python.newtyping.inference.TypeInferenceNode
import org.utbot.python.newtyping.inference.addEdge
import org.utbot.python.newtyping.pythonAnyType

class SymbolTypeTree(
    private val state: PythonExecutionState,
    private val typeHintsStorage: PythonTypeHintsStorage,
    rootSymbol: UninterpretedSymbolicPythonObject
) {
    private val root = SymbolTreeNode(rootSymbol)
    private fun generateSuccessors(node: SymbolTreeNode): List<SymbolTreeNode> =
        state.getMocksForSymbol(node.symbol).map { (mockHeader, resultSymbol) ->
            val protocol = { returnType: UtType ->
                when (mockHeader.method) {
                    MpAssSubscriptMethod ->
                        createBinaryProtocol("__setitem__", pythonAnyType, returnType)
                    MpSubscriptMethod ->
                        createBinaryProtocol("__getitem__", pythonAnyType, returnType)
                    NbAddMethod ->
                        createBinaryProtocol("__add__", pythonAnyType, returnType)
                    NbSubtractMethod ->
                        createBinaryProtocol("__sub__", pythonAnyType, returnType)
                    NbBoolMethod ->
                        createUnaryProtocol("__bool__", typeHintsStorage.pythonBool)
                    NbIntMethod ->
                        createUnaryProtocol("__int__", typeHintsStorage.pythonInt)
                    NbMatrixMultiplyMethod ->
                        createBinaryProtocol("__matmul__", pythonAnyType, returnType)
                    NbMultiplyMethod ->
                        createBinaryProtocol("__mul__", pythonAnyType, returnType)
                    SqLengthMethod ->
                        createUnaryProtocol("__len__", typeHintsStorage.pythonInt)
                    TpIterMethod ->
                        createUnaryProtocol("__iter__", returnType)
                    is TpRichcmpMethod -> {
                        when (mockHeader.method.op) {
                            ConcretePythonInterpreter.pyEQ ->
                                createBinaryProtocol("__eq__", pythonAnyType, returnType)
                            ConcretePythonInterpreter.pyNE ->
                                createBinaryProtocol("__ne__", pythonAnyType, returnType)
                            ConcretePythonInterpreter.pyLT ->
                                createBinaryProtocol("__lt__", pythonAnyType, returnType)
                            ConcretePythonInterpreter.pyLE ->
                                createBinaryProtocol("__le__", pythonAnyType, returnType)
                            ConcretePythonInterpreter.pyGT ->
                                createBinaryProtocol("__gt__", pythonAnyType, returnType)
                            ConcretePythonInterpreter.pyGE ->
                                createBinaryProtocol("__ge__", pythonAnyType, returnType)
                            else -> error("Wrong OP in TpRichcmpMethod")
                        }
                    }
                }
            }
            node.upperBounds.add(protocol(pythonAnyType))
            val newNode = SymbolTreeNode(resultSymbol)
            val edge = SymbolTreeEdge(newNode, node) { type -> listOf(protocol(type)) }
            addEdge(edge)
            newNode
        }

    private fun generateNodes(node: SymbolTreeNode) {
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

    private fun dfs(node: SymbolTreeNode, onEdge: (SymbolTreeEdge) -> Unit) {
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

class SymbolTreeNode(val symbol: UninterpretedSymbolicPythonObject): TypeInferenceNode {
    override val partialType: UtType = pythonAnyType
    override val ingoingEdges = mutableListOf<SymbolTreeEdge>()
    override val outgoingEdges = mutableListOf<SymbolTreeEdge>()
    val upperBounds = mutableListOf<UtType>()
}

class SymbolTreeEdge(
    override val from: SymbolTreeNode,
    override val to: SymbolTreeNode,
    override val dependency: (UtType) -> List<UtType>
): TypeInferenceEdgeWithBound {
    override val boundType: TypeInferenceEdgeWithBound.BoundType = TypeInferenceEdgeWithBound.BoundType.Upper
}