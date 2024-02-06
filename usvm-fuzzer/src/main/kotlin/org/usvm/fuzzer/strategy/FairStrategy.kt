package org.usvm.fuzzer.strategy

import kotlin.random.Random

class FairStrategy<T : Selectable> : ChoosingStrategy<T> {

    private val maxProb = 0.7
    private val minProb = 0.1

    override fun chooseBest(from: Collection<T>, iterationNumber: Int): T {
        val selectableToK = from.map {
            val score =
                if (it.numberOfChooses == 0) {
                    0.1
                } else {
                    (it.score / it.numberOfChooses) + 0.1
                }
            it to score
        }.sortedBy { it.second }
        if (selectableToK.map { it.second }.toSet().size == 1) {
            return from.random()
        }
        val minScore = selectableToK.minOf { it.second }
        val maxScore = selectableToK.maxOf { it.second }
        val probabilities = selectableToK.map { it.first to calcFairProbability(minScore, maxScore, it.second) }
        val sumOfProbabilities = selectableToK.sumOf { it.second }
        val randomProb = Random.nextDouble(0.0, sumOfProbabilities)
        var s = 0.0
        for (el in probabilities) {
            s += el.second
            if (s >= randomProb) {
                return el.first
            }
        }
        //Should be unreachable
        return from.random()
    }

    private fun calcFairProbability(minScore: Double, maxScore: Double, score: Double): Double {
        return ((score * (maxProb - minProb) - minScore * maxProb + minScore * minProb) / (maxScore - minScore)) + minProb
    }

    override fun chooseWorst(from: Collection<T>, iterationNumber: Int): T {
        return from.minBy { it.score / it.numberOfChooses }
    }

}
