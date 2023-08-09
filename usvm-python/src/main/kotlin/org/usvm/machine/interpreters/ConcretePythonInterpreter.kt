package org.usvm.machine.interpreters

import org.usvm.language.SymbolForCPython
import org.usvm.language.VirtualPythonObject
import org.usvm.interpreter.CPythonAdapter
import org.usvm.interpreter.ConcolicRunContext

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

    fun decref(obj: PythonObject) {
        pythonAdapter.decref(obj.address)
    }

    fun decref(namespace: PythonNamespace) {
        pythonAdapter.decref(namespace.address)
    }

    private fun createTypeQuery(checkMethod: (Long) -> Int): (PythonObject) -> Boolean = { pythonObject ->
        val result = checkMethod(pythonObject.address)
        if (result < 0)
            error("Given Python object is not a type")
        result != 0
    }

    val typeHasNbBool = createTypeQuery { pythonAdapter.typeHasNbBool(it) }
    val typeHasNbInt = createTypeQuery { pythonAdapter.typeHasNbInt(it) }
    val typeHasNbAdd = createTypeQuery { pythonAdapter.typeHasNbAdd(it) }
    val typeHasNbMultiply = createTypeQuery { pythonAdapter.typeHasNbMultiply(it) }
    val typeHasSqLength = createTypeQuery { pythonAdapter.typeHasSqLength(it) }
    val typeHasMpLength = createTypeQuery { pythonAdapter.typeHasMpLength(it) }
    val typeHasMpSubscript = createTypeQuery { pythonAdapter.typeHasMpSubscript(it) }
    val typeHasMpAssSubscript = createTypeQuery { pythonAdapter.typeHasMpAssSubscript(it) }
    val typeHasTpRichcmp = createTypeQuery { pythonAdapter.typeHasTpRichcmp(it) }
    val typeHasTpIter = createTypeQuery { pythonAdapter.typeHasTpIter(it) }

    fun kill() {
        pythonAdapter.finalizePython()
    }

    val initialSysPath: PythonObject
    val initialSysModules: PythonObject

    init {
        pythonAdapter.initializePython()
        val namespace = pythonAdapter.newNamespace
        pythonAdapter.concreteRun(namespace, "import sys, copy")
        initialSysPath = PythonObject(pythonAdapter.eval(namespace, "copy.copy(sys.path)"))
        initialSysModules = PythonObject(pythonAdapter.eval(namespace, "copy.copy(sys.modules)"))
        pythonAdapter.decref(namespace)
    }
}

class CPythonExecutionException(
    val pythonExceptionValue: PythonObject? = null,
    val pythonExceptionType: PythonObject? = null
): Exception()
data class PythonObject(val address: Long)
data class PythonNamespace(val address: Long)

val emptyNamespace = ConcretePythonInterpreter.getNewNamespace()