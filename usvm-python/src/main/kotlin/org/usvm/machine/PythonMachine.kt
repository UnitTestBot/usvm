package org.usvm.machine

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.symbolicobjects.ConverterToPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.constructInputObject
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.USVMPythonInterpreter
import org.usvm.memory.UMemoryBase
import org.usvm.ps.DfsPathSelector
import org.usvm.solver.USatResult
import org.usvm.statistics.UMachineObserver

class PythonMachine<PYTHON_OBJECT_REPRESENTATION>(
    private val program: PythonProgram,
    private val typeSystem: PythonTypeSystem,
    private val printErrorMsg: Boolean = false,
    private val allowPathDiversion: Boolean = true,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION
): UMachine<PythonExecutionState>() {
    private val ctx = UPythonContext(typeSystem)
    private val solver = ctx.solver<PropertyOfPythonObject, PythonType, PythonCallable>()
    private val iterationCounter = IterationCounter()

    private fun getInterpreter(
        target: PythonUnpinnedCallable,
        results: MutableList<PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>>
    ): USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION> =
        USVMPythonInterpreter(
            ctx,
            typeSystem,
            program,
            target,
            iterationCounter,
            printErrorMsg,
            allowPathDiversion,
            pythonObjectSerialization
        ) {
            results.add(it)
        }

    private fun getInitialState(target: PythonUnpinnedCallable): PythonExecutionState {
        val pathConstraints = UPathConstraints<PythonType>(ctx)
        val memory = UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>(
            ctx,
            pathConstraints.typeConstraints
        ).apply {
            stack.push(target.numberOfArguments)
        }
        val symbols = target.signature.mapIndexed { index, type ->
            SymbolForCPython(constructInputObject(index, type, ctx, memory, pathConstraints))
        }
        val solverRes = solver.check(pathConstraints)
        if (solverRes !is USatResult)
            error("Failed to construct initial model")
        return PythonExecutionState(
            ctx,
            target,
            symbols,
            pathConstraints,
            memory,
            solverRes.model
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
        results: MutableList<PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>>,
        maxIterations: Int = 300
    ): Int {
        val observer = PythonMachineObserver()
        val interpreter = getInterpreter(pythonCallable, results)
        val pathSelector = getPathSelector(pythonCallable)
        run(
            interpreter,
            pathSelector,
            observer = observer,
            isStateTerminated = { it.meta.modelDied },
            stopStrategy = { observer.stateCounter >= 1000 || iterationCounter.iterations >= maxIterations }
        )
        return iterationCounter.iterations
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

data class InputObject<PYTHON_OBJECT_REPRESENTATION>(
    val asUExpr: InterpretedInputSymbolicPythonObject,
    val type: PythonType,
    val reprFromPythonObject: PYTHON_OBJECT_REPRESENTATION
)

sealed class ExecutionResult<PYTHON_OBJECT_REPRESENTATION>
class Success<PYTHON_OBJECT_REPRESENTATION>(
    val output: PYTHON_OBJECT_REPRESENTATION
): ExecutionResult<PYTHON_OBJECT_REPRESENTATION>()

class Fail<PYTHON_OBJECT_REPRESENTATION>(
    val exception: PYTHON_OBJECT_REPRESENTATION
): ExecutionResult<PYTHON_OBJECT_REPRESENTATION>()

data class PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>(
    val inputValueConverter: ConverterToPythonObject,
    val inputValues: List<InputObject<PYTHON_OBJECT_REPRESENTATION>>,
    val result: ExecutionResult<PYTHON_OBJECT_REPRESENTATION>
)