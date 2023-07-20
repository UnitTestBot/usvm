package org.usvm.interpreter

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.interpreter.symbolicobjects.ConverterToPythonObject
import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.interpreter.symbolicobjects.constructInputObject
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.memory.UMemoryBase
import org.usvm.ps.DfsPathSelector
import org.usvm.solver.USatResult
import org.usvm.statistics.UMachineObserver

class PythonMachine<PYTHON_OBJECT_REPRESENTATION>(
    program: PythonProgram,
    private val printErrorMsg: Boolean = false,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION
): UMachine<PythonExecutionState>() {
    private val ctx = UContext(PythonComponents)
    private val solver = ctx.solver<PropertyOfPythonObject, PythonType, PythonCallable>()
    private val iterationCounter = IterationCounter()
    private val namespace = ConcretePythonInterpreter.getNewNamespace()

    init {
        ConcretePythonInterpreter.concreteRun(namespace, program.asString)
    }

    private fun getInterpreter(
        target: PythonUnpinnedCallable,
        results: MutableList<PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>>
    ): USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION> =
        USVMPythonInterpreter(ctx, namespace, target, iterationCounter, printErrorMsg, pythonObjectSerialization) {
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
        val solverRes = solver.check(pathConstraints, useSoftConstraints = false)
        if (solverRes !is USatResult)
            error("Failed to construct initial model")
        return PythonExecutionState(
            ctx,
            target,
            symbols,
            pathConstraints,
            memory,
            solverRes.model
        )
    }

     private fun getPathSelector(target: PythonUnpinnedCallable): UPathSelector<PythonExecutionState> {
         val ps = PythonVirtualPathSelector(DfsPathSelector(), DfsPathSelector(), DfsPathSelector())
         val initialState = getInitialState(target)
         ps.add(listOf(initialState))
         return ps
    }

    fun analyze(
        pythonCallable: PythonUnpinnedCallable,
        results: MutableList<PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>>
    ): Int {
        val observer = PythonMachineObserver()
        val interpreter = getInterpreter(pythonCallable, results)
        val pathSelector = getPathSelector(pythonCallable)
        run(
            interpreter,
            pathSelector,
            observer = observer,
            isStateTerminated = { it.modelDied },
            stopStrategy = { observer.stateCounter >= 10000 }
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
    val asUExpr: InterpretedSymbolicPythonObject,
    val type: PythonType,
    val reprFromPythonObject: PYTHON_OBJECT_REPRESENTATION
)

sealed class ExecutionResult<PYTHON_OBJECT_REPRESENTATION>
class Success<PYTHON_OBJECT_REPRESENTATION>(
    val output: PYTHON_OBJECT_REPRESENTATION
): ExecutionResult<PYTHON_OBJECT_REPRESENTATION>()

class Fail<PYTHON_OBJECT_REPRESENTATION>: ExecutionResult<PYTHON_OBJECT_REPRESENTATION>()

data class PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>(
    val inputValueConverter: ConverterToPythonObject,
    val inputValues: List<InputObject<PYTHON_OBJECT_REPRESENTATION>>,
    val result: ExecutionResult<PYTHON_OBJECT_REPRESENTATION>
)