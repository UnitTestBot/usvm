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

class PythonMachine(
    private val program: PythonProgram,
): UMachine<PythonExecutionState, PythonCallable>() {
    private val ctx = UContext(PythonComponents)
    val solver = ctx.solver<Attribute, PythonType, PythonCallable>()
    private val iterationCounter = IterationCounter()
    override fun getInterpreter(target: PythonCallable): USVMPythonInterpreter =
        USVMPythonInterpreter(ctx, program, target, iterationCounter)

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