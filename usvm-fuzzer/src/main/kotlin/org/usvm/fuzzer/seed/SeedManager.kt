package org.usvm.fuzzer.seed

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.strategy.ChoosingStrategy
import org.usvm.fuzzer.strategy.fitness.BranchCoverageFitness
import org.usvm.fuzzer.strategy.fitness.TestFitness
import org.usvm.fuzzer.util.getFalseBranchInst
import org.usvm.fuzzer.util.getTrace
import org.usvm.fuzzer.util.getTrueBranchInst
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import kotlin.math.sqrt

class SeedManager(
    private val targetMethod: JcMethod,
    private val seedExecutor: UTestConcreteExecutor,
    private val dataFactory: DataFactory,
    private val seedsLimit: Int,
    private val seedSelectionStrategy: ChoosingStrategy<Seed>
) {

    val seeds = mutableListOf<Seed>()
    val coveredInstructions: MutableSet<JcInst> = mutableSetOf()
    val unCoveredBranches: MutableSet<Branch> = mutableSetOf()

    //Will fill after initial seed generation
    val targetBranches: MutableSet<Branch> = mutableSetOf()

    val testSuite: MutableList<Seed> = mutableListOf()
    val fitness: TestFitness = BranchCoverageFitness()

    init {
        targetMethod.instList
            .filterIsInstance<JcIfInst>()
            .forEach { unCoveredBranches.add(Branch(it, it.getTrueBranchInst(), it.getFalseBranchInst())) }
    }

    suspend fun generateInitialSeed(size: Int) {
        repeat(size) {
            val seed = dataFactory.generateSeedsForMethod(targetMethod)
            addSeed(seed, it)
            val seedExecutionResult = seedExecutor.executeAsync(seed.toUTest())
            seed.addSeedExecutionInfo(seedExecutionResult)
            val seedCoverage = seedExecutionResult.getTrace()
            coveredInstructions.addAll(seedCoverage)
            updateBranchInfo()
        }
    }

    fun calculateFronts(): List<List<Seed>> {
        val targetInstructions = targetBranches.map {
            if (it.trueBranchCovered) it.falseBranch
            else it.trueBranch
        }
        val fronts = ArrayList<HashSet<Seed>>()
        val curFront = HashSet<Seed>()
        val availableSeeds = mutableMapOf<Seed, MutableList<Double>>()
        for (targetInstruction in targetInstructions) {
            val fitnessOfSeeds = seeds.map { it to fitness.getFitness(it, listOf(targetInstruction)) }
            val minDistance = fitnessOfSeeds.minOf { it.second }
            val nonDominatedSeeds = fitnessOfSeeds.filter { it.second == minDistance }
            fitnessOfSeeds
                .filter { it.second >= minDistance }
                .forEach { availableSeeds.getOrPut(it.first) { mutableListOf(it.second) }.add(it.second) }
            nonDominatedSeeds.forEach {
                curFront.add(it.first)
                availableSeeds.remove(it.first)
            }
        }
        fronts.add(curFront.toHashSet())
        curFront.clear()
        while (availableSeeds.isNotEmpty()) {
            val availableSeedsAsList = availableSeeds.toList()
            val nonDominatedSeeds = availableSeeds.keys.toMutableList()
            for (i in 0 until availableSeedsAsList.size) {
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
                        isDominates1 -> nonDominatedSeeds.remove(seed2)
                        else -> nonDominatedSeeds.remove(seed1)
                    }
                }
            }
        }
        return emptyList()
    }

    private fun updateBranchInfo() {
        unCoveredBranches.forEach { branch ->
            if (branch.trueBranch in coveredInstructions) {
                branch.trueBranchCovered = true
            }
            if (branch.falseBranch in coveredInstructions) {
                branch.falseBranchCovered = true
            }
        }
        unCoveredBranches.removeAll { it.trueBranchCovered && it.falseBranchCovered }
        targetBranches.removeAll { it.trueBranchCovered && it.falseBranchCovered }
        targetBranches.addAll(unCoveredBranches.filter { it.trueBranchCovered || it.falseBranchCovered })
    }

    fun addSeed(seed: Seed, iterationNumber: Int) {
        if (seeds.size >= seedsLimit) {
            seeds.add(seed)
            val worstSeed = seedSelectionStrategy.chooseWorst(seeds, iterationNumber)
            seeds.remove(worstSeed)
        } else {
            seeds.add(seed)
        }
        //TODO update test suite
    }

    fun getSeed(iterationNumber: Int) = seedSelectionStrategy.chooseBest(seeds, iterationNumber)

    data class Branch(
        val ifInst: JcIfInst,
        val trueBranch: JcInst,
        val falseBranch: JcInst,
        var trueBranchCovered: Boolean = false,
        var falseBranchCovered: Boolean = false,
    )


}