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
import org.usvm.language.SqConcatMethod
import org.usvm.language.SqLengthMethod
import org.usvm.language.TpCallMethod
import org.usvm.language.TpGetattro
import org.usvm.language.TpIterMethod
import org.usvm.language.TpRichcmpMethod
import org.usvm.language.TpSetattro
import org.usvm.machine.PyState
import org.usvm.machine.getMocksForSymbol
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.getConcreteStrIfDefined
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.utpython.types.PythonAnyTypeDescription
import org.utpython.types.PythonCallableTypeDescription
import org.utpython.types.PythonCompositeTypeDescription
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
    private val typeSystem: PythonTypeSystemWithMypyInfo,
    rootSymbol: UninterpretedSymbolicPythonObject,
    private val maxDepth: Int = 5,
) {
    private val typeHintsStorage: PythonTypeHintsStorage
        get() = typeSystem.typeHintsStorage

    private val root = SymbolTreeNode(rootSymbol)

    private fun generateSuccessors(node: SymbolTreeNode): List<SymbolTreeNode> =
        state.getMocksForSymbol(node.symbol).mapNotNull { (mockHeader, resultSymbol) ->
            val protocol: (UtType) -> List<UtType> =
                when (mockHeader.method) {
                    MpAssSubscriptMethod -> { returnType: UtType ->
                        listOf(createBinaryProtocol("__setitem__", pythonAnyType, returnType))
                    }

                    MpSubscriptMethod -> { returnType: UtType ->
                        listOf(createBinaryProtocol("__getitem__", pythonAnyType, returnType))
                    }

                    NbAddMethod -> { returnType: UtType ->
                        listOf(createBinaryProtocol("__add__", pythonAnyType, returnType))
                    }

                    NbSubtractMethod -> { returnType: UtType ->
                        listOf(createBinaryProtocol("__sub__", pythonAnyType, returnType))
                    }

                    NbBoolMethod -> { _: UtType ->
                        listOf(createUnaryProtocol("__bool__", typeHintsStorage.pythonBool))
                    }

                    NbIntMethod -> { _: UtType ->
                        listOf(createUnaryProtocol("__int__", typeHintsStorage.pythonInt))
                    }

                    NbNegativeMethod -> { returnType: UtType ->
                        listOf(createUnaryProtocol("__neg__", returnType))
                    }

                    NbPositiveMethod -> { returnType: UtType ->
                        listOf(createUnaryProtocol("__pos__", returnType))
                    }

                    NbMatrixMultiplyMethod -> { returnType: UtType ->
                        listOf(createBinaryProtocol("__matmul__", pythonAnyType, returnType))
                    }

                    NbMultiplyMethod -> { returnType: UtType ->
                        listOf(createBinaryProtocol("__mul__", pythonAnyType, returnType))
                    }

                    SqConcatMethod -> { returnType: UtType ->
                        listOf(createBinaryProtocol("__add__", pythonAnyType, returnType))
                    }

                    SqLengthMethod -> { _: UtType ->
                        listOf(createUnaryProtocol("__len__", typeHintsStorage.pythonInt))
                    }

                    TpIterMethod -> { returnType: UtType ->
                        listOf(createUnaryProtocol("__iter__", returnType))
                    }

                    TpGetattro, TpSetattro -> func@{ returnType: UtType ->
                        val attribute = mockHeader.args[1].getConcreteStrIfDefined(state.preAllocatedObjects)
                            ?: return@func emptyList()
                        listOf(createProtocolWithAttribute(attribute, returnType))
                    }

                    is TpRichcmpMethod -> { returnType: UtType ->
                        val protocolName: String = when (mockHeader.method.op) {
                            ConcretePythonInterpreter.pyEQ -> "__eq__"
                            ConcretePythonInterpreter.pyNE -> "__ne__"
                            ConcretePythonInterpreter.pyLT -> "__lt__"
                            ConcretePythonInterpreter.pyLE -> "__le__"
                            ConcretePythonInterpreter.pyGT -> "__gt__"
                            ConcretePythonInterpreter.pyGE -> "__ge__"
                            else -> error("Wrong OP in TpRichcmpMethod")
                        }

                        val operandTypes = listOf(pythonAnyType) + mockHeader.args.mapNotNull { operand ->
                            val modelHolder = PyModelHolder(state.pyModel)
                            val type = operand.getTypeIfDefined(modelHolder, state.memory) as? ConcretePythonType
                            type?.let { typeSystem.typeHintOfConcreteType(it) }
                        }

                        operandTypes.toSet().map {
                            createBinaryProtocol(protocolName, it, returnType)
                        } + operandTypes.filter { // "soft" constraints
                            it.pythonDescription() is PythonCompositeTypeDescription
                        }
                    }

                    is TpCallMethod -> { returnType: UtType ->
                        listOf(
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
                        )
                    }
                }
            val originalHints = protocol(pythonAnyType)
            if (originalHints.isEmpty()) {
                return@mapNotNull null
            }
            node.upperBounds += originalHints
            val newNode = SymbolTreeNode(resultSymbol)
            val edge = SymbolTreeEdge(newNode, node) { type -> protocol(type) }
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
