package org.usvm.samples.fairpathselector

import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.approximation.Approximations
import org.jacodb.impl.features.InMemoryHierarchy
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorFairnessStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.JcMachine
import org.usvm.samples.JacoDBContainer
import org.usvm.samples.JavaMethodTestRunner
import java.io.File
import kotlin.test.Ignore
import kotlin.time.Duration.Companion.milliseconds

internal class FairPathSelectorTest : JavaMethodTestRunner() {

    @Test
    @Ignore
    fun guavaTest() {
        val classpath = listOf(
            // Insert actual path
            File("..\\UTBotJava\\utbot-junit-contest\\src\\main\\resources\\projects\\guava\\guava-28.2-jre.jar")
        )
        val jcContainer = JacoDBContainer(
            "guava",
            classpath
        ) {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy, Approximations)
            loadByteCode(classpath)
        }

        val pqClass = jcContainer.cp.findClass("com.google.common.collect.LinkedListMultimap")
        //val pqClass = jcContainer.cp.findClass("com.google.common.collect.ImmutableEnumSet")
        //val pqClass = jcContainer.cp.findClass("com.google.common.graph.Graphs")
        val methods = pqClass.methods.filter { it.instList.size > 0 }

        val constantTimeOptions = UMachineOptions(
            timeout = 5000L.milliseconds,
            solverTimeout = 200L.milliseconds,
            pathSelectionStrategies = listOf(PathSelectionStrategy.RANDOM_PATH, PathSelectionStrategy.CLOSEST_TO_UNCOVERED),
            pathSelectorFairnessStrategy = PathSelectorFairnessStrategy.CONSTANT_TIME
        )

        val constantTimeMachine = JcMachine(jcContainer.cp, constantTimeOptions)
        constantTimeMachine.use {
            it.analyze(methods)
        }

        val completelyFairOptions = UMachineOptions(
            timeout = 5000L.milliseconds,
            solverTimeout = 200L.milliseconds,
            pathSelectionStrategies = listOf(PathSelectionStrategy.RANDOM_PATH, PathSelectionStrategy.CLOSEST_TO_UNCOVERED),
            pathSelectorFairnessStrategy = PathSelectorFairnessStrategy.COMPLETELY_FAIR
        )

        val completelyFairMachine = JcMachine(jcContainer.cp, completelyFairOptions)
        completelyFairMachine.use {
            it.analyze(methods)
        }
    }
}
