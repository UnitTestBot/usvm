package org.usvm.machine

import mu.KLogging
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.jacodb.panda.dynamic.api.PandaBranchingInst
import org.jacodb.panda.dynamic.api.PandaBytecodeGraph
import org.jacodb.panda.dynamic.api.PandaCatchInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaLoop
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.loops
import org.usvm.machine.state.PandaState
import org.usvm.ps.StateLoopTracker

val logger = object : KLogging() {}.logger


class PandaLoopTracker : StateLoopTracker<PandaLoopTracker.LoopInfo, PandaInst, PandaState> {

    private val methodLoops = hashMapOf<PandaMethod, List<LoopInfo>>()

    override fun findLoopEntrance(statement: PandaInst): LoopInfo? {

        val method = statement.location.method

        val allMethodLoops = methodLoops.getOrPut(method) {
            method.flowGraph().loops.map { it.buildInfo() }
        }

        return allMethodLoops.firstOrNull { it.loop.head == statement }
    }

    override fun isLoopIterationFork(loop: LoopInfo, forkPoint: PandaInst): Boolean {
        return forkPoint in loop.forkPoints
    }

    class LoopInfo(val loop: PandaLoop, val forkPoints: Set<PandaInst>) {
        val size: Int get() = loop.instructions.size
    }

    private fun PandaBranchingInst.isConditional(): Boolean = successors.size > 1

    private fun PandaLoop.buildInfo(): LoopInfo {
        val loopExecutionPathAnalyzer = LoopExecutionPathAnalyzer(this)

        val loopForkPoints = hashSetOf<PandaInst>()
        for (inst in instructions) {
            if (inst !is PandaBranchingInst || !inst.isConditional()) {
                continue
            }

            // If we have no branches that can jump to the next loop iteration inst is not a loop fork point
            if (loopExecutionPathAnalyzer.allExecutionPathsAreInsideLoop(inst)) {
                continue
            }

            loopForkPoints.add(inst)
        }

        if (loopForkPoints.isEmpty()) {
            logger.warn { "Infinite loop in: ${instructions.first().location.method}" }
        }

        return LoopInfo(this, loopForkPoints)
    }

    private class LoopExecutionPathAnalyzer(private val loop: PandaLoop) {
        private val graph = LoopExecutionGraph(
            loop.instructions + LOOP_ITERATION_EXIT,
            loop.instructions.toHashSet()
        )

        private val dominators = GraphDominators(graph).also {
            it.find()
        }

        fun allExecutionPathsAreInsideLoop(inst: PandaInst): Boolean {
            val dom = dominators.dominators(inst)
            return dom.any { it !== inst && it !== LOOP_ITERATION_EXIT }
        }

        private inner class LoopExecutionGraph(
            private val instructionsWithIterationExit: List<Any>,
            private val loopBodyInstructions: Set<PandaInst>
        ) : PandaBytecodeGraph<Any>, List<Any> by instructionsWithIterationExit {
            override val entries: List<Any> = listOf(LOOP_ITERATION_EXIT)
            override val instructions: List<Any> get() = instructionsWithIterationExit

            override fun predecessors(node: Any): Set<Any> {
                if (node === LOOP_ITERATION_EXIT) return emptySet()

                val allSuccessors = loop.graph.successors(node as PandaInst)
                // + loop.graph.catchers(node) // todo: exceptions?
                return normalizeSuccessors(allSuccessors)
            }

            override fun throwers(node: Any): Set<Any> =
                normalizeSuccessors(loop.graph.successors(node as PandaCatchInst))

            private fun normalizeSuccessors(successors: Collection<PandaInst>): Set<Any> =
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
        }

        companion object {
            private val LOOP_ITERATION_EXIT = Any()
        }
    }

}
