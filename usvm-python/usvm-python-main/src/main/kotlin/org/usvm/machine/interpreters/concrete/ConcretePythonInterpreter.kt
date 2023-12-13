package org.usvm.machine.interpreters.concrete

import org.usvm.annotations.ids.ApproximationId
import org.usvm.annotations.ids.SymbolicMethodId
import org.usvm.language.SymbolForCPython
import org.usvm.language.VirtualPythonObject
import org.usvm.interpreter.CPythonAdapter
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.MemberDescriptor
import org.usvm.machine.interpreters.symbolic.SymbolicClonesOfGlobals
import org.usvm.machine.interpreters.concrete.venv.VenvConfig
import org.usvm.machine.interpreters.concrete.venv.activateThisScript
import org.usvm.machine.utils.withAdditionalPaths
import java.io.File

@Suppress("unused")
object ConcretePythonInterpreter {
    private val pythonAdapter = CPythonAdapter()

    fun getNewNamespace(): PythonNamespace {
        val result = pythonAdapter.newNamespace
        if (result == 0L)
            throw CPythonExecutionException()
        return PythonNamespace(result)
    }

    fun pythonExceptionOccurred(): Boolean = CPythonAdapter.pythonExceptionOccurred() != 0

    fun addObjectToNamespace(namespace: PythonNamespace, pythonObject: PythonObject, name: String) {
        pythonAdapter.addName(namespace.address, pythonObject.address, name)
    }

    fun concreteRun(globals: PythonNamespace, code: String, printErrorMsg: Boolean = false, setHook: Boolean = false) {
        val result = pythonAdapter.concreteRun(globals.address, code, printErrorMsg, setHook)
        if (result != 0) {
            val op = if (setHook) pythonAdapter.checkForIllegalOperation() else null
            if (op != null)
                throw IllegalOperationException(op)
            else
                throw CPythonExecutionException()
        }
    }

    fun eval(globals: PythonNamespace, expr: String, printErrorMsg: Boolean = false, setHook: Boolean = false): PythonObject {
        val result = pythonAdapter.eval(globals.address, expr, printErrorMsg, setHook)
        if (result == 0L) {
            val op = if (setHook) pythonAdapter.checkForIllegalOperation() else null
            if (op != null)
                throw IllegalOperationException(op)
            else
                throw CPythonExecutionException()
        }
        return PythonObject(result)
    }

    private fun wrap(address: Long): PythonObject? {
        if (address == 0L)
            return null
        return PythonObject(address)
    }

    fun concreteRunOnFunctionRef(
        functionRef: PythonObject,
        concreteArgs: Collection<PythonObject>,
        setHook: Boolean = false
    ): PythonObject {
        pythonAdapter.thrownException = 0L
        pythonAdapter.thrownExceptionType = 0L
        val result = pythonAdapter.concreteRunOnFunctionRef(
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray(),
            setHook
        )
        if (result != 0L)
            return PythonObject(result)

        val op = if (setHook) pythonAdapter.checkForIllegalOperation() else null
        if (op != null)
            throw IllegalOperationException(op)
        else
            throw CPythonExecutionException(
                wrap(pythonAdapter.thrownException),
                wrap(pythonAdapter.thrownExceptionType)
            )
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
            SymbolicClonesOfGlobals.getNamedSymbols(),
            printErrorMsg
        )
        if (result != 0L)
            return PythonObject(result)

        val op = pythonAdapter.checkForIllegalOperation()
        if (op != null)
            throw IllegalOperationException(op)

        throw CPythonExecutionException(wrap(pythonAdapter.thrownException), wrap(pythonAdapter.thrownExceptionType))
    }


    fun printPythonObject(pythonObject: PythonObject) {
        pythonAdapter.printPythonObject(pythonObject.address)
    }

    fun getPythonObjectRepr(pythonObject: PythonObject, printErrorMsg: Boolean = false): String {
        return pythonAdapter.getPythonObjectRepr(pythonObject.address, printErrorMsg) ?: throw CPythonExecutionException()
    }

    fun getPythonObjectStr(pythonObject: PythonObject): String {
        return pythonAdapter.getPythonObjectStr(pythonObject.address) ?: throw CPythonExecutionException()
    }

    fun getAddressOfReprFunction(pythonObject: PythonObject): Long {
        return pythonAdapter.getAddressOfReprFunction(pythonObject.address)
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

    fun allocateTuple(size: Int): PythonObject {
        return PythonObject(pythonAdapter.allocateTuple(size))
    }

    fun setTupleElement(tuple: PythonObject, index: Int, elem: PythonObject) {
        pythonAdapter.setTupleElement(tuple.address, index, elem.address)
    }

    fun getIterableElements(iterable: PythonObject): List<PythonObject> {
        val addresses = pythonAdapter.getIterableElements(iterable.address)
        return addresses.map { PythonObject(it) }
    }

    fun decref(obj: PythonObject) {
        pythonAdapter.decref(obj.address)
    }

    fun incref(obj: PythonObject) {
        pythonAdapter.incref(obj.address)
    }

    fun decref(namespace: PythonNamespace) {
        pythonAdapter.decref(namespace.address)
    }

    fun typeLookup(type: PythonObject, name: String): PythonObject? {
        val result = pythonAdapter.typeLookup(type.address, name)
        return if (result == 0L) null else PythonObject(result)
    }

    fun getSymbolicDescriptor(concreteDescriptor: PythonObject): MemberDescriptor? {
        return pythonAdapter.getSymbolicDescriptor(concreteDescriptor.address)
    }

    fun constructPartiallyAppliedSymbolicMethod(self: SymbolForCPython?, id: SymbolicMethodId): SymbolForCPython {
        val ref = pythonAdapter.constructPartiallyAppliedSymbolicMethod(self, id.cRef)
        require(ref != 0L)
        return SymbolForCPython(null, ref)
    }

    fun constructApproximation(self: SymbolForCPython?, id: ApproximationId): SymbolForCPython {
        val ref = pythonAdapter.constructApproximation(self, 0, id.cRef)
        require(ref != 0L)
        return SymbolForCPython(null, ref)
    }

    fun constructPartiallyAppliedPythonMethod(self: SymbolForCPython): SymbolForCPython {
        val ref = pythonAdapter.constructPartiallyAppliedPythonMethod(self)
        require(ref != 0L)
        return SymbolForCPython(null, ref)
    }

    private fun createTypeQuery(checkMethod: (Long) -> Int): (PythonObject) -> Boolean = { pythonObject ->
        val result = checkMethod(pythonObject.address)
        if (result < 0)
            error("Given Python object is not a type")
        result != 0
    }

    val typeHasNbBool = createTypeQuery { pythonAdapter.typeHasNbBool(it) }
    val typeHasNbInt = createTypeQuery { pythonAdapter.typeHasNbInt(it) }
    val typeHasNbIndex = createTypeQuery { pythonAdapter.typeHasNbIndex(it) }
    val typeHasNbAdd = createTypeQuery { pythonAdapter.typeHasNbAdd(it) }
    val typeHasNbSubtract = createTypeQuery { pythonAdapter.typeHasNbSubtract(it) }
    val typeHasNbMultiply = createTypeQuery { pythonAdapter.typeHasNbMultiply(it) }
    val typeHasNbMatrixMultiply = createTypeQuery { pythonAdapter.typeHasNbMatrixMultiply(it) }
    val typeHasNbNegative = createTypeQuery { pythonAdapter.typeHasNbNegative(it) }
    val typeHasNbPositive = createTypeQuery { pythonAdapter.typeHasNbPositive(it) }
    val typeHasSqLength = createTypeQuery { pythonAdapter.typeHasSqLength(it) }
    val typeHasMpLength = createTypeQuery { pythonAdapter.typeHasMpLength(it) }
    val typeHasMpSubscript = createTypeQuery { pythonAdapter.typeHasMpSubscript(it) }
    val typeHasMpAssSubscript = createTypeQuery { pythonAdapter.typeHasMpAssSubscript(it) }
    val typeHasTpRichcmp = createTypeQuery { pythonAdapter.typeHasTpRichcmp(it) }
    val typeHasTpGetattro = createTypeQuery { pythonAdapter.typeHasTpGetattro(it) }
    val typeHasTpSetattro = createTypeQuery { pythonAdapter.typeHasTpSetattro(it) }
    val typeHasTpIter = createTypeQuery { pythonAdapter.typeHasTpIter(it) }
    val typeHasTpCall = createTypeQuery { pythonAdapter.typeHasTpCall(it) }
    val typeHasTpHash = createTypeQuery { pythonAdapter.typeHasTpHash(it) }
    val typeHasTpDescrGet = createTypeQuery { pythonAdapter.typeHasTpDescrGet(it) }
    val typeHasTpDescrSet = createTypeQuery { pythonAdapter.typeHasTpDescrSet(it) }
    val typeHasStandardNew = createTypeQuery { pythonAdapter.typeHasStandardNew(it) }
    val typeHasStandardTpGetattro = createTypeQuery { pythonAdapter.typeHasStandardTpGetattro(it) }
    val typeHasStandardTpSetattro = createTypeQuery { pythonAdapter.typeHasStandardTpSetattro(it) }

    fun callStandardNew(type: PythonObject): PythonObject {
        return PythonObject(pythonAdapter.callStandardNew(type.address))
    }

    fun typeHasStandardDict(type: PythonObject): Boolean {
        return typeHasStandardTpGetattro(type) && typeHasStandardTpSetattro(type) && typeHasStandardNew(type)
    }

    fun restart() {
        pythonAdapter.finalizePython()
        initialize()
        SymbolicClonesOfGlobals.restart()
        if (venvConfig != null)
            activateVenv(venvConfig!!)
    }

    fun setVenv(config: VenvConfig) {
        venvConfig = config
        activateVenv(config)
    }

    private val approximationsPath = System.getProperty("approximations.path") ?: error("approximations.path not specified")

    private fun initializeMethodApproximations() {
        withAdditionalPaths(listOf(File(approximationsPath)), null) {
            ApproximationId.values().forEach {
                val namespace = getNewNamespace()
                concreteRun(namespace, "import ${it.pythonModule}")
                val ref = eval(namespace, "${it.pythonModule}.${it.pythonName}")
                it.cRef = ref.address
                incref(ref)
                decref(namespace)
            }
            pythonAdapter.initializeSpecialApproximations()
        }
    }

    private fun initialize() {
        val pythonHome = System.getenv("PYTHONHOME") ?: error("Variable PYTHONHOME not set")
        pythonAdapter.initializePython(pythonHome)
        pyEQ = pythonAdapter.pyEQ
        pyNE = pythonAdapter.pyNE
        pyLT = pythonAdapter.pyLT
        pyLE = pythonAdapter.pyLE
        pyGT = pythonAdapter.pyGT
        pyGE = pythonAdapter.pyGE
        pyNoneRef = pythonAdapter.pyNoneRef
        val namespace = pythonAdapter.newNamespace
        pythonAdapter.concreteRun(
            namespace,
            """
                import sys
                sys.setrecursionlimit(1000)
            """.trimIndent(),
            true,
            false
        )
        initializeSysPath(namespace)
        pythonAdapter.decref(namespace)
        emptyNamespace = getNewNamespace()
        initializeMethodApproximations()
    }

    private fun initializeSysPath(namespace: Long) {
        val initialModules = listOf("sys", "copy", "builtins", "ctypes", "array")
        pythonAdapter.concreteRun(namespace, "import " + initialModules.joinToString(", "), true, false)
        initialSysPath = PythonObject(pythonAdapter.eval(namespace, "copy.copy(sys.path)", true, false))
        if (initialSysPath.address == 0L)
            throw CPythonExecutionException()
        initialSysModulesKeys = PythonObject(pythonAdapter.eval(namespace, "sys.modules.keys()", true, false))
        if (initialSysModulesKeys.address == 0L)
            throw CPythonExecutionException()
    }

    private fun activateVenv(config: VenvConfig) {
        val script = activateThisScript(config)
        val namespace = getNewNamespace()
        concreteRun(namespace, script, printErrorMsg = true)
        initializeSysPath(namespace.address)
        decref(namespace)
    }

    private var venvConfig: VenvConfig? = null
    lateinit var initialSysPath: PythonObject
    lateinit var  initialSysModulesKeys: PythonObject
    var pyEQ: Int = 0
    var pyNE: Int = 0
    var pyLT: Int = 0
    var pyLE: Int = 0
    var pyGT: Int = 0
    var pyGE: Int = 0
    var pyNoneRef: Long = 0L
    lateinit var emptyNamespace: PythonNamespace

    init {
        initialize()
    }

    fun printIdInfo() {  // for debugging
        println("SymbolicMethodId:")
        SymbolicMethodId.values().forEach {
            println(it)
            println(it.cRef)
        }
        println()
        println("ApproximationId:")
        ApproximationId.values().forEach {
            println(it)
            println(it.cRef)
        }
    }
}

class CPythonExecutionException(
    val pythonExceptionValue: PythonObject? = null,
    val pythonExceptionType: PythonObject? = null
): Exception()
data class PythonObject(val address: Long) {
    init {
        require(address != 0L)
    }
}
data class PythonNamespace(val address: Long) {
    init {
        require(address != 0L)
    }
}

data class IllegalOperationException(val operation: String): Exception()