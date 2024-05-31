package org.usvm.runner.manual.analyzers

import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.PyMachine
import org.usvm.machine.interpreters.concrete.IllegalOperationException
import org.usvm.machine.results.DefaultPyMachineResultsReceiver
import org.usvm.machine.results.serialization.ObjectWithDictSerializer
import org.usvm.python.model.PyResultFailure
import org.usvm.python.model.PyResultSuccess
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

            println("GENERAL STATISTICS")
            println(machine.statistics.writeReport())
        }

        println()
        println("Empty coverage for:")
        emptyCoverage.forEach { println(it) }
    }

    private fun processFunction(
        f: PyUnpinnedCallable,
        machine: PyMachine,
        emptyCoverage: MutableList<String>,
    ) {
        println("Started analysing function ${f.tag}")

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
                println("INPUT:")
                test.inputArgs.forEach { println(it) }
                println("RESULT:")
                when (val result = test.result) {
                    is PyResultSuccess -> println(result.output)
                    is PyResultFailure -> println(result.exception)
                }
                println()
            }

            if (machine.statistics.functionStatistics.last().coverage == 0.0) {
                emptyCoverage.add(f.tag)
            }
            println(
                "Finished analysing ${f.tag} in ${System.currentTimeMillis() - start} milliseconds. " +
                    "Made $iterations iterations."
            )
            println("FUNCTION STATISTICS")
            println(machine.statistics.functionStatistics.last().writeReport())
            println()

        } catch (e: IllegalOperationException) {
            println("Illegal operation while analyzing: ${e.operation}\n")
        }
    }
}
