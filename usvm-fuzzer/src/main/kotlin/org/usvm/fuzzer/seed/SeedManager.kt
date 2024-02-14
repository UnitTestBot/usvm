package org.usvm.fuzzer.seed

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.mutation.MutationRepository
import org.usvm.fuzzer.strategy.seed.MOSA
import org.usvm.fuzzer.util.getFalseBranchInst
import org.usvm.fuzzer.util.getTrace
import org.usvm.fuzzer.util.getTrueBranchInst
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionTimedOutResult

class SeedManager(
    private val targetMethod: JcMethod,
    private val seedExecutor: UTestConcreteExecutor,
    private val dataFactory: DataFactory,
    private val seedsLimit: Int,
    private val mutationRepository: MutationRepository,
) {

    private val seedShrinker: SeedShrinker = SeedShrinker(seedExecutor)
    val seedSelectionStrategy = MOSA(this)
    val seeds = mutableListOf<Seed>()
    val coveredInstructions: MutableSet<JcInst> = HashSet()
    val unCoveredBranches: MutableSet<Branch> = HashSet()
    val coveredMethodInstructions: List<JcInst>
        get() = coveredInstructions.filter { it in targetMethod.instList }

    //Will fill after initial seed generation
    val targetBranches: MutableSet<Branch> = HashSet()
    val random = dataFactory.random

    val testSuite: MutableList<Seed> = mutableListOf()

    init {
        targetMethod.instList
            .filterIsInstance<JcIfInst>()
            .forEach { unCoveredBranches.add(Branch(it, it.getTrueBranchInst(), it.getFalseBranchInst())) }
    }

    suspend fun generateAndExecuteInitialSeed(size: Int) {
        repeat(size) {
            val seed = dataFactory.generateSeedsForMethod(targetMethod)
            executeSeed(seed)
        }
    }

    suspend fun executeSeed(seed: Seed): Pair<UTestExecutionResult, Boolean> {
        val seedExecutionResult = seedExecutor.executeAsync(seed.toUTest())
        if (seedExecutionResult.getTrace().isEmpty()) return seedExecutionResult to false
        seed.addSeedExecutionInfo(seedExecutionResult)
        val isAdded = addSeed(seed, 0)
        val seedCoverage = seedExecutionResult.getTrace()
        coveredInstructions.addAll(seedCoverage.keys)
        updateBranchInfo()
        return seedExecutionResult to isAdded
    }

    suspend fun mutateAndExecuteSeed() {
        val seedToMutate = getSeed(0)
        val mutationToApply = mutationRepository.getMutation(0)
        val mutationResult = mutationToApply.mutate(seedToMutate) ?: return
        val mutatedSeed = mutationResult.first ?: return
        val prevCoverageSize = coveredInstructions.size
        val (seedExecutionResult, isAdded) = executeSeed(mutatedSeed)
        if (isAdded) {
            val shrinkedSeed = seedShrinker.shrink(mutatedSeed) ?: mutatedSeed
            val executionResult = seedExecutor.executeAsync(shrinkedSeed.toUTest())
            shrinkedSeed.addSeedExecutionInfo(executionResult)
            replaceSeed(mutatedSeed, shrinkedSeed)
        }
        //Add execution info to mutation
        val newCoverageSize = coveredInstructions.size
        mutationToApply.numberOfChooses += 1
        mutationResult.second.mutatedField?.let { mutatedField ->
            Seed.fieldInfo.getFieldInfo(mutatedField)?.let { fieldInfo ->
                fieldInfo.numberOfChooses += 1
            }
        }
        if (newCoverageSize > prevCoverageSize) {
            println("INCREASE SCORE OF ${mutationToApply::class.java.name}")
            mutationToApply.score++
            if (mutationResult.second.mutatedField != null) {
                val fieldInfo = Seed.fieldInfo.getFieldInfo(mutationResult.second.mutatedField!!)
                if (fieldInfo != null) {
                    println("INCREASE SCORE OF FIELD ${fieldInfo.jcField.name}")
                    fieldInfo.score++
                }
            }
        }
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

    fun addSeed(seed: Seed, iterationNumber: Int): Boolean {

        return if (seeds.size >= seedsLimit) {
            seeds.add(seed)
            val worstSeed = seedSelectionStrategy.chooseWorstAndRemove(seed)
            seeds.remove(worstSeed)
            worstSeed !== seed
        } else {
            seeds.add(seed)
            true
        }
        //TODO update test suite
    }

    private fun replaceSeed(seed: Seed, replacement: Seed) {
        seeds.remove(seed)
        seeds.add(replacement)
        seedSelectionStrategy.replace(seed, replacement)
    }

    private fun getSeed(iterationNumber: Int) = seedSelectionStrategy.chooseBest()

    data class Branch(
        val ifInst: JcIfInst,
        val trueBranch: JcInst,
        val falseBranch: JcInst,
        var trueBranchCovered: Boolean = false,
        var falseBranchCovered: Boolean = false,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Branch

            return ifInst == other.ifInst
        }

        override fun hashCode(): Int {
            return ifInst.hashCode()
        }
    }

    fun printStats() {
        println(
            """
            Method ${targetMethod.name} fuzzing stats:
            Generated seeds: ${seeds.size}
            Covered instructions: ${coveredMethodInstructions.size} from ${targetMethod.instList.size} ${coveredMethodInstructions.size / targetMethod.instList.size.toDouble() * 100}%
            Covered lines: ${
                coveredMethodInstructions.map { it.lineNumber }.toSet().size
            } from ${targetMethod.instList.map { it.lineNumber }.toSet().size}
        """.trimIndent()
        )
    }

    fun isMethodCovered(): Boolean {
        val coveredMethodInstructions = coveredMethodInstructions.filter { it in targetMethod.instList }
        return coveredMethodInstructions.size == targetMethod.instList.size
    }

}