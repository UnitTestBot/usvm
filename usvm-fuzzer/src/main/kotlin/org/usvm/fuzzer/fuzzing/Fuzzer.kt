package org.usvm.fuzzer.fuzzing

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorRepository
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.seed.SeedManager
import org.usvm.fuzzer.strategy.ExecutionEstimator
import org.usvm.fuzzer.strategy.RandomStrategy
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.instrumentation.util.enclosingMethod
import org.usvm.fuzzer.generator.random.FuzzerRandomNormalDistribution
import org.usvm.fuzzer.mutation.CallRandomMethod
import org.usvm.fuzzer.mutation.MutationRepository
import org.usvm.fuzzer.util.getTrace
import org.usvm.instrumentation.testcase.api.UTestExecutionInitFailedResult
import kotlin.system.exitProcess

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
    val dataFactory = DataFactory(
        jcClasspath = jcClasspath,
        generatorRepository = generatorRepository,
        userClassLoader = userClassLoader,
        random = random,
        seedArgsChoosingStrategy = RandomStrategy()
    )
    val seedManager = SeedManager(
        targetMethod = targetMethod,
        seedExecutor = runner,
        dataFactory = dataFactory,
        seedsLimit = seedLimit,
        seedSelectionStrategy = RandomStrategy()
    )

    val executionEstimator = ExecutionEstimator()
    val coveredStatements = HashSet<JcInst>()
    private val mutationRepository = MutationRepository(RandomStrategy(), dataFactory)


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
//        generateInitialSeed()
//        val d2vConverter = Descriptor2ValueConverter(userClassLoader)
//        //Execute each seed
//        for (seed in seedManager.seeds) {
//            val res = runner.executeAsync(seed.toUTest())
//            coveredStatements.addAll(res.getTrace())
//            println("RES = ${res::class.java.name} TRACE SIZE = ${res.getTrace().size}")
//            seed.addSeedExecutionInfo(res)
//        }
        seedManager.generateInitialSeed(seedLimit)
        println(seedManager)
        return

        //MUTATION
        repeat(100) {
            val seed = seedManager.seeds.random()
            val newSeed = CallRandomMethod().appendSeedFactory(dataFactory).mutate(seed)?.first!!
            val executionResult = runner.executeAsync(newSeed.toUTest())
            coveredStatements.addAll(executionResult.getTrace())
            println("EXEC RES AFTER MUTATION = ${executionResult::class.java.name} TRACE SIZE = ${executionResult.getTrace().size}")
        }
        val methodCoverage = coveredStatements.filter { it.enclosingMethod == targetMethod }
        val coveragePercents = methodCoverage.size.toDouble() / targetMethod.instList.size * 100
        println("Method: ${targetMethod.name} ||| COVERED ${methodCoverage.size} of ${targetMethod.instList.size} ($coveragePercents%)")
        val methodCoverageInLines = methodCoverage.map { it.lineNumber }.toSet()
        val methodLines = targetMethod.instList.map { it.lineNumber }.toSet()
        val lineCoveragePercents = methodCoverageInLines.size.toDouble() / methodLines.size * 100
        println("LINE COV Method: ${targetMethod.name} ||| COVERED ${methodCoverageInLines.size} of ${methodLines.size} ($lineCoveragePercents%)")
        println("DID NOT COVERED LINES: ${methodLines.filter { it !in methodCoverageInLines }}")

        Cov.coveredStatements.addAll(coveredStatements)
        return

//        val methodCoverage = coveredStatements.filter { it.enclosingMethod == targetMethod }
//        val coveragePercents = methodCoverage.size.toDouble() / targetMethod.instList.size * 100
//        println("Method: ${targetMethod.name} ||| COVERED ${methodCoverage.size} of ${targetMethod.instList.size} ($coveragePercents%)")
//        Cov.coveredStatements.addAll(coveredStatements)
//        return

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

    suspend fun mutate() {
        repeat(10) { iteration ->
            val seedToMutate = seedManager.getSeed(iteration)
            val randomMutation = mutationRepository.getMutation(iteration)
            val (mutatedSeed, mutationInfo) = randomMutation.mutate(seedToMutate) ?: return@repeat
            mutatedSeed ?: return@repeat
            val executionResult = runner.executeAsync(mutatedSeed.toUTest())
            mutatedSeed.addSeedExecutionInfo(executionResult)
            executionEstimator.estimateExecution(mutatedSeed, randomMutation, mutationInfo, executionResult)
        }
    }


    private fun generateInitialSeed() {
        repeat(seedLimit) {
            seedManager.addSeed(dataFactory.generateSeedsForMethod(targetMethod), it)
        }
    }

    private fun extractConstants() {

    }


}