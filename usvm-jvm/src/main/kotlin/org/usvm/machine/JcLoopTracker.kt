package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcBranchingInst
import org.jacodb.api.cfg.JcBytecodeGraph
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.jacodb.impl.cfg.util.JcLoop
import org.jacodb.impl.cfg.util.loops
import org.usvm.machine.state.JcState
import org.usvm.ps.StateLoopTracker

class JcLoopTracker : StateLoopTracker<JcLoopTracker.LoopInfo, JcInst, JcState> {
    private val methodLoops = hashMapOf<JcMethod, List<LoopInfo>>()

    override fun findLoopEntrance(statement: JcInst): LoopInfo? {
        val method = statement.location.method

        val allMethodLoops = methodLoops.getOrPut(method) {
            method.flowGraph().loops.map { it.buildInfo() }.sortedByDescending { it.size }
        }

        return allMethodLoops.firstOrNull { it.loop.head == statement }
    }

    override fun isLoopIterationFork(loop: LoopInfo, forkPoint: JcInst): Boolean =
        forkPoint in loop.forkPoints

    class LoopInfo(val loop: JcLoop, val forkPoints: Set<JcInst>) {
        val size: Int get() = loop.instructions.size
    }

    private fun JcLoop.buildInfo(): LoopInfo {
        val loopExecutionPathAnalyzer = LoopExecutionPathAnalyzer(this)

        val loopForkPoints = hashSetOf<JcInst>()
        for (inst in instructions) {
            if (inst !is JcBranchingInst || !inst.isConditional()) {
                continue
            }

            // If we have no branches that can jump to the next loop iteration inst is not a loop fork point
            if (loopExecutionPathAnalyzer.allExecutionPathsAreInsideLoop(inst)) {
                continue
            }

            loopForkPoints.add(inst)
        }

        if (loopForkPoints.isEmpty()) {
            logger.warn { "Infinite loop in: ${graph.method}" }
        }

        return LoopInfo(this, loopForkPoints)
    }

    private fun JcBranchingInst.isConditional(): Boolean = successors.size > 1

    private class LoopExecutionPathAnalyzer(private val loop: JcLoop) {
        private val graph = LoopExecutionGraph(
            loop.instructions + LOOP_ENTRY,
            loop.instructions.toHashSet()
        )

        private val dominators = GraphDominators(graph).also {
            it.find()
        }

        fun allExecutionPathsAreInsideLoop(inst: JcInst): Boolean {
            val dom = dominators.dominators(inst)
            return dom.any { it !== inst && it !== LOOP_ENTRY }
        }

        private inner class LoopExecutionGraph(
            private val instructionsWithEntry: List<Any>,
            private val loopBodyInstructions: Set<JcInst>
        ) : JcBytecodeGraph<Any>, List<Any> by instructionsWithEntry {
            override val entries: List<Any> get() = listOf(LOOP_ENTRY)

            override fun predecessors(node: Any): Set<Any> {
                if (node === LOOP_ENTRY) return emptySet()

                val allSuccessors = loop.graph.successors(node as JcInst)
                        // + loop.graph.catchers(node) // todo: exceptions?
                return normalizeSuccessors(allSuccessors)
            }

            override fun throwers(node: Any): Set<Any> =
                normalizeSuccessors(loop.graph.successors(node as JcCatchInst))

            private fun normalizeSuccessors(successors: Collection<JcInst>): Set<Any> =
                successors.mapTo(hashSetOf()) {
                    if (it == loop.head || it !in loopBodyInstructions) {
                        LOOP_ENTRY
                    } else {
                        it
                    }
                }

            override val exits: List<Any> get() = error("Should not be used")
            override fun successors(node: Any): Set<Any> = error("Should not be used")
            override fun catchers(node: Any): Set<Any> = error("Should not be used")
        }

        companion object {
            private val LOOP_ENTRY = Any()
        }
    }
}
