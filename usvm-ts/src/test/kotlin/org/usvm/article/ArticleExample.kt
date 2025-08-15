package org.usvm.article

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath

class ArticleExample {
    val scene = run {
        val name = "examples.ts"
        val path = getResourcePath("/article/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }
    val options = UMachineOptions()
    val tsOptions = TsOptions()

    private fun generateTestsFor(methodName: String) {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == methodName }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF1TestGeneration() {
        generateTestsFor("f1")
    }

    @Test
    fun runF2TestGeneration() {
        generateTestsFor("f2")
    }

    @Test
    fun runF3TestGeneration() {
        generateTestsFor("f3")
    }

    @Test
    fun runF4TestGeneration() {
        generateTestsFor("f4")
    }

    @Test
    fun runF5TestGeneration() {
        generateTestsFor("f5")
    }

    @Test
    fun runF6TestGeneration() {
        generateTestsFor("f6")
    }

    @Test
    fun runF7TestGeneration() {
        generateTestsFor("f7")
    }

    @Test
    fun runF8TestGeneration() {
        generateTestsFor("f8")
    }

    @Test
    fun runF9TestGeneration() {
        generateTestsFor("f9")
    }

    @Test
    fun runF10TestGeneration() {
        generateTestsFor("f10")
    }
}
