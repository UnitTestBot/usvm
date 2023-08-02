package org.usvm.interpreter

import org.usvm.language.SymbolForCPython
import org.usvm.language.VirtualPythonObject

object ConcretePythonInterpreter {
    private val pythonAdapter = CPythonAdapter()

    fun getNewNamespace(): PythonNamespace {
        val result = pythonAdapter.newNamespace
        if (result == 0L)
            throw CPythonExecutionException()
        return PythonNamespace(result)
    }

    fun addObjectToNamespace(namespace: PythonNamespace, pythonObject: PythonObject, name: String) {
        pythonAdapter.addName(namespace.address, pythonObject.address, name)
    }

    fun concreteRun(globals: PythonNamespace, code: String) {
        val result = pythonAdapter.concreteRun(globals.address, code)
        if (result != 0)
            throw CPythonExecutionException()
    }

    fun eval(globals: PythonNamespace, expr: String): PythonObject {
        val result = pythonAdapter.eval(globals.address, expr)
        if (result == 0L)
            throw CPythonExecutionException()
        return PythonObject(result)
    }

    private fun wrap(address: Long): PythonObject? {
        if (address == 0L)
            return null
        return PythonObject(address)
    }

    fun concreteRunOnFunctionRef(
        functionRef: PythonObject,
        concreteArgs: Collection<PythonObject>
    ): PythonObject {
        pythonAdapter.thrownException = 0L
        pythonAdapter.thrownExceptionType = 0L
        val result = pythonAdapter.concreteRunOnFunctionRef(
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray()
        )
        if (result == 0L)
            throw CPythonExecutionException(wrap(pythonAdapter.thrownException), wrap(pythonAdapter.thrownExceptionType))
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
        pythonAdapter.thrownException = 0L
        pythonAdapter.thrownExceptionType = 0L
        val result = pythonAdapter.concolicRun(
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray(),
            virtualArgs.map { it.address }.toLongArray(),
            Array(symbolicArgs.size) { symbolicArgs[it] },
            ctx,
            printErrorMsg
        )
        if (result == 0L)
            throw CPythonExecutionException(wrap(pythonAdapter.thrownException), wrap(pythonAdapter.thrownExceptionType))
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

    fun getNameOfPythonType(pythonObject: PythonObject): String {
        return pythonAdapter.getNameOfPythonType(pythonObject.address)
    }

    fun getPythonObjectType(pythonObject: PythonObject): PythonObject {
        return PythonObject(pythonAdapter.getPythonObjectType(pythonObject.address))
    }

    fun isJavaException(pythonObject: PythonObject): Boolean {
        return pythonAdapter.javaExceptionType == pythonAdapter.getPythonObjectType(pythonObject.address)
    }

    fun extractException(pythonObject: PythonObject): Throwable {
        require(isJavaException(pythonObject))
        return pythonAdapter.extractException(pythonObject.address)
    }

    fun allocateVirtualObject(virtualObject: VirtualPythonObject): PythonObject {
        val ref = pythonAdapter.allocateVirtualObject(virtualObject)
        if (ref == 0L)
            throw CPythonExecutionException()
        return PythonObject(ref)
    }

    fun makeList(elements: List<PythonObject>): PythonObject {
        return PythonObject(pythonAdapter.makeList(elements.map { it.address }.toLongArray()))
    }

    fun getIterableElements(iterable: PythonObject): List<PythonObject> {
        val addresses = pythonAdapter.getIterableElements(iterable.address)
        return addresses.map { PythonObject(it) }
    }

    private fun createTypeQuery(checkMethod: (Long) -> Int): (PythonObject) -> Boolean = { pythonObject ->
        val result = checkMethod(pythonObject.address)
        if (result < 0)
            error("Given Python object is not a type")
        result != 0
    }

    val typeHasNbBool = createTypeQuery { pythonAdapter.typeHasNbBool(it) }
    val typeHasNbInt = createTypeQuery { pythonAdapter.typeHasNbInt(it) }
    val typeHasMpSubscript = createTypeQuery { pythonAdapter.typeHasMpSubscript(it) }
    val typeHasTpRichcmp = createTypeQuery { pythonAdapter.typeHasTpRichcmp(it) }

    fun kill() {
        pythonAdapter.finalizePython()
    }

    init {
        pythonAdapter.initializePython()
    }
}

class CPythonExecutionException(
    val pythonExceptionValue: PythonObject? = null,
    val pythonExceptionType: PythonObject? = null
): Exception()
data class PythonObject(val address: Long)
data class PythonNamespace(val address: Long)

val emptyNamespace = ConcretePythonInterpreter.getNewNamespace()