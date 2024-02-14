package org.usvm.fuzzer.generators

import kotlinx.coroutines.runBlocking
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.int
import org.jacodb.api.ext.toType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.generator.random.FuzzerRandomNormalDistribution
import org.usvm.fuzzer.mutation.MutationRepository
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.seed.SeedManager
import org.usvm.fuzzer.seed.SeedShrinker
import org.usvm.fuzzer.strategy.FairStrategy
import org.usvm.fuzzer.strategy.RandomStrategy
import org.usvm.fuzzer.test.Arrays
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.fuzzer.util.getTrace
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestIntExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.random.Random

class ShrinkerTest : GeneratorTest() {

    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath =
                listOf(
                    "/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation.jar",
                    "/home/zver/IdeaProjects/usvm/usvm-fuzzer/build/libs/usvm-fuzzer-test.jar"
                )
            userClassLoader =
                URLClassLoader(arrayOf(Paths.get(testJarPath.first()).toUri().toURL()), this::class.java.classLoader)
            init()
        }
    }

    @Test
    fun testShrink() {
//        val klass = jcClasspath.findClass<Arrays>()
//        val tm = klass.declaredMethods.find { it.name == "isIdentityMatrix" }!!
//        val random = FuzzerRandomNormalDistribution(42, 0.0, 50.0)
//        val dataFactory = DataFactory(
//            jcClasspath = jcClasspath,
//            generatorRepository = generatorRepository,
//            userClassLoader = userClassLoader,
//            random = random,
//            seedArgsChoosingStrategy = RandomStrategy()
//        )
//        val mutationRepository = MutationRepository(FairStrategy(), dataFactory)
//        val seedManager = SeedManager(
//            targetMethod = tm,
//            seedExecutor = executor,
//            dataFactory = dataFactory,
//            seedsLimit = 10,
//            mutationRepository = mutationRepository
//        )
//
//        runBlocking {
//            seedManager.generateAndExecuteInitialSeed(1)
//        }
//        val seed = seedManager.seeds.first()
//        println("COV1 = ${seed.coverage?.values?.sum()}")
//        val arg = seed.args.first()
//        val klassInstance = arg.instance
//        val m = klass.declaredMethods.find { it.name == "setMatrixElement" }!!
//        val methodCalls = (0..100).map {
//            UTestMethodCall(
//                klassInstance,
//                m,
//                listOf(
//                    UTestIntExpression(Random.nextInt(0, 4), jcClasspath.int),
//                    UTestIntExpression(Random.nextInt(0, 4), jcClasspath.int),
//                    UTestIntExpression(Random.nextInt(0, 2), jcClasspath.int),
//                )
//            )
//        }
//        val newArg = Seed.ArgumentDescriptor(
//            arg.instance,
//            arg.type,
//            arg.initialExprs + methodCalls
//        )
//        val newSeed = seed.mutate(arg, newArg)
//        val res = runBlocking {
//            seedManager.executeSeed(newSeed)
//        }
////        println("COV2 = ${res.getTrace().values.sum()}")
//        val shriker = SeedShrinker(executor)
//        val shrinked =
//            runBlocking {
//                shriker.shrink(newSeed)
//            }
//        executor.close()
    }
}