package org.usvm

import org.junit.jupiter.api.Test
import org.usvm.model.Converter
import org.usvm.model.Parser
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JacoDbTest {
    @Test
    fun testMax2() {
        test("max2")
    }

    @Test
    fun testMax2Improved() {
        test("Max2Improved")
    }

    @Test
    fun testLoopSimple() {
        test("loopSimple")
    }

    @Test
    fun testPanicRecover() {
        test("panicRecover")
    }

    @Test
    fun testPanicRecoverSimple() {
        test("panicRecoverSimple")
    }

    private val options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )

    private fun test(name: String) {
        val stopwatch = measureTimeMillis {
            val pkg = Converter.unpackPackage(Parser().deserialize("out/usvm_examples.json"))
            val machine = GoMachine(options)
            println(machine.analyzeAndResolve(pkg.methods.find { it.metName == name }!!))
        }

        println(stopwatch)
    }
}