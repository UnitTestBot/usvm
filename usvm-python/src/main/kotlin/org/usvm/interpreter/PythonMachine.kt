package org.usvm.interpreter

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.memory.UMemoryBase
import org.usvm.ps.DfsPathSelector
import org.usvm.statistics.UMachineObserver

class PythonMachine<PYTHON_OBJECT_REPRESENTATION>(
    private val program: PythonProgram,
    private val printErrorMsg: Boolean = false,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION
): UMachine<PythonExecutionState>() {
    private val ctx = UContext(PythonComponents)
    private val solver = ctx.solver<Attribute, PythonType, PythonCallable>()
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
        val memory = UMemoryBase<Attribute, PythonType, PythonCallable>(
            ctx,
            pathConstraints.typeConstraints
        ).apply {
            stack.push(target.numberOfArguments)
        }
        val symbols = List(target.numberOfArguments) { SymbolForCPython(memory.read(URegisterLValue(ctx.intSort, it))) }
        return PythonExecutionState(
            ctx,
            target,
            symbols,
            pathConstraints,
            memory,
            listOf(solver.emptyModel())
        )
    }

     private fun getPathSelector(target: PythonUnpinnedCallable): UPathSelector<PythonExecutionState> {
        val ps = DfsPathSelector<PythonExecutionState>()
        val initialState = getInitialState(target)
        ps.add(listOf(initialState))
        return ps
    }

    fun analyze(
        pythonCallable: PythonUnpinnedCallable,
        results: MutableList<PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>>
    ): Int {
        val observer = PythonMachineObserver()
        run(
            getInterpreter(pythonCallable, results),
            getPathSelector(pythonCallable),
            observer = observer,
            isStateTerminated = { it.wasExecuted },
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
    val asUExpr: UExpr<*>,
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