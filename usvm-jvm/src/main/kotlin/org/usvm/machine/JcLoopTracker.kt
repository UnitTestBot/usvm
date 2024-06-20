package org.usvm.machine

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcBranchingInst
import org.jacodb.api.jvm.cfg.JcBytecodeGraph
import org.jacodb.api.jvm.cfg.JcCatchInst
import org.jacodb.api.jvm.cfg.JcInst
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
            method.flowGraph().loops.map { it.buildInfo() }
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
            loop.instructions + LOOP_ITERATION_EXIT,
            loop.instructions.toHashSet()
        )

        private val dominators = GraphDominators(graph).also {
            it.find()
        }

        fun allExecutionPathsAreInsideLoop(inst: JcInst): Boolean {
            val dom = dominators.dominators(inst)
            return dom.any { it !== inst && it !== LOOP_ITERATION_EXIT }
        }

        /**
         * Loop iteration execution graph.
         *
         * Normally, the loop CFG looks like:
         *
         * (header) -> bodyStmt_0 -> ... -> bodyStmt_N -> (exit)
         *     ^_________________________________|
         *
         * To analyze branches that belongs to the same iteration we remove loop back-edges
         * and replace all exits with a single exit point:
         *
         * (header) -> bodyStmt_0 -> ... -> bodyStmt_N -> [LOOP_ITERATION_EXIT]
         *                                      |_______________^
         * Note: the graph is reversed.
         * */
        private inner class LoopExecutionGraph(
            private val instructionsWithIterationExit: List<Any>,
            private val loopBodyInstructions: Set<JcInst>
        ) : JcBytecodeGraph<Any>, List<Any> by instructionsWithIterationExit {
            override val entries: List<Any> = listOf(LOOP_ITERATION_EXIT)
            override val instructions: List<Any> get() = instructionsWithIterationExit

            override fun predecessors(node: Any): Set<Any> {
                if (node === LOOP_ITERATION_EXIT) return emptySet()

                val allSuccessors = loop.graph.successors(node as JcInst)
                        // + loop.graph.catchers(node) // todo: exceptions?
                return normalizeSuccessors(allSuccessors)
            }

            override fun throwers(node: Any): Set<Any> =
                normalizeSuccessors(loop.graph.successors(node as JcCatchInst))

            private fun normalizeSuccessors(successors: Collection<JcInst>): Set<Any> =
                successors.mapTo(hashSetOf()) {
                    if (it == loop.head || it !in loopBodyInstructions) {
                        LOOP_ITERATION_EXIT
                    } else {
                        it
                    }
                }

            override val exits: List<Any> get() = error("Should not be used")
            override fun successors(node: Any): Set<Any> = error("Should not be used")
            override fun catchers(node: Any): Set<Any> = error("Should not be used")
            override fun iterator(): Iterator<Any> = super.iterator()
        }

        companion object {
            private val LOOP_ITERATION_EXIT = Any()
        }
    }
}
