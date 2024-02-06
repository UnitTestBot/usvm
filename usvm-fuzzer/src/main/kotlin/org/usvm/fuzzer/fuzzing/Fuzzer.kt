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
import org.usvm.instrumentation.util.enclosingMethod
import org.usvm.fuzzer.generator.random.FuzzerRandomNormalDistribution
import org.usvm.fuzzer.helpers.JcdbConstantsCollector
import org.usvm.fuzzer.mutation.`object`.CallRandomMethod
import org.usvm.fuzzer.mutation.MutationRepository
import org.usvm.fuzzer.strategy.FairStrategy
import org.usvm.fuzzer.util.getTrace
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class Fuzzer(
    private val targetMethod: JcMethod,
    classPath: List<String>,
    private val userClassLoader: ClassLoader,
    private val runner: UTestConcreteExecutor,
    private val timeoutInSeconds: Duration,
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
    private val mutationRepository = MutationRepository(FairStrategy(), dataFactory)
    val seedManager = SeedManager(
        targetMethod = targetMethod,
        seedExecutor = runner,
        dataFactory = dataFactory,
        seedsLimit = seedLimit,
        mutationRepository = mutationRepository
    )

    val executionEstimator = ExecutionEstimator()
    val coveredStatements = HashSet<JcInst>()


    init {
        val constantsCollector = JcdbConstantsCollector(jcClasspath)
        constantsCollector.collect(targetMethod)
        val generatorContext = GeneratorContext(
            constants = mapOf(),
            repository = generatorRepository,
            random = random,
            jcClasspath = jcClasspath,
            userClassLoader = userClassLoader,
            extractedConstants = constantsCollector.extractedConstants
        )
        generatorRepository.registerGeneratorContext(generatorContext)
    }


    @OptIn(ExperimentalTime::class)
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
        seedManager.generateAndExecuteInitialSeed(seedLimit)
        seedManager.printStats()

        //Mutation
        var mutationsCounter = 0
        while (mutationsCounter != 100_000) {
            if (mutationsCounter % 1_000 == 0) {
                seedManager.printStats()
                mutationRepository.printStats()
            }
            seedManager.mutateAndExecuteSeed()
//            val seedToMutate = seedManager.getSeed(0)
//            val mutationToApply = mutationRepository.getMutation(0)
//            val mutationResult = mutationToApply.mutate(seedToMutate) ?: continue
//            val mutatedSeed = mutationResult.first ?: continue
//            seedManager.executeSeed(mutatedSeed)
            if (seedManager.isMethodCovered()) {
                break
            }
            mutationsCounter++
        }
        seedManager.printStats()
        println("MUTATIONS = $mutationsCounter")
        runner.close()
        return

        //MUTATION
//        repeat(100) {
//            val seed = seedManager.seeds.random()
//            val newSeed = CallRandomMethod().appendSeedFactory(dataFactory).mutate(seed)?.first!!
//            val executionResult = runner.executeAsync(newSeed.toUTest())
//            coveredStatements.addAll(executionResult.getTrace())
//            println("EXEC RES AFTER MUTATION = ${executionResult::class.java.name} TRACE SIZE = ${executionResult.getTrace().size}")
//        }
//        val methodCoverage = coveredStatements.filter { it.enclosingMethod == targetMethod }
//        val coveragePercents = methodCoverage.size.toDouble() / targetMethod.instList.size * 100
//        println("Method: ${targetMethod.name} ||| COVERED ${methodCoverage.size} of ${targetMethod.instList.size} ($coveragePercents%)")
//        val methodCoverageInLines = methodCoverage.map { it.lineNumber }.toSet()
//        val methodLines = targetMethod.instList.map { it.lineNumber }.toSet()
//        val lineCoveragePercents = methodCoverageInLines.size.toDouble() / methodLines.size * 100
//        println("LINE COV Method: ${targetMethod.name} ||| COVERED ${methodCoverageInLines.size} of ${methodLines.size} ($lineCoveragePercents%)")
//        println("DID NOT COVERED LINES: ${methodLines.filter { it !in methodCoverageInLines }}")
//
//        Cov.coveredStatements.addAll(coveredStatements)
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



    private fun generateInitialSeed() {
        repeat(seedLimit) {
            seedManager.addSeed(dataFactory.generateSeedsForMethod(targetMethod), it)
        }
    }

    private fun extractConstants() {

    }


}