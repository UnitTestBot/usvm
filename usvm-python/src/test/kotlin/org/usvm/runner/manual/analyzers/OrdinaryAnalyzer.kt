package org.usvm.runner.manual.analyzers

import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.PyMachine
import org.usvm.machine.interpreters.concrete.IllegalOperationException
import org.usvm.machine.results.DefaultPyMachineResultsReceiver
import org.usvm.machine.results.serialization.ObjectWithDictSerializer
import org.usvm.python.model.PyResultFailure
import org.usvm.python.model.PyResultSuccess
import org.usvm.runner.manual.manualTestLogger
import org.usvm.runner.manual.program.ProgramProvider

object OrdinaryAnalyzer : ProgramAnalyzer {
    private const val maxIterations = 200
    private const val allowPathDiversion = true
    private const val maxInstructions = 50_000
    private const val timeoutPerRunMs = 4_000L
    private const val timeoutMs = 30_000L
    private val saverCreator: () -> DefaultPyMachineResultsReceiver<String> = {
        DefaultPyMachineResultsReceiver(ObjectWithDictSerializer)
    }

    override fun run(provider: ProgramProvider) {
        val machine = PyMachine(
            provider.program,
            provider.typeSystem,
            printErrorMsg = false
        )
        val emptyCoverage = mutableListOf<String>()
        machine.use {
            provider.functions.forEach { f ->
                processFunction(f, machine, emptyCoverage)
            }

            manualTestLogger.info("GENERAL STATISTICS")
            manualTestLogger.info(machine.statistics.writeReport())
        }

        manualTestLogger.info("Empty coverage for:")
        emptyCoverage.forEach { manualTestLogger.info(it) }
    }

    private fun processFunction(
        f: PyUnpinnedCallable,
        machine: PyMachine,
        emptyCoverage: MutableList<String>,
    ) {
        manualTestLogger.info("Started analysing function ${f.tag}")

        try {
            val start = System.currentTimeMillis()
            val saver = saverCreator()
            val iterations = machine.analyze(
                f,
                saver,
                maxIterations = maxIterations,
                allowPathDiversion = allowPathDiversion,
                maxInstructions = maxInstructions,
                timeoutPerRunMs = timeoutPerRunMs,
                timeoutMs = timeoutMs
            )

            saver.pyTestObserver.tests.forEach { test ->
                manualTestLogger.info("INPUT:")
                test.inputArgs.forEach { manualTestLogger.info(it) }
                manualTestLogger.info("RESULT:")
                when (val result = test.result) {
                    is PyResultSuccess -> manualTestLogger.info(result.output)
                    is PyResultFailure -> manualTestLogger.info(result.exception)
                }
                manualTestLogger.info("")
            }

            if (machine.statistics.functionStatistics.last().coverage == 0.0) {
                emptyCoverage.add(f.tag)
            }
            manualTestLogger.info(
                "Finished analysing ${f.tag} in ${System.currentTimeMillis() - start} milliseconds. " +
                    "Made $iterations iterations."
            )
            manualTestLogger.info("FUNCTION STATISTICS")
            manualTestLogger.info(machine.statistics.functionStatistics.last().writeReport())
            manualTestLogger.info("")
        } catch (e: IllegalOperationException) {
            manualTestLogger.info("Illegal operation while analyzing: ${e.operation}\n")
        }
    }
}
