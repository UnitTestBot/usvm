package org.usvm.fuzzer

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorRepository
import org.usvm.fuzzer.generator.SeedGenerator
import org.usvm.fuzzer.mutation.AddPrimitiveConstant
import org.usvm.fuzzer.mutation.CallRandomMethod
import org.usvm.fuzzer.mutation.MutationManager
import org.usvm.fuzzer.seed.SeedManager
import org.usvm.fuzzer.strategy.ExecutionEstimator
import org.usvm.fuzzer.strategy.RandomStrategy
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import kotlin.random.Random

class Fuzzer(
    private val targetMethod: JcMethod,
    classPath: List<String>,
    private val userClassLoader: ClassLoader
) {

    private val jcClasspath: JcClasspath = targetMethod.enclosingClass.classpath
    private val generatorRepository = GeneratorRepository()

    val runner = UTestConcreteExecutor(
        JcRuntimeTraceInstrumenterFactory::class,
        classPath,
        jcClasspath,
        InstrumentationModuleConstants.testExecutionTimeout
    )

    val seedLimit = 10
    val seedGenerator = SeedGenerator(jcClasspath, generatorRepository)
    val seedManager = SeedManager(listOf(), seedLimit, RandomStrategy())
    val executionEstimator = ExecutionEstimator()
    val coveredStatements = HashSet<JcInst>()

    private val mutations = listOf(AddPrimitiveConstant(), CallRandomMethod())
    private val mutationManager = MutationManager(mutations, RandomStrategy())

    init {
        val generatorContext = GeneratorContext(mapOf(), generatorRepository, Random(42), jcClasspath, userClassLoader)
        generatorRepository.registerGeneratorContext(generatorContext)
    }


    suspend fun fuzz() {
        generateInitialSeed()
        //Execute each seed
        for (seed in seedManager.seeds) {
            val res = runner.executeAsync(seed.toUTest())
            println("RES = $res")
            executionEstimator.estimate(seed, res)
            when (res) {
                is UTestExecutionExceptionResult -> coveredStatements.addAll(res.trace ?: listOf())
                is UTestExecutionFailedResult -> {}
                is UTestExecutionInitFailedResult -> coveredStatements.addAll(res.trace ?: listOf())
                is UTestExecutionSuccessResult -> coveredStatements.addAll(res.trace ?: listOf())
                is UTestExecutionTimedOutResult -> {}
            }
        }
        val coveragePercents = coveredStatements.size.toDouble() / targetMethod.instList.size * 100
        println("COVERED ${coveredStatements.size} of ${targetMethod.instList.size} ($coveragePercents%)")
        return

        repeat(10) {
            val mutation = mutationManager.getMutation(it)
            val seed = seedManager.getSeed(it)
            val position = seed.getPositionToMutate(it)
            val mutatedSeed = mutation.mutate(seed, position.index) ?: return@repeat
            val res = runner.executeAsync(mutatedSeed.toUTest())
            executionEstimator.estimate(mutation, res)
            executionEstimator.estimate(seed, res)
            executionEstimator.estimate(position, res)
            seedManager.addSeed(mutatedSeed, it)
        }
    }

    private fun generateInitialSeed() {
        repeat(seedLimit) {
            seedManager.addSeed(seedGenerator.generateForMethod(targetMethod), it)
        }
    }


}