package org.usvm.interpreter

import org.usvm.language.Symbol

object ConcretePythonInterpreter {
    private val pythonAdapter = CPythonAdapter()

    fun getNewNamespace(): PythonNamespace {
        val result = pythonAdapter.newNamespace
        if (result == 0L)
            throw CPythonExecutionException
        return PythonNamespace(result)
    }

    fun concreteRun(globals: PythonNamespace, code: String) {
        val result = pythonAdapter.concreteRun(globals.address, code)
        if (result != 0)
            throw CPythonExecutionException
    }

    fun eval(globals: PythonNamespace, expr: String): PythonObject {
        val result = pythonAdapter.eval(globals.address, expr)
        if (result == 0L)
            throw CPythonExecutionException
        return PythonObject(result)
    }

    fun concolicRun(globals: PythonNamespace, functionRef: PythonObject, concreteArgs: Collection<PythonObject>,
                    symbolicArgs: Array<Symbol>, stepScope: PythonStepScope) {
        val result = pythonAdapter.concolicRun(
            globals.address,
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray(),
            symbolicArgs,
            ConcolicRunContext(stepScope)
        )
        if (result != 0)
            throw CPythonExecutionException
    }

    fun kill() {
        pythonAdapter.finalizePython()
    }

    init {
        pythonAdapter.initializePython()
    }
}

// object CPythonInitializationException: Exception()
object CPythonExecutionException: Exception()
data class PythonObject(val address: Long)
data class PythonNamespace(val address: Long)