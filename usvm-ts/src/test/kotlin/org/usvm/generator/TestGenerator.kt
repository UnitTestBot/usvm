package org.usvm.generator

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTestTypeScriptGenerator
import org.usvm.api.TsTestTypeScriptGenerator.generateTest
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class TestGenerator {
    val scene = run {
        val name = "SimpleGeneratorExample.ts"
        val path = getResourcePath("/generator/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }
    val options = UMachineOptions()
    val tsOptions = TsOptions()

    @Test
    fun runSimpleTestGeneration() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "simpleFunction" }

        val results = machine.analyze(listOf(method))
        val resolver = TsTestResolver()
        val tests = results.map { resolver.resolve(method, it) }
        val testsText = tests.map { generateTest(it) }

        println(testsText.joinToString("\n\n"))

        val resourceTsPath = getResourcePath("/generator/SimpleGeneratorExample.ts")
        val originalTs = Files.readString(resourceTsPath)
        val harness = TsTestTypeScriptGenerator.buildHarness(originalTs, testsText)
        val tmpDir = createTempDirectory("ts-gen-tests-")
        val testFile = tmpDir.resolve("generatedTests.js")
        Files.writeString(testFile, harness)
        println("Harness written to: " + testFile.toAbsolutePath())

        val process = ProcessBuilder("node", testFile.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        println("Node output:\n$output")

        // Assertions
        assertTrue(exitCode == 0, "Node exited with $exitCode")
        assertTrue(!output.contains("FAILED:"), "Detected failed tests in harness. Output: $output")
        assertTrue("ALL_PASSED" in output, "ALL_PASSED marker missing. Output: $output")
        val executedCount = output.lineSequence().count { it.startsWith("PASSED: ") }
        val generatedCount = testsText.size
        assertTrue(executedCount == generatedCount, "Mismatch test count: generated=$generatedCount, executed=$executedCount. Output: $output")
    }
}
