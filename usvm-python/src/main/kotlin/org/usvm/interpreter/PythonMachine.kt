package org.usvm.interpreter

import org.usvm.UContext
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.URegisterRef
import org.usvm.constraints.UPathConstraints
import org.usvm.language.Attribute
import org.usvm.language.PythonCallable
import org.usvm.language.PythonProgram
import org.usvm.language.PythonType
import org.usvm.language.SymbolForCPython
import org.usvm.memory.UMemoryBase
import org.usvm.ps.DfsPathSelector

data class PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>(
    val inputValues: List<PYTHON_OBJECT_REPRESENTATION>,
    val result: PYTHON_OBJECT_REPRESENTATION?
)

class PythonMachine<PYTHON_OBJECT_REPRESENTATION>(
    private val program: PythonProgram,
    private val pythonObjectSerialization: (PythonObject) -> PYTHON_OBJECT_REPRESENTATION
): UMachine<PythonExecutionState, PythonCallable>() {
    private val ctx = UContext(PythonComponents)
    private val solver = ctx.solver<Attribute, PythonType, PythonCallable>()
    private val iterationCounter = IterationCounter()
    val results = mutableListOf<PythonAnalysisResult<PYTHON_OBJECT_REPRESENTATION>>()
    override fun getInterpreter(target: PythonCallable): USVMPythonInterpreter<PYTHON_OBJECT_REPRESENTATION> =
        USVMPythonInterpreter(ctx, program, target, iterationCounter, pythonObjectSerialization) { inputs, result ->
            results.add(PythonAnalysisResult(inputs, result))
        }

    private fun getInitialState(target: PythonCallable): PythonExecutionState {
        val pathConstraints = UPathConstraints<PythonType>(ctx)
        val memory = UMemoryBase<Attribute, PythonType, PythonCallable>(
            ctx,
            pathConstraints.typeConstraints
        ).apply {
            stack.push(target.numberOfArguments)
        }
        val symbols = List(target.numberOfArguments) { SymbolForCPython(memory.read(URegisterRef(ctx.intSort, it))) }
        return PythonExecutionState(
            ctx,
            target,
            symbols,
            pathConstraints,
            memory,
            listOf(solver.emptyModel())
        )
    }

    override fun getPathSelector(target: PythonCallable): UPathSelector<PythonExecutionState> {
        val ps = DfsPathSelector<PythonExecutionState>()
        val initialState = getInitialState(target)
        ps.add(sequenceOf(initialState))
        return ps
    }

    fun analyze(pythonCallable: PythonCallable): Int {
        var cnt = 0
        results.clear()
        run(
            pythonCallable,
            onState = { cnt += 1 },
            continueAnalyzing = { !it.wasExecuted },
            shouldStop = { cnt >= 10000 }
        )
        return iterationCounter.iterations
    }

    override fun close() {
        solver.close()
        ctx.close()
    }
}

data class IterationCounter(var iterations: Int = 0)