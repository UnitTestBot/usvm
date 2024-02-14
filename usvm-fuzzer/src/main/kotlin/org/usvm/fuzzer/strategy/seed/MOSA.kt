package org.usvm.fuzzer.strategy.seed

import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.seed.SeedManager
import org.usvm.fuzzer.strategy.fitness.BranchCoverageFitness
import org.usvm.fuzzer.strategy.fitness.TestFitness
import org.usvm.fuzzer.util.toIdentityHashSet
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class MOSA(private val seedManager: SeedManager) : SeedChoosingStrategy {
    private val fitness: TestFitness = BranchCoverageFitness()
    private var mosaFronts = ArrayList<MutableSet<Seed>>()
    private val seedsDistancesCache =
        IdentityHashMap<Seed, MutableMap<JcInst, Double>>()//mutableMapOf<Seed, List<Pair<JcInst, Double>>>()

    fun replace(seed: Seed, replacement: Seed) {
        mosaFronts.forEach {
            if (it.contains(seed)) {
                it.remove(seed)
                it.add(replacement)
            }
        }
    }

    override fun chooseWorst(): Seed {
        recalculateFrontsIfNeed()
        return mosaFronts.last().random()
    }


    override fun chooseWorstAndRemove(): Seed {
        recalculateFrontsIfNeed()
        val worstSeed =
            mosaFronts.last().random()
        mosaFronts.last().remove(worstSeed)
        if (mosaFronts.last().isEmpty()) {
            mosaFronts.removeLast()
        }
        seedsDistancesCache.remove(worstSeed)
        return worstSeed
    }

    override fun chooseWorstAndRemove(recentlyAddedSeed: Seed): Seed {
        recalculateFrontsIfNeed()
        val worstSeed =
            if (mosaFronts.last().contains(recentlyAddedSeed)) {
                recentlyAddedSeed
            } else {
                mosaFronts.last().random()
            }
        mosaFronts.last().remove(worstSeed)
        if (mosaFronts.last().isEmpty()) {
            mosaFronts.removeLast()
        }
        seedsDistancesCache.remove(worstSeed)
        return worstSeed
    }

    override fun chooseBest(): Seed {
        recalculateFrontsIfNeed()
        val probabilityToGetFromFirstFront = 75.0
        val step = probabilityToGetFromFirstFront / mosaFronts.size
        var curFront = 0
        var curProbability = probabilityToGetFromFirstFront
        while (curProbability > 0) {
            if (curFront >= mosaFronts.size) return mosaFronts.random().random()
            if (seedManager.random.getTrueWithProb(probabilityToGetFromFirstFront.toInt())) {
                return try {
                    mosaFronts[curFront].random()
                } catch (e: Throwable) {
                    println()
                    throw e
                }
            }
            curFront++
            curProbability -= step
        }
        return try {
            mosaFronts.random().random()
        } catch (e: Throwable) {
            println()
            throw e
        }
    }

    private fun recalculateFrontsIfNeed() {
        mosaFronts = calculateFronts()
    }

    private fun calculateFronts(): ArrayList<MutableSet<Seed>> {
        val targetInstructions = seedManager.targetBranches.map {
            if (it.trueBranchCovered) it.falseBranch
            else it.trueBranch
        }
        val fronts = ArrayList<MutableSet<Seed>>()
        val curFront = Collections.newSetFromMap(IdentityHashMap<Seed, Boolean>())
        val availableSeeds = IdentityHashMap<Seed, MutableList<Double>>()
        if (targetInstructions.isEmpty()) {
            curFront.addAll(seedManager.seeds)
            fronts.add(curFront)
            return fronts
        }
        for (targetInstruction in targetInstructions) {
            val fitnessOfSeeds = seedManager.seeds.map { seed ->
                val distancesOfSeed = seedsDistancesCache.getOrPut(seed) { mutableMapOf() }
                val distanceToTargetInst =
                    distancesOfSeed.getOrPut(targetInstruction) {
                        fitness.getFitness(
                            seed,
                            listOf(targetInstruction)
                        )
                    }
                seed to distanceToTargetInst
            }
            val minDistance = fitnessOfSeeds.minOf { it.second }
            val maxCoverage = fitnessOfSeeds.maxOf { it.first.coverage?.values?.sum() ?: 0 }
            val nonDominatedSeeds =
                fitnessOfSeeds.filter { it.second == minDistance && it.first.coverage?.values?.sum() == maxCoverage }

            fitnessOfSeeds
                .filter { it.second >= minDistance }
                .forEach { availableSeeds.getOrPut(it.first) { mutableListOf() }.add(it.second) }

            nonDominatedSeeds.forEach {
                curFront.add(it.first)
            }
        }
        fronts.add(curFront)
        curFront.forEach { availableSeeds.remove(it) }
        while (availableSeeds.isNotEmpty()) {
            val availableSeedsAsList = availableSeeds.toList()
            val nonDominatedSeeds = availableSeeds.keys.toMutableList()
            for (i in availableSeedsAsList.indices) {
                val seed1 = availableSeedsAsList[i].first
                for (j in i + 1 until availableSeedsAsList.size) {
                    val seed2 = availableSeedsAsList[j].first
                    var isDominates1 = false
                    var isDominates2 = false
                    for ((ind, _) in targetInstructions.withIndex()) {
                        val f1 = availableSeedsAsList[i].second[ind]
                        val f2 = availableSeedsAsList[j].second[ind]
                        if (f1 < f2) {
                            isDominates1 = true
                        }
                        if (f2 < f1) {
                            isDominates2 = true
                        }
                        if (isDominates1 && isDominates2) {
                            break
                        }
                    }
                    when {
                        isDominates1 == isDominates2 -> {}
                        isDominates1 -> nonDominatedSeeds.remove(seed2)
                        else -> nonDominatedSeeds.remove(seed1)
                    }
                }
            }
            fronts.add(nonDominatedSeeds.toIdentityHashSet())
            nonDominatedSeeds.forEach { availableSeeds.remove(it) }
        }
        return fronts
    }
}