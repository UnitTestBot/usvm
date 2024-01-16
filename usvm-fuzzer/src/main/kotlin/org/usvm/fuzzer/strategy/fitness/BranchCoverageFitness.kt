package org.usvm.fuzzer.strategy.fitness

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.fuzzing.FuzzingStorage
import org.usvm.fuzzer.seed.Seed
import kotlin.math.sqrt

class BranchCoverageFitness : TestFitness {
    override fun getFitness(seed: Seed, targetInsts: List<JcInst>): Double {
        val coverage = seed.coverage ?: error("Can't calc fitness for not executed seed")
        var res = 0.0
        for (targetInst in targetInsts) {
            val distance = getDistanceBetween(coverage, targetInst, seed.targetMethod)
            res += distance * distance
        }
        return sqrt(res)
    }

    private fun getDistanceBetween(trace: List<JcInst>, targetInst: JcInst, targetMethod: JcMethod): Int {
        val que = ArrayDeque<Pair<JcInst, Int>>()
        val targetMethodFlowGraph = targetMethod.flowGraph()
        targetMethodFlowGraph.predecessors(targetInst).forEach { parentInst -> que.add(parentInst to 1) }
        while (que.isNotEmpty()) {
            val (parentInst, distance) = que.removeFirst()
            if (parentInst in trace) {
                return distance
            }
            targetMethodFlowGraph.predecessors(parentInst).forEach { que.add(it to distance + 1) }
        }
        return Int.MAX_VALUE
    }
}