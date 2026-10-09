package org.usvm

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.usvm.model.Converter
import org.usvm.model.Parser
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Coverage and execution smoke tests; scalar semantics are checked by GoSemanticRegressionTest. */
class JacoDbTest {
    @TestFactory
    fun fastSamples(): Collection<DynamicTest> = samples(slow = false)

    @Tag("manual")
    @TestFactory
    fun slowSamples(): Collection<DynamicTest> = samples(slow = true)

    private fun samples(slow: Boolean): Collection<DynamicTest> {
        val pkg = Converter.unpack(Parser().deserialize(generatedGoFile("examples/usvm_examples.json").path))
        val program = GoProgram(listOf(pkg))
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
            coverageZone = CoverageZone.TRANSITIVE,
            exceptionsPropagation = true,
            timeout = (if (slow) 30 else 5).seconds,
            solverTimeout = 2.seconds,
            typeOperationsTimeout = 2.seconds,
        )
        val customOptions = GoMachineOptions(
            failOnNotFullCoverage = true,
            uncoveredMethods = listOf("panicRecoverComplex"),
        )
        return pkg.methods.filter { '$' !in it.metName && (it.metName in slowMethods) == slow }.map { method ->
            DynamicTest.dynamicTest(method.metName) {
                GoMachine(program, options, customOptions).use { machine ->
                    val results = machine.analyzeAndResolve(pkg, method.metName)
                    assertTrue(results.isNotEmpty(), "No complete execution for ${method.metName}")
                }
            }
        }
    }

    private val slowMethods = setOf("loopInfinite", "loopInner", "loopCollatz", "mapLoopLen", "canVisitAllRooms")
}
