package org.usvm.interpreter

import org.usvm.language.SymbolForCPython

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

    fun concreteRunOnFunctionRef(
        globals: PythonNamespace,
        functionRef: PythonObject,
        concreteArgs: Collection<PythonObject>
    ): PythonObject {
        val result = pythonAdapter.concreteRunOnFunctionRef(
            globals.address,
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray()
        )
        if (result == 0L)
            throw CPythonExecutionException
        return PythonObject(result)
    }

    fun concolicRun(globals: PythonNamespace, functionRef: PythonObject, concreteArgs: Collection<PythonObject>,
                    symbolicArgs: List<SymbolForCPython>, ctx: ConcolicRunContext): PythonObject {
        val result = pythonAdapter.concolicRun(
            globals.address,
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray(),
            Array(symbolicArgs.size) { symbolicArgs[it] },
            ctx
        )
        if (result == 0L)
            throw CPythonExecutionException
        return PythonObject(result)
    }

    fun printPythonObject(pythonObject: PythonObject) {
        pythonAdapter.printPythonObject(pythonObject.address)
    }

    fun getPythonObjectRepr(pythonObject: PythonObject): String {
        return pythonAdapter.getPythonObjectRepr(pythonObject.address)
    }

    fun getPythonObjectTypeName(pythonObject: PythonObject): String {
        return pythonAdapter.getPythonObjectTypeName(pythonObject.address)
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