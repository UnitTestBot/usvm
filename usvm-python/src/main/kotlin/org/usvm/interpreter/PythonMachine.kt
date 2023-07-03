package org.usvm.interpreter

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.Attribute
import org.usvm.language.PythonCallable
import org.usvm.language.PythonProgram
import org.usvm.language.PythonType
import org.usvm.language.SymbolForCPython
import org.usvm.memory.UMemoryBase
import org.usvm.ps.DfsPathSelector
import org.usvm.statistics.UMachineObserver

class PythonMachine<PYTHON_OBJECT_REPRESENTATION>(
    private val program: PythonProgram,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION
): UMachine<PythonExecutionState>() {
    private val ctx = UContext(PythonComponents)
    private val solver = ctx.solver<Attribute, PythonType, PythonCallable>()
    private val iterationCounter = IterationCounter()
    private fun getInterpreter(
        target: PythonCallable,
        results: MutableList<PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>>
    ): USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION> =
        USVMPythonInterpreter(ctx, program, target, iterationCounter, pythonObjectSerialization) {
            results.add(it)
        }

    private fun getInitialState(target: PythonCallable): PythonExecutionState {
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

     fun getPathSelector(target: PythonCallable): UPathSelector<PythonExecutionState> {
        val ps = DfsPathSelector<PythonExecutionState>()
        val initialState = getInitialState(target)
        ps.add(listOf(initialState))
        return ps
    }

    fun analyze(
        pythonCallable: PythonCallable,
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