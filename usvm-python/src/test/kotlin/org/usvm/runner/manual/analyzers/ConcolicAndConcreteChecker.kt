package org.usvm.runner.manual.analyzers

import org.usvm.UMachineOptions
import org.usvm.machine.interpreters.concrete.IllegalOperationException
import org.usvm.runner.CustomPythonTestRunner
import org.usvm.runner.manual.manualTestLogger
import org.usvm.runner.manual.program.ProgramProvider
import kotlin.time.Duration.Companion.seconds

class ConcolicAndConcreteChecker : ProgramAnalyzer {
    override fun run(provider: ProgramProvider) {
        val runner = CustomPythonTestRunner(
            provider.program,
            provider.typeSystem,
            UMachineOptions(stepLimit = 60U, timeout = 60.seconds),
            allowPathDiversions = true
        )
        runner.timeoutPerRunMs = 10_000
        provider.functions.forEach { function ->
            manualTestLogger.info("Running ${function.tag}...")
            try {
                val comparator = runner.standardConcolicAndConcreteChecks
                when (val argsNum = function.numberOfArguments) {
                    0 -> runner.check0NoPredicates(function, comparator)
                    1 -> runner.check1NoPredicates(function, comparator)
                    2 -> runner.check2NoPredicates(function, comparator)
                    3 -> runner.check3NoPredicates(function, comparator)
                    4 -> runner.check4NoPredicates(function, comparator)
                    else -> manualTestLogger.warn("${function.tag} ignored because it has $argsNum arguments")
                }
            } catch (e: IllegalOperationException) {
                manualTestLogger.info("Illegal operation while analyzing: ${e.operation}\n")
            }
        }
    }
}
