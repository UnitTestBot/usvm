package org.usvm.machine

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.language.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.USVMPythonInterpreter
import org.usvm.machine.model.toPyModel
import org.usvm.machine.saving.PythonAnalysisResultSaver
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.utils.PythonMachineStatistics
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction
import org.usvm.machine.utils.isGenerator
import org.usvm.machine.utils.unfoldGenerator
import org.usvm.memory.UMemory
import org.usvm.ps.DfsPathSelector
import org.usvm.solver.USatResult
import org.usvm.statistics.UMachineObserver

class PythonMachine(
    private val program: PythonProgram,
    private val typeSystem: PythonTypeSystem,
    private val printErrorMsg: Boolean = false
): UMachine<PythonExecutionState>() {
    private val ctx = UPythonContext(typeSystem)

    // private val random = Random(0)
    val statistics = PythonMachineStatistics()

    private fun <InputRepr> getInterpreter(
        target: PythonUnpinnedCallable,
        pinnedTarget: PythonPinnedCallable,
        saver: PythonAnalysisResultSaver<InputRepr>,
        allowPathDiversion: Boolean,
        iterationCounter: IterationCounter,
        maxInstructions: Int,
        isCancelled: (Long) -> Boolean
    ): USVMPythonInterpreter<InputRepr> =
        USVMPythonInterpreter(
            ctx,
            typeSystem,
            target,
            pinnedTarget,
            iterationCounter,
            printErrorMsg,
            PythonMachineStatisticsOnFunction(pinnedTarget).also { statistics.functionStatistics.add(it) },
            maxInstructions,
            saver,
            isCancelled,
            allowPathDiversion
        )

    private fun getInitialState(target: PythonUnpinnedCallable): PythonExecutionState {
        val pathConstraints = UPathConstraints<PythonType>(ctx)
        val memory = UMemory<PythonType, PythonCallable>(
            ctx,
            pathConstraints.typeConstraints
        ).apply {
            stack.push(target.numberOfArguments)
        }
        val preAllocatedObjects = PreallocatedObjects.initialize(ctx, memory, pathConstraints, typeSystem)
        val symbols = target.signature.mapIndexed { index, type ->
            constructInputObject(index, type, ctx, memory, pathConstraints, typeSystem)
        }
        val solverRes = ctx.solver<PythonType>().check(pathConstraints)
        if (solverRes !is USatResult)
            error("Failed to construct initial model")
        return PythonExecutionState(
            ctx,
            target,
            symbols,
            pathConstraints,
            memory,
            solverRes.model.toPyModel(ctx, pathConstraints),
            typeSystem,
            preAllocatedObjects
        ).also {
            it.meta.generatedFrom = "Initial state"
        }
    }

    private fun getPathSelector(
        target: PythonUnpinnedCallable,
        newStateObserver: NewStateObserver
    ): UPathSelector<PythonExecutionState> {
        val pathSelectorCreation = {
            DfsPathSelector<PythonExecutionState>()
            // createForkDepthPathSelector<PythonCallable, SymbolicHandlerEvent<Any>, PythonExecutionState>(random)
        }
        val initialState = getInitialState(target)
        val ps = PythonVirtualPathSelector(
            ctx,
            pathSelectorCreation(),
            pathSelectorForStatesWithDelayedForks = DfsPathSelector(),
            pathSelectorCreation(),
            newStateObserver
        )
        ps.add(listOf(initialState))
        return ps
    }

    fun <InputRepr> analyze(
        pythonCallable: PythonUnpinnedCallable,
        saver: PythonAnalysisResultSaver<InputRepr>,
        maxIterations: Int = 300,
        allowPathDiversion: Boolean = true,
        maxInstructions: Int = 1_000_000_000,
        timeoutMs: Long? = null,
        timeoutPerRunMs: Long? = null,
        unfoldGenerator: Boolean = true,
        newStateObserver: NewStateObserver = DummyNewStateObserver
    ): Int {
        if (pythonCallable.module != null && typeSystem is PythonTypeSystemWithMypyInfo) {
            typeSystem.resortTypes(pythonCallable.module)
        }
        return program.withPinnedCallable(pythonCallable, typeSystem) { rawPinnedCallable ->
            typeSystem.restart()
            val pinnedCallable = if (!unfoldGenerator || !isGenerator(rawPinnedCallable.asPythonObject)) {
                rawPinnedCallable
            } else {
                val substituted = unfoldGenerator(rawPinnedCallable.asPythonObject)
                PythonPinnedCallable(substituted)
            }
            val observer = PythonMachineObserver(newStateObserver)
            val iterationCounter = IterationCounter()
            val startTime = System.currentTimeMillis()
            val stopTime = timeoutMs?.let { startTime + it }
            val interpreter = getInterpreter(
                pythonCallable,
                pinnedCallable,
                saver,
                allowPathDiversion,
                iterationCounter,
                maxInstructions
            ) { startIterationTime ->
                (timeoutPerRunMs?.let { (System.currentTimeMillis() - startIterationTime) >= it } ?: false) ||
                        (stopTime != null && System.currentTimeMillis() >= stopTime)
            }
            val pathSelector = getPathSelector(pythonCallable, newStateObserver)
            run(
                interpreter,
                pathSelector,
                observer = observer,
                isStateTerminated = { !it.isInterestingForPathSelector() },
                stopStrategy = {
                    iterationCounter.iterations >= maxIterations ||
                            (stopTime != null && System.currentTimeMillis() >= stopTime)
                }
            )
            iterationCounter.iterations
        }.also {
            ConcretePythonInterpreter.restart()
            ctx.restartSolver()
        }
    }

    override fun close() {
        ctx.closeSolver()
        ctx.close()
    }

    private class PythonMachineObserver(
        val newStateObserver: NewStateObserver
    ): UMachineObserver<PythonExecutionState> {
        override fun onState(parent: PythonExecutionState, forks: Sequence<PythonExecutionState>) {
            super.onState(parent, forks)
            if (!parent.isTerminated())
                newStateObserver.onNewState(parent)
            forks.forEach {
                if (!it.isTerminated())
                    newStateObserver.onNewState(it)
            }
        }
    }
}

data class IterationCounter(var iterations: Int = 0)

abstract class NewStateObserver {
    abstract fun onNewState(state: PythonExecutionState)
}

object DummyNewStateObserver: NewStateObserver() {
    override fun onNewState(state: PythonExecutionState) = run {}
}