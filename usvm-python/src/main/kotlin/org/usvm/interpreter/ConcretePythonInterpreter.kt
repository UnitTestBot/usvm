package org.usvm.interpreter

import org.usvm.language.SymbolForCPython
import org.usvm.language.VirtualPythonObject

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
        functionRef: PythonObject,
        concreteArgs: Collection<PythonObject>
    ): PythonObject {
        val result = pythonAdapter.concreteRunOnFunctionRef(
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray()
        )
        if (result == 0L)
            throw CPythonExecutionException
        return PythonObject(result)
    }

    fun concolicRun(
        functionRef: PythonObject,
        concreteArgs: List<PythonObject>,
        virtualArgs: Collection<PythonObject>,
        symbolicArgs: List<SymbolForCPython>,
        ctx: ConcolicRunContext,
        printErrorMsg: Boolean = false
    ): PythonObject {
        val result = pythonAdapter.concolicRun(
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray(),
            virtualArgs.map { it.address }.toLongArray(),
            Array(symbolicArgs.size) { symbolicArgs[it] },
            ctx,
            printErrorMsg
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

    fun allocateVirtualObject(virtualObject: VirtualPythonObject): PythonObject {
        val ref = pythonAdapter.allocateVirtualObject(virtualObject)
        if (ref == 0L)
            throw CPythonExecutionException
        return PythonObject(ref);
    }

    fun makeList(elements: List<PythonObject>): PythonObject {
        return PythonObject(pythonAdapter.makeList(elements.map { it.address }.toLongArray()))
    }

    fun typeHasNbBool(pythonObject: PythonObject): Boolean {
        val result = pythonAdapter.typeHasNbBool(pythonObject.address)
        if (result < 0)
            error("Given Python object is not a type")
        return result != 0
    }

    fun typeHasNbInt(pythonObject: PythonObject): Boolean {
        val result = pythonAdapter.typeHasNbInt(pythonObject.address)
        if (result < 0)
            error("Given Python object is not a type")
        return result != 0
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

val emptyNamespace = ConcretePythonInterpreter.getNewNamespace()