package org.usvm.ps

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.PathNode
import org.usvm.UState

class StateLoopStatistic<Stmt, Method, Loop : Any, State : UState<*, Method, Stmt, *, *, State>>(
    private val loopTracker: StateLoopTracker<Loop, Stmt, State>,
) {
    private val emptyStatsFrames = hashMapOf<Method, StateStackFrameStats<Method, Stmt, Loop>>()

    fun updateStats(referenceStats: StateStats<Method, Stmt, Loop>, state: State): StateStats<Method, Stmt, Loop> {
        val stats = updateStackFrames(referenceStats, state)

        val stateLocation = state.pathNode
        val loop = loopTracker.findLoopEntrance(stateLocation.statement)

        if (loop == null) {
            return stats.copy(forksCount = state.forkPoints.depth)
        }

        val currentFrame = stats.frames.last()
        val loopStats = currentFrame.getLoopStats(loop)

        val updatedLoopStats = if (loopStats == null) {
            // first loop iteration
            LoopStats(loop, stateLocation, iterations = 0, nonConcreteIterations = 0)
        } else {
            val forkedInPreviousLoopIteration = loopIterationForkedInPreviousIteration(state, loopStats, loop)

            var nonConcreteIterations = loopStats.nonConcreteIterations
            if (forkedInPreviousLoopIteration) {
                nonConcreteIterations++
            }

            LoopStats(loop, stateLocation, loopStats.iterations + 1, nonConcreteIterations)
        }

        val updatedFrame = currentFrame.updateLoopStats(updatedLoopStats)
        return stats.copy(
            frames = stats.frames.let { frames -> frames.set(frames.lastIndex, updatedFrame) },
            forksCount = state.forkPoints.depth
        )
    }

    /**
     * Check if the state has forks during last [loop] iteration, that
     * can affect the [loop] iteration number.
     * */
    private fun loopIterationForkedInPreviousIteration(
        state: State,
        loopStats: LoopStats<Stmt, Loop>,
        loop: Loop
    ): Boolean {
        var forkPoint = state.forkPoints
        while (forkPoint.depth > 0 && forkPoint.statement.depth >= loopStats.previousLoopEnter.depth) {
            if (loopTracker.isLoopIterationFork(loop, forkPoint.statement.statement)) {
                return true
            }
            forkPoint = forkPoint.parent ?: break
        }
        return false
    }

    /**
     * Ensure that [stats] stack frames match [state] stack frames.
     * */
    private fun updateStackFrames(stats: StateStats<Method, Stmt, Loop>, state: State): StateStats<Method, Stmt, Loop> {
        val frames = stats.frames.builder()
        val stack = state.callStack

        var idx = 0
        var stackFramesMatch = true

        while (idx < stack.size) {
            val method = stack[idx].method

            if (stackFramesMatch && idx < frames.size && method == frames[idx].method) {
                idx++
                continue
            }

            stackFramesMatch = false

            val emptyFrame = emptyStatsFrames.getOrPut(method) {
                StateStackFrameStats(method, loops = null, maxNonConcreteIteration = 0)
            }

            if (idx < frames.size) {
                frames[idx] = emptyFrame
            } else {
                frames.add(emptyFrame)
            }

            idx++
        }

        while (frames.size > idx) {
            frames.removeLast()
        }

        val updatedFrames = frames.build()

        if (updatedFrames === stats.frames) {
            return stats
        }

        return stats.copy(frames = updatedFrames)
    }

    companion object {
        private val rootStats = StateStats<Any?, Any?, Any?>(frames = persistentListOf(), forksCount = 0)

        @Suppress("UNCHECKED_CAST")
        fun <Method, Stmt, Loop> rootStats(): StateStats<Method, Stmt, Loop> =
            rootStats as StateStats<Method, Stmt, Loop>
    }
}

/**
 * Single loop statistic.
 *
 * [previousLoopEnter] -- start of the previous iteration.
 * [iterations] -- total number of the iterations (concrete and non-concrete).
 * [nonConcreteIterations] -- number of iterations that can be affected via some symbolic values.
 * */
data class LoopStats<Stmt, Loop>(
    val loop: Loop,
    val previousLoopEnter: PathNode<Stmt>,
    val iterations: Int,
    val nonConcreteIterations: Int
)

/**
 * Loop stats for a single state stack frame.
 *
 * [loops] -- compressed loop stats representation.
 *   null -- method has no loops, or state didn't enter any loop on the current frame.
 *   LoopStats -- state enter only  a single loop on the current frame.
 *   Array<LoopStats> -- state enter many loops.
 * */
data class StateStackFrameStats<Method, Stmt, Loop>(
    val method: Method,
    private val loops: Any? = null,
    val maxNonConcreteIteration: Int,
) {
    fun updateLoopStats(stats: LoopStats<Stmt, Loop>): StateStackFrameStats<Method, Stmt, Loop> =
        StateStackFrameStats(
            method = method,
            loops = updateLoops(stats),
            maxNonConcreteIteration = maxOf(maxNonConcreteIteration, stats.nonConcreteIterations)
        )

    @Suppress("UNCHECKED_CAST")
    fun getLoopStats(loop: Loop): LoopStats<Stmt, Loop>? {
        val loopsStats = loops ?: return null
        if (loopsStats is LoopStats<*, *>) {
            return if (loopsStats.loop == loop) loopsStats as LoopStats<Stmt, Loop> else null
        }

        loopsStats as Array<LoopStats<*, *>>
        for (stats in loopsStats) {
            if (stats.loop == loop) return stats as LoopStats<Stmt, Loop>
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateLoops(stats: LoopStats<Stmt, Loop>): Any {
        val loopsStats = loops ?: return stats

        if (loopsStats is LoopStats<*, *>) {
            if (loopsStats.loop == stats.loop) return stats
            return arrayOf(loopsStats, stats)
        }

        loopsStats as Array<LoopStats<*, *>>
        for (i in loopsStats.indices) {
            if (loopsStats[i].loop == stats.loop) {
                return loopsStats.copyOf().also { it[i] = stats }
            }
        }

        val result = loopsStats.copyOf(loopsStats.size + 1)
        result[result.lastIndex] = stats
        return result
    }
}

data class StateStats<Method, Stmt, Loop>(
    val frames: PersistentList<StateStackFrameStats<Method, Stmt, Loop>>,
    val forksCount: Int,
) {
    val maxLoopIteration: Int
        get() = frames.maxOfOrNull { it.maxNonConcreteIteration } ?: 0
}
