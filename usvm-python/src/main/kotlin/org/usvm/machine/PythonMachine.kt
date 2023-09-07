package org.usvm.machine

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.interpreters.USVMPythonInterpreter
import org.usvm.machine.model.toPyModel
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.utils.PythonMachineStatistics
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction
import org.usvm.memory.UMemory
import org.usvm.ps.DfsPathSelector
import org.usvm.solver.USatResult
import org.usvm.statistics.UMachineObserver
import org.usvm.utils.PythonObjectSerializer

class PythonMachine<PythonObjectRepresentation>(
    private val program: PythonProgram,
    private val typeSystem: PythonTypeSystem,
    private val serializer: PythonObjectSerializer<PythonObjectRepresentation>,
    private val printErrorMsg: Boolean = false
): UMachine<PythonExecutionState>() {
    private val ctx = UPythonContext(typeSystem)
    private val solver = ctx.solver<PythonType, UPythonContext>()
    val statistics = PythonMachineStatistics()

    private fun getInterpreter(
        target: PythonUnpinnedCallable,
        pinnedTarget: PythonPinnedCallable,
        results: MutableList<PythonAnalysisResult<PythonObjectRepresentation>>,
        allowPathDiversion: Boolean,
        iterationCounter: IterationCounter,
        maxInstructions: Int,
        isCancelled: (Long) -> Boolean
    ): USVMPythonInterpreter<PythonObjectRepresentation> =
        USVMPythonInterpreter(
            ctx,
            typeSystem,
            target,
            pinnedTarget,
            iterationCounter,
            printErrorMsg,
            PythonMachineStatisticsOnFunction(pinnedTarget).also { statistics.functionStatistics.add(it) },
            maxInstructions,
            isCancelled,
            allowPathDiversion,
            serializer
        ) {
            results.add(it)
        }

    private fun getInitialState(target: PythonUnpinnedCallable): PythonExecutionState {
        val pathConstraints = UPathConstraints<PythonType, UPythonContext>(ctx)
        val memory = UMemory<PythonType, PythonCallable>(
            ctx,
            pathConstraints.typeConstraints
        ).apply {
            stack.push(target.numberOfArguments)
        }
        val symbols = target.signature.mapIndexed { index, type ->
            constructInputObject(index, type, ctx, memory, pathConstraints, typeSystem)
        }
        val preAllocatedObjects = PreallocatedObjects.initialize(ctx, memory, pathConstraints, typeSystem)
        val solverRes = solver.check(pathConstraints)
        if (solverRes !is USatResult)
            error("Failed to construct initial model")
        return PythonExecutionState(
            ctx,
            target,
            symbols,
            pathConstraints,
            memory,
            solverRes.model.toPyModel(ctx, typeSystem),
            typeSystem,
            preAllocatedObjects
        ).also {
            it.meta.generatedFrom = "Initial state"
        }
    }

     private fun getPathSelector(target: PythonUnpinnedCallable): UPathSelector<PythonExecutionState> {
         val ps = PythonVirtualPathSelector(ctx, typeSystem, DfsPathSelector(), DfsPathSelector(), DfsPathSelector())
         val initialState = getInitialState(target)
         ps.add(listOf(initialState))
         return ps
    }

    fun analyze(
        pythonCallable: PythonUnpinnedCallable,
        results: MutableList<PythonAnalysisResult<PythonObjectRepresentation>>,
        maxIterations: Int = 300,
        allowPathDiversion: Boolean = true,
        maxInstructions: Int = 1_000_000_000,
        timeoutMs: Long? = null,
        timeoutPerRunMs: Long? = null
    ): Int = program.withPinnedCallable(pythonCallable, typeSystem) { pinnedCallable ->
        typeSystem.restart()
        val observer = PythonMachineObserver()
        val iterationCounter = IterationCounter()
        val startTime = System.currentTimeMillis()
        val stopTime = timeoutMs?.let { startTime + it }
        val interpreter = getInterpreter(
            pythonCallable,
            pinnedCallable,
            results,
            allowPathDiversion,
            iterationCounter,
            maxInstructions
        ) { startIterationTime ->
            (timeoutPerRunMs?.let {(System.currentTimeMillis() - startIterationTime) >= it} ?: false) ||
                    (stopTime != null && System.currentTimeMillis() >= stopTime)
        }
        val pathSelector = getPathSelector(pythonCallable)
        run(
            interpreter,
            pathSelector,
            observer = observer,
            isStateTerminated = { it.meta.modelDied },
            stopStrategy = {
                observer.stateCounter >= 1000 || iterationCounter.iterations >= maxIterations ||
                        (stopTime != null && System.currentTimeMillis() >= stopTime)
            }
        )
        iterationCounter.iterations
    }

    override fun close() {
        solver.close()
        ctx.close()
    }

    private class PythonMachineObserver(
        var stateCounter: Int = 0
    ): UMachineObserver<PythonExecutionState> {
        override fun onState(parent: PythonExecutionState, forks: Sequence<PythonExecutionState>) {
            super.onState(parent, forks)
            stateCounter += 1
        }
    }
}

data class IterationCounter(var iterations: Int = 0)

data class InputObject<PythonObjectRepresentation>(
    val asUExpr: InterpretedInputSymbolicPythonObject,
    val type: PythonType,
    val reprFromPythonObject: PythonObjectRepresentation
)

sealed class ExecutionResult<PythonObjectRepresentation>
class Success<PythonObjectRepresentation>(
    val output: PythonObjectRepresentation
): ExecutionResult<PythonObjectRepresentation>()

class Fail<PythonObjectRepresentation>(
    val exception: PythonObjectRepresentation
): ExecutionResult<PythonObjectRepresentation>()

data class PythonAnalysisResult<PythonObjectRepresentation>(
    val inputValueConverter: ConverterToPythonObject,
    val inputValues: List<InputObject<PythonObjectRepresentation>>,
    val result: ExecutionResult<PythonObjectRepresentation>
)