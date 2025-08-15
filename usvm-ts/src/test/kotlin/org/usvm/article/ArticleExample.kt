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

    @Test
    fun runF1TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f1" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF2TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f2" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF3TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f3" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF4estGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f4" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF5TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f5" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF6TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f6" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF7TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f7" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF8TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f8" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF9TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f9" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }

    @Test
    fun runF10TestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "f10" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        println("Generated tests for method: ${method.name}")
        println("Total tests generated: ${tests.size}")
        println("Tests: ${tests.joinToString("\n")}")
    }
}
