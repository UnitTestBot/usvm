package org.usvm.fuzzer

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorRepository
import org.usvm.fuzzer.generator.SeedFactory
import org.usvm.fuzzer.seed.SeedManager
import org.usvm.fuzzer.strategy.ExecutionEstimator
import org.usvm.fuzzer.strategy.RandomStrategy
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.instrumentation.util.enclosingMethod
import org.usvm.fuzzer.generator.random.FuzzerRandomNormalDistribution
import org.usvm.fuzzer.mutation.CallRandomMethod
import org.usvm.fuzzer.util.getPossiblePathsToField

class Fuzzer(
    private val targetMethod: JcMethod,
    classPath: List<String>,
    private val userClassLoader: ClassLoader,
    private val runner: UTestConcreteExecutor
) {

    private val jcClasspath: JcClasspath = targetMethod.enclosingClass.classpath
    private val generatorRepository = GeneratorRepository()
    val random = FuzzerRandomNormalDistribution(42, 0.0, 50.0)

    val seedLimit = 10
    val seedFactory = SeedFactory(jcClasspath, generatorRepository, userClassLoader, random)
    val seedManager = SeedManager(listOf(), seedLimit, RandomStrategy())
    val executionEstimator = ExecutionEstimator()
    val coveredStatements = HashSet<JcInst>()


    init {
        val generatorContext = GeneratorContext(
            constants = mapOf(),
            repository = generatorRepository,
            random = random,
            jcClasspath = jcClasspath,
            userClassLoader = userClassLoader
        )
        generatorRepository.registerGeneratorContext(generatorContext)
    }


    suspend fun fuzz() {
        generateInitialSeed()
        val d2vConverter = Descriptor2ValueConverter(userClassLoader)
        //Execute each seed
        for (seed in seedManager.seeds) {
            val res = runner.executeAsync(seed.toUTest())
            println("RES = ${res::class.java.name}")
            executionEstimator.estimate(seed, res)
            when (res) {
                is UTestExecutionExceptionResult -> {
//                    val exceptionInstance = d2vConverter.buildObjectFromDescriptor(res.cause)
//                    println(exceptionInstance)
                    println("COV = ${res.trace?.size}")
                    coveredStatements.addAll(res.trace ?: listOf())
                    seed.coverage = res.trace
                    seed.apply {
                        coverage = res.trace
                        argsInitialDescriptors = listOf(res.initialState.instanceDescriptor) + res.initialState.argsDescriptors
                        argsResDescriptors = listOf(res.resultState.instanceDescriptor) + res.resultState.argsDescriptors
                        accessedFields = res.resultState.accessedFields
                    }
                }

                is UTestExecutionFailedResult -> {}
                is UTestExecutionInitFailedResult -> {
//                    val exceptionInstance = d2vConverter.buildObjectFromDescriptor(res.cause)
//                    println(exceptionInstance)
                    coveredStatements.addAll(res.trace ?: listOf())
                    seed.coverage = res.trace
                }

                is UTestExecutionSuccessResult -> {
                    println("COV = ${res.trace?.size}")
                    coveredStatements.addAll(res.trace ?: listOf())
                    seed.apply {
                        coverage = res.trace
                        argsInitialDescriptors = listOf(res.initialState.instanceDescriptor) + res.initialState.argsDescriptors
                        argsResDescriptors = listOf(res.resultState.instanceDescriptor) + res.resultState.argsDescriptors
                        accessedFields = res.resultState.accessedFields
                    }
                }
                is UTestExecutionTimedOutResult -> {}
            }
        }
        val seed = seedManager.seeds.random()
        CallRandomMethod().appendSeedFactory(seedFactory).mutate(seed)
        val randomFieldToMutate = seed.accessedFields?.randomOrNull()
        println(seed)


        val methodCoverage = coveredStatements.filter { it.enclosingMethod == targetMethod }
        val coveragePercents = methodCoverage.size.toDouble() / targetMethod.instList.size * 100
        println("Method: ${targetMethod.name} ||| COVERED ${methodCoverage.size} of ${targetMethod.instList.size} ($coveragePercents%)")
        Cov.coveredStatements.addAll(coveredStatements)
        return

//        repeat(10) {
//            val mutation = mutationManager.getMutation(it)
//            val seed = seedManager.getSeed(it)
//            val position = seed.getPositionToMutate(it)
//            val mutatedSeed = mutation.mutate(seed, position.index) ?: return@repeat
//            val res = runner.executeAsync(mutatedSeed.toUTest())
//            executionEstimator.estimate(mutation, res)
//            executionEstimator.estimate(seed, res)
//            executionEstimator.estimate(position, res)
//            seedManager.addSeed(mutatedSeed, it)
//        }
    }

    private fun mutate() {
        repeat(10) { iteration ->
            val seedToMutate = seedManager.getSeed(iteration)
            val randomFieldToMutate = seedToMutate.accessedFields?.firstOrNull() ?: return@repeat

        }
    }


    private fun generateInitialSeed() {
        repeat(seedLimit) {
            seedManager.addSeed(seedFactory.generateForMethod(targetMethod), it)
        }
    }

    private fun extractConstants() {

    }


}