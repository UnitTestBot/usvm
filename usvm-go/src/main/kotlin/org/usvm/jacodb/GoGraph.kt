package org.usvm.jacodb

import org.jacodb.api.core.analysis.ApplicationGraph
import org.jacodb.api.core.cfg.ControlFlowGraph

interface GoBytecodeGraph<out Statement> : ControlFlowGraph<@UnsafeVariance Statement> {
    fun throwers(node: @UnsafeVariance Statement): Set<Statement>
    fun catchers(node: @UnsafeVariance Statement): Set<Statement>
}

class GoGraph(
    override val instructions: List<GoInst>,
) : GoBytecodeGraph<GoInst> {

    private val predecessorMap: MutableMap<GoInst, MutableSet<GoInst>> = hashMapOf()
    private val successorMap: MutableMap<GoInst, Set<GoInst>> = hashMapOf()

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is GoTerminatingInst -> emptySet()
                is GoBranchingInst -> inst.successors.map { instructions[it.index] }.toSet()
                else -> setOf(next(inst))
            }
            successorMap[inst] = successors
            for (successor in successors) {
                predecessorMap.computeIfAbsent(successor) { mutableSetOf() }.add(inst)
            }
        }
    }

    override val entries: List<GoInst>
        get() = instructions.take(1)

    override val exits: List<GoInst>
        get() = instructions.filterIsInstance<GoTerminatingInst>()

    fun index(inst: GoInst): Int {
        if (inst in instructions) {
            return inst.location.index
        }
        return -1
    }

    fun ref(inst: GoInst): GoInstRef = GoInstRef(index(inst))
    fun inst(ref: GoInstRef): GoInst = instructions[ref.index]
    fun next(inst: GoInst): GoInst = instructions[ref(inst).index + 1]
    fun previous(inst: GoInst): GoInst = instructions[ref(inst).index - 1]

    override fun successors(node: GoInst): Set<GoInst> = successorMap[node].orEmpty()
    override fun predecessors(node: GoInst): Set<GoInst> = predecessorMap[node].orEmpty()

    override fun throwers(node: GoInst): Set<GoInst> {
        return exits.filterIsInstance<GoPanicInst>().toSet()
    }
    // TODO: catchers? Is there any?
    override fun catchers(node: GoInst): Set<GoInst> = emptySet()

    override fun iterator(): Iterator<GoInst> = instructions.iterator()
}

data class GoBasicBlock(
    val id: Int,
    val successors: List<Int>,
    val predecessors: List<Int>,
    val insts: List<GoInst> = emptyList(),
)

class GoBlockGraph(
    override val instructions: List<GoBasicBlock>,
    instList: List<GoInst>,
) : GoBytecodeGraph<GoBasicBlock> {

    val graph: GoGraph = GoGraph(instList)

    override val entries: List<GoBasicBlock>
        get() = instructions.take(1)

    override val exits: List<GoBasicBlock>
        get() = instructions.filter { it.successors.isEmpty() }

    override fun successors(node: GoBasicBlock): Set<GoBasicBlock> {
        return node.successors.mapTo(hashSetOf()) { instructions[it] }
    }

    override fun predecessors(node: GoBasicBlock): Set<GoBasicBlock> {
        return node.predecessors.mapTo(hashSetOf()) { instructions[it] }
    }

    override fun throwers(node: GoBasicBlock): Set<GoBasicBlock> {
        return exits.filter { it.insts.last() is GoPanicInst }.toSet()
    }

    // TODO: catchers? Is there any?
    override fun catchers(node: GoBasicBlock): Set<GoBasicBlock> = emptySet()

    override fun iterator(): Iterator<GoBasicBlock> = instructions.iterator()
}

interface GoApplicationGraph : ApplicationGraph<GoMethod, GoInst> {
    val project: GoProject
}

class GoApplicationGraphImpl(
    override val project: GoProject,
) : GoApplicationGraph {

    override fun predecessors(node: GoInst): Sequence<GoInst> {
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        return predecessors.asSequence()
    }

    override fun successors(node: GoInst): Sequence<GoInst> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        return successors.asSequence()
    }

    override fun callees(node: GoInst): Sequence<GoMethod> {
        return sequenceOf(node.parent)
    }

    override fun callers(method: GoMethod): Sequence<GoInst> {
        var res = listOf<GoInst>()
        for (block in method.blocks) {
            res = listOf(res, block.insts).flatten()
        }
        return res.asSequence()
    }

    override fun entryPoint(method: GoMethod): Sequence<GoInst> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: GoMethod): Sequence<GoInst> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: GoInst): GoMethod {
        return node.location.method
    }
}
