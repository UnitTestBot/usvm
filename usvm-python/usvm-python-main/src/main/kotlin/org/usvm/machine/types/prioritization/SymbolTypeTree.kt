package org.usvm.machine.types.prioritization

import org.usvm.language.*
import org.usvm.machine.PythonExecutionState
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.getConcreteStrIfDefined
import org.utbot.python.newtyping.*
import org.utbot.python.newtyping.general.FunctionTypeCreator
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.inference.TypeInferenceEdgeWithBound
import org.utbot.python.newtyping.inference.TypeInferenceNode
import org.utbot.python.newtyping.inference.addEdge

class SymbolTypeTree(
    private val state: PythonExecutionState,
    private val typeHintsStorage: PythonTypeHintsStorage,
    rootSymbol: UninterpretedSymbolicPythonObject,
    private val maxDepth: Int = 5
) {
    private val root = SymbolTreeNode(rootSymbol)
    private fun generateSuccessors(node: SymbolTreeNode): List<SymbolTreeNode> =
        state.getMocksForSymbol(node.symbol).mapNotNull { (mockHeader, resultSymbol) ->
            val protocol =
                 when (mockHeader.method) {
                    MpAssSubscriptMethod ->
                        { returnType: UtType -> createBinaryProtocol("__setitem__", pythonAnyType, returnType) }
                    MpSubscriptMethod ->
                        { returnType: UtType -> createBinaryProtocol("__getitem__", pythonAnyType, returnType) }
                    NbAddMethod ->
                        { returnType: UtType -> createBinaryProtocol("__add__", pythonAnyType, returnType) }
                    NbSubtractMethod ->
                        { returnType: UtType -> createBinaryProtocol("__sub__", pythonAnyType, returnType) }
                    NbBoolMethod ->
                        { _: UtType -> createUnaryProtocol("__bool__", typeHintsStorage.pythonBool) }
                    NbIntMethod ->
                        { _: UtType -> createUnaryProtocol("__int__", typeHintsStorage.pythonInt) }
                    NbMatrixMultiplyMethod ->
                        { returnType: UtType -> createBinaryProtocol("__matmul__", pythonAnyType, returnType) }
                    NbMultiplyMethod ->
                        { returnType: UtType -> createBinaryProtocol("__mul__", pythonAnyType, returnType) }
                    SqLengthMethod ->
                        { _: UtType -> createUnaryProtocol("__len__", typeHintsStorage.pythonInt) }
                    TpIterMethod ->
                        { returnType: UtType -> createUnaryProtocol("__iter__", returnType) }
                    TpGetattro -> {
                        val attribute = mockHeader.args[1].getConcreteStrIfDefined(state.preAllocatedObjects)
                            ?: return@mapNotNull null
                        { returnType: UtType -> createUnaryProtocol(attribute, returnType) }
                    }
                    TpSetattro -> {
                        val attribute = mockHeader.args[1].getConcreteStrIfDefined(state.preAllocatedObjects)
                            ?: return@mapNotNull null
                        { _: UtType -> createUnaryProtocol(attribute, pythonAnyType) }
                    }
                    is TpRichcmpMethod -> { returnType: UtType ->
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
                    is TpCallMethod -> { returnType: UtType ->
                        createProtocolWithAttribute(
                            "__call__",
                                createPythonCallableType(
                                    1,
                                    listOf(PythonCallableTypeDescription.ArgKind.ARG_STAR),
                                    listOf(null)
                                ) {
                                    FunctionTypeCreator.InitializationData(
                                        listOf(pythonAnyType),
                                        returnType
                                    )
                                }
                            )
                    }
                }
            node.upperBounds.add(protocol(pythonAnyType))
            val newNode = SymbolTreeNode(resultSymbol)
            val edge = SymbolTreeEdge(newNode, node) { type -> listOf(protocol(type)) }
            addEdge(edge)
            newNode
        }

    private fun generateNodes(node: SymbolTreeNode, depth: Int) {
        if (depth >= maxDepth)
            return
        generateSuccessors(node).forEach {
            generateNodes(it, depth + 1)
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
        generateNodes(root, 0)
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