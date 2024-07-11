package org.usvm

import mu.KLogging
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.jacodb.panda.dynamic.ets.base.EtsBranchingStmt
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.graph.EtsBytecodeGraph
import org.jacodb.panda.dynamic.ets.graph.EtsLoop
import org.jacodb.panda.dynamic.ets.graph.loops
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.usvm.ps.StateLoopTracker
import org.usvm.state.TSState

val logger = object : KLogging() {}.logger


class TSLoopTracker : StateLoopTracker<TSLoopTracker.LoopInfo, EtsStmt, TSState> {

    private val methodLoops = hashMapOf<EtsMethod, List<LoopInfo>>()

    override fun findLoopEntrance(statement: EtsStmt): LoopInfo? {

        val method = statement.location.method

        val allMethodLoops = methodLoops.getOrPut(method) {
            method.flowGraph().loops.map { it.buildInfo() }
        }

        return allMethodLoops.firstOrNull { it.loop.head == statement }
    }

    override fun isLoopIterationFork(loop: LoopInfo, forkPoint: EtsStmt): Boolean {
        return forkPoint in loop.forkPoints
    }

    class LoopInfo(val loop: EtsLoop, val forkPoints: Set<EtsStmt>) {
        val size: Int get() = loop.instructions.size
    }

    private fun EtsBranchingStmt.isConditional(): Boolean {
        return method.cfg.successors(this).size > 1
    }

    private fun EtsLoop.buildInfo(): LoopInfo {
        val loopExecutionPathAnalyzer = LoopExecutionPathAnalyzer(this)

        val loopForkPoints = hashSetOf<EtsStmt>()
        for (inst in instructions) {
            if (inst !is EtsBranchingStmt || !inst.isConditional()) {
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

    private class LoopExecutionPathAnalyzer(private val loop: EtsLoop) {
        private val graph = LoopExecutionGraph(
            loop.instructions + LOOP_ITERATION_EXIT,
            loop.instructions.toHashSet()
        )

        private val dominators = GraphDominators(graph).also {
            it.find()
        }

        fun allExecutionPathsAreInsideLoop(inst: EtsStmt): Boolean {
            val dom = dominators.dominators(inst)
            return dom.any { it !== inst && it !== LOOP_ITERATION_EXIT }
        }

        private inner class LoopExecutionGraph(
            private val instructionsWithIterationExit: List<Any>,
            private val loopBodyInstructions: Set<EtsStmt>
        ) : EtsBytecodeGraph<Any>, List<Any> by instructionsWithIterationExit {
            override val entries: List<Any> = listOf(LOOP_ITERATION_EXIT)
            override val instructions: List<Any> get() = instructionsWithIterationExit

            override fun predecessors(node: Any): Set<Any> {
                if (node === LOOP_ITERATION_EXIT) return emptySet()

                val allSuccessors = loop.graph.successors(node as EtsStmt)
                // + loop.graph.catchers(node) // todo: exceptions?
                return normalizeSuccessors(allSuccessors)
            }

            // TODO: no catch stmt in ETS??
            override fun throwers(node: Any): Set<Any> = emptySet()

            private fun normalizeSuccessors(successors: Collection<EtsStmt>): Set<Any> =
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
