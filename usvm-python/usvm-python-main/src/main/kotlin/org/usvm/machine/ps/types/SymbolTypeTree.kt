package org.usvm.machine.ps.types

import org.usvm.language.MpAssSubscriptMethod
import org.usvm.language.MpSubscriptMethod
import org.usvm.language.NbAddMethod
import org.usvm.language.NbBoolMethod
import org.usvm.language.NbIntMethod
import org.usvm.language.NbMatrixMultiplyMethod
import org.usvm.language.NbMultiplyMethod
import org.usvm.language.NbNegativeMethod
import org.usvm.language.NbPositiveMethod
import org.usvm.language.NbSubtractMethod
import org.usvm.language.SqLengthMethod
import org.usvm.language.TpCallMethod
import org.usvm.language.TpGetattro
import org.usvm.language.TpIterMethod
import org.usvm.language.TpRichcmpMethod
import org.usvm.language.TpSetattro
import org.usvm.machine.PyState
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.getConcreteStrIfDefined
import org.utpython.types.PythonAnyTypeDescription
import org.utpython.types.PythonCallableTypeDescription
import org.utpython.types.PythonTypeHintsStorage
import org.utpython.types.createBinaryProtocol
import org.utpython.types.createProtocolWithAttribute
import org.utpython.types.createPythonCallableType
import org.utpython.types.createUnaryProtocol
import org.utpython.types.general.FunctionTypeCreator
import org.utpython.types.general.UtType
import org.utpython.types.inference.TypeInferenceEdgeWithBound
import org.utpython.types.inference.TypeInferenceNode
import org.utpython.types.inference.addEdge
import org.utpython.types.pythonAnyType
import org.utpython.types.pythonDescription

class SymbolTypeTree(
    private val state: PyState,
    private val typeHintsStorage: PythonTypeHintsStorage,
    rootSymbol: UninterpretedSymbolicPythonObject,
    private val maxDepth: Int = 5,
) {
    private val root = SymbolTreeNode(rootSymbol)
    private fun generateSuccessors(node: SymbolTreeNode): List<SymbolTreeNode> =
        state.getMocksForSymbol(node.symbol).mapNotNull { (mockHeader, resultSymbol) ->
            val protocol =
                when (mockHeader.method) {
                    MpAssSubscriptMethod -> {
                        { returnType: UtType -> createBinaryProtocol("__setitem__", pythonAnyType, returnType) }
                    }
                    MpSubscriptMethod -> {
                        { returnType: UtType -> createBinaryProtocol("__getitem__", pythonAnyType, returnType) }
                    }
                    NbAddMethod -> {
                        { returnType: UtType -> createBinaryProtocol("__add__", pythonAnyType, returnType) }
                    }
                    NbSubtractMethod -> {
                        { returnType: UtType -> createBinaryProtocol("__sub__", pythonAnyType, returnType) }
                    }
                    NbBoolMethod -> {
                        { _: UtType -> createUnaryProtocol("__bool__", typeHintsStorage.pythonBool) }
                    }
                    NbIntMethod -> {
                        { _: UtType -> createUnaryProtocol("__int__", typeHintsStorage.pythonInt) }
                    }
                    NbNegativeMethod -> {
                        { returnType: UtType -> createUnaryProtocol("__neg__", returnType) }
                    }
                    NbPositiveMethod -> {
                        { returnType: UtType -> createUnaryProtocol("__pos__", returnType) }
                    }
                    NbMatrixMultiplyMethod -> {
                        { returnType: UtType -> createBinaryProtocol("__matmul__", pythonAnyType, returnType) }
                    }
                    NbMultiplyMethod -> {
                        { returnType: UtType -> createBinaryProtocol("__mul__", pythonAnyType, returnType) }
                    }
                    SqLengthMethod -> {
                        { _: UtType -> createUnaryProtocol("__len__", typeHintsStorage.pythonInt) }
                    }
                    TpIterMethod -> {
                        { returnType: UtType -> createUnaryProtocol("__iter__", returnType) }
                    }
                    TpGetattro -> {
                        val attribute = mockHeader.args[1].getConcreteStrIfDefined(state.preAllocatedObjects)
                            ?: return@mapNotNull null
                        { returnType: UtType -> createProtocolWithAttribute(attribute, returnType) }
                    }
                    TpSetattro -> {
                        val attribute = mockHeader.args[1].getConcreteStrIfDefined(state.preAllocatedObjects)
                            ?: return@mapNotNull null
                        { _: UtType -> createProtocolWithAttribute(attribute, pythonAnyType) }
                    }
                    is TpRichcmpMethod -> {
                        { returnType: UtType ->
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
                    is TpCallMethod -> {
                        { returnType: UtType ->
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
                }
            val originalHint = protocol(pythonAnyType)
            if (originalHint.pythonDescription() !is PythonAnyTypeDescription) {
                node.upperBounds.add(originalHint)
            }
            val newNode = SymbolTreeNode(resultSymbol)
            val edge = SymbolTreeEdge(newNode, node) { type -> listOf(protocol(type)) }
            addEdge(edge)
            newNode
        }

    private fun generateNodes(node: SymbolTreeNode, depth: Int) {
        if (depth >= maxDepth) {
            return
        }
        generateSuccessors(node).forEach {
            generateNodes(it, depth + 1)
        }
    }

    private fun propagateBounds() {
        dfs(root) { edge ->
            edge.from.upperBounds.forEach {
                val newBounds = edge.dependency(it).filter { type ->
                    type.pythonDescription() !is PythonAnyTypeDescription
                }
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

class SymbolTreeNode(val symbol: UninterpretedSymbolicPythonObject) : TypeInferenceNode {
    override val partialType: UtType = pythonAnyType
    override val ingoingEdges = mutableListOf<SymbolTreeEdge>()
    override val outgoingEdges = mutableListOf<SymbolTreeEdge>()
    val upperBounds = mutableListOf<UtType>()
}

class SymbolTreeEdge(
    override val from: SymbolTreeNode,
    override val to: SymbolTreeNode,
    override val dependency: (UtType) -> List<UtType>,
) : TypeInferenceEdgeWithBound {
    override val boundType: TypeInferenceEdgeWithBound.BoundType = TypeInferenceEdgeWithBound.BoundType.Upper
}
