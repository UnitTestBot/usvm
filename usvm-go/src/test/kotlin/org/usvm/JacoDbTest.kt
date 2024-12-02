package org.usvm

import org.jacodb.go.api.GoMethod
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.usvm.model.Converter
import org.usvm.model.Parser
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JacoDbTest {
    @TestFactory
    fun jacodbTestLong(): Collection<DynamicTest> {
        val pkg = Converter.unpackPackage(Parser().deserialize("out/usvm_examples.json"))
        val machine = GoMachine(pkg, options)
        return methods(pkg).filter { it.metName in arrayOf("loopInfinite", "loopInner", "loopCollatz") }.map {
            DynamicTest.dynamicTest(it.metName) {
                println(measureTimeMillis { machine.analyzeAndResolve(it.metName) })
            }
        }
    }

    @TestFactory
    fun jacodbTestFast(): Collection<DynamicTest> {
        val pkg = Converter.unpackPackage(Parser().deserialize("out/usvm_examples.json"))
        val machine = GoMachine(pkg, options)
        return methods(pkg).filter { it.metName !in arrayOf("loopInfinite", "loopInner", "loopCollatz") }.map {
            DynamicTest.dynamicTest(it.metName) {
                println(measureTimeMillis { machine.analyzeAndResolve(it.metName) })
            }
        }
    }

    private fun methods(pkg: GoPackage): List<GoMethod> = pkg.methods.filter { !it.metName.contains('$') }

    private val options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 1000000L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
}