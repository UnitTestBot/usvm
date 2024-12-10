package org.usvm.machine

import mu.KLogging
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.language.PyCallable
import org.usvm.language.PyPinnedCallable
import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.symbolic.USVMPythonInterpreter
import org.usvm.machine.model.GenerateNewFromPathConstraints
import org.usvm.machine.model.toPyModel
import org.usvm.machine.ps.createPyPathSelector
import org.usvm.machine.results.PyMachineResultsReceiver
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.symbolicobjects.PreallocatedObjects
import org.usvm.machine.symbolicobjects.constructInputObject
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.utils.PyMachineStatistics
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction
import org.usvm.machine.utils.isGenerator
import org.usvm.machine.utils.pyDebugProfileObserver
import org.usvm.machine.utils.unfoldGenerator
import org.usvm.memory.UMemory
import org.usvm.python.ps.PyPathSelectorType
import org.usvm.solver.USatResult
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.UMachineObserver
import kotlin.random.Random

class PyMachine(
    private val program: PyProgram,
    private val typeSystem: PythonTypeSystem,
    private val pathSelectorType: PyPathSelectorType =
        PyPathSelectorType.DelayedForkByInstructionPriorityNumberOfInstructionsRandomTreePlusTypeRating,
    private val printErrorMsg: Boolean = false,
) : UMachine<PyState>() {
    private val ctx = PyContext(typeSystem)

    private val random = Random(0)
    val statistics = PyMachineStatistics()

    private fun <PyObjectRepr> getInterpreter(
        pinnedTarget: PyPinnedCallable,
        saver: PyMachineResultsReceiver<PyObjectRepr>,
        allowPathDiversion: Boolean,
        maxInstructions: Int,
        isCancelled: (Long) -> Boolean,
    ): USVMPythonInterpreter<PyObjectRepr> =
        USVMPythonInterpreter(
            ctx,
            pinnedTarget,
            printErrorMsg,
            PythonMachineStatisticsOnFunction(pinnedTarget).also { statistics.functionStatistics.add(it) },
            maxInstructions,
            saver,
            isCancelled,
            allowPathDiversion
        )

    private fun getInitialState(target: PyUnpinnedCallable): PyState {
        val initOwnership = MutabilityOwnership()
        val pathConstraints = PyPathConstraints(ctx, initOwnership)
        val memory = UMemory<PythonType, PyCallable>(
            ctx,
            initOwnership,
            pathConstraints.typeConstraints,
        ).apply {
            stack.push(target.numberOfArguments)
        }
        val preAllocatedObjects = PreallocatedObjects.initialize(ctx, memory, pathConstraints, typeSystem)
        val symbols = target.signature.mapIndexed { index, type ->
            constructInputObject(index, type, ctx, memory, pathConstraints, typeSystem)
        }
        val solverRes = ctx.solver<PythonType>().check(pathConstraints)
        if (solverRes !is USatResult) {
            error("Failed to construct initial model")
        }

        return PyState(
            ctx,
            initOwnership,
            target,
            symbols,
            pathConstraints,
            memory,
            solverRes.model.toPyModel(ctx, GenerateNewFromPathConstraints(pathConstraints)),
            typeSystem,
            preAllocatedObjects
        ).also {
            it.generatedFrom = "Initial state"
        }
    }

    private fun getPathSelector(
        target: PyUnpinnedCallable,
        newStateObserver: NewStateObserver,
    ): UPathSelector<PyState> {
        val initialState = getInitialState(target)
        newStateObserver.onNewState(initialState)
        return createPyPathSelector(initialState, pathSelectorType, ctx, random, newStateObserver)
    }

    fun <PyObjectRepr> analyze(
        pythonCallable: PyUnpinnedCallable,
        saver: PyMachineResultsReceiver<PyObjectRepr>,
        maxIterations: Int = 300,
        allowPathDiversion: Boolean = true,
        maxInstructions: Int = 1_000_000_000,
        timeoutMs: Long? = null,
        timeoutPerRunMs: Long? = null,
        unfoldGenerator: Boolean = true,
    ): Int {
        if (pythonCallable.module != null && typeSystem is PythonTypeSystemWithMypyInfo) {
            typeSystem.resortTypes(pythonCallable.module)
        }
        typeSystem.restart()

        return program.withPinnedCallable(pythonCallable, typeSystem) { rawPinnedCallable ->

            val pinnedCallable = if (!unfoldGenerator || !isGenerator(rawPinnedCallable.pyObject)) {
                rawPinnedCallable
            } else {
                val substituted = unfoldGenerator(rawPinnedCallable.pyObject)
                PyPinnedCallable(substituted)
            }

            val pyObserver = PythonMachineObserver(saver.newStateObserver)
            val observers: MutableList<UMachineObserver<PyState>> = mutableListOf(pyObserver)

            if (logger.isDebugEnabled) {
                observers.add(pyDebugProfileObserver)
            }

            val startTime = System.currentTimeMillis()
            val stopTime = timeoutMs?.let { startTime + it }

            val interpreter = getInterpreter(
                pinnedCallable,
                saver,
                allowPathDiversion,
                maxInstructions
            ) { startIterationTime ->
                (timeoutPerRunMs?.let { (System.currentTimeMillis() - startIterationTime) >= it } ?: false) ||
                    (stopTime != null && System.currentTimeMillis() >= stopTime)
            }

            val pathSelector = getPathSelector(pythonCallable, saver.newStateObserver)

            run(
                interpreter,
                pathSelector,
                observer = CompositeUMachineObserver(observers),
                isStateTerminated = { !it.isInterestingForPathSelector() },
                stopStrategy = {
                    pyObserver.iterations >= maxIterations ||
                        (stopTime != null && System.currentTimeMillis() >= stopTime)
                }
            )

            pyObserver.iterations
        }.also {
            ConcretePythonInterpreter.restart()
        }
    }

    override fun close() {
        ctx.close()
    }

    private class PythonMachineObserver(
        val newStateObserver: NewStateObserver,
    ) : UMachineObserver<PyState> {
        var iterations: Int = 0
        override fun onState(parent: PyState, forks: Sequence<PyState>) {
            super.onState(parent, forks)
            iterations += 1
            if (!parent.isTerminated()) {
                newStateObserver.onNewState(parent)
            }
            forks.forEach {
                if (!it.isTerminated()) {
                    newStateObserver.onNewState(it)
                }
            }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
