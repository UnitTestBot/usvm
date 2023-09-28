package org.usvm.util

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.AnnotationConsumer
import org.usvm.*
import java.util.stream.Stream

annotation class Options(
    val strategies: Array<PathSelectionStrategy>,
    val combinationStrategy: PathSelectorCombinationStrategy = PathSelectorCombinationStrategy.INTERLEAVED,
    val stopOnCoverage: Int = 100,
    val timeout: Long = 20_000,
    val coverageZone: CoverageZone = CoverageZone.METHOD,
    val solverType: SolverType = SolverType.YICES,
    val targetSearchDepth: UInt = 0u
)

@ParameterizedTest
@ArgumentsSource(MachineOptionsArgumentsProvider::class)
annotation class UsvmTest(val options: Array<Options>)

class MachineOptionsArgumentsProvider : ArgumentsProvider, AnnotationConsumer<UsvmTest> {

    private var options = listOf<UMachineOptions>()

    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
        return options.map { Arguments.of(it) }.stream()
    }

    override fun accept(t: UsvmTest?) {
        requireNotNull(t)
        options = t.options.map {
            UMachineOptions(
                pathSelectionStrategies = it.strategies.toList(),
                pathSelectorCombinationStrategy = it.combinationStrategy,
                stopOnCoverage = it.stopOnCoverage,
                timeoutMs = it.timeout,
                coverageZone = it.coverageZone,
                solverType = it.solverType,
                targetSearchDepth = it.targetSearchDepth
            )
        }
    }
}
