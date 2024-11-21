package org.usvm.machine.interpreters.concrete

import org.usvm.annotations.ids.ApproximationId
import org.usvm.annotations.ids.NativeId
import org.usvm.annotations.ids.SymbolicMethodId
import org.usvm.interpreter.CPythonAdapter
import org.usvm.machine.CPythonExecutionException
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython
import org.usvm.machine.interpreters.concrete.utils.VirtualPythonObject
import org.usvm.machine.interpreters.concrete.venv.VenvConfig
import org.usvm.machine.interpreters.concrete.venv.activateThisScript
import org.usvm.machine.interpreters.symbolic.SymbolicClonesOfGlobals
import org.usvm.machine.interpreters.symbolic.operations.descriptors.MemberDescriptor
import org.usvm.machine.utils.withAdditionalPaths
import java.io.File

@Suppress("unused")
object ConcretePythonInterpreter {
    private val pythonAdapter = CPythonAdapter()

    fun getNewNamespace(): PyNamespace {
        val result = pythonAdapter.newNamespace
        if (result == 0L) {
            throw CPythonExecutionException()
        }
        return PyNamespace(result)
    }

    fun pythonExceptionOccurred(): Boolean = CPythonAdapter.pythonExceptionOccurred() != 0

    fun addObjectToNamespace(namespace: PyNamespace, pyObject: PyObject, name: String) {
        pythonAdapter.addName(namespace.address, pyObject.address, name)
    }

    fun concreteRun(globals: PyNamespace, code: String, printErrorMsg: Boolean = false, setHook: Boolean = false) {
        val result = pythonAdapter.concreteRun(globals.address, code, printErrorMsg, setHook)
        if (result != 0) {
            val op = if (setHook) pythonAdapter.checkForIllegalOperation() else null
            if (op != null) {
                throw IllegalOperationException(op)
            } else {
                throw CPythonExecutionException()
            }
        }
    }

    fun eval(globals: PyNamespace, expr: String, printErrorMsg: Boolean = false, setHook: Boolean = false): PyObject {
        val result = pythonAdapter.eval(globals.address, expr, printErrorMsg, setHook)
        if (result == 0L) {
            val op = if (setHook) pythonAdapter.checkForIllegalOperation() else null
            if (op != null) {
                throw IllegalOperationException(op)
            } else {
                throw CPythonExecutionException()
            }
        }
        return PyObject(result)
    }

    private fun wrap(address: Long): PyObject? {
        if (address == 0L) {
            return null
        }
        return PyObject(address)
    }

    fun concreteRunOnFunctionRef(
        functionRef: PyObject,
        concreteArgs: Collection<PyObject>,
        setHook: Boolean = false,
    ): PyObject {
        pythonAdapter.thrownException = 0L
        pythonAdapter.thrownExceptionType = 0L
        val result = pythonAdapter.concreteRunOnFunctionRef(
            functionRef.address,
            concreteArgs.map { it.address }.toLongArray(),
            setHook
        )
        if (result != 0L) {
            return PyObject(result)
        }

        val op = if (setHook) pythonAdapter.checkForIllegalOperation() else null
        if (op != null) {
            throw IllegalOperationException(op)
        } else {
            throw CPythonExecutionException(
                wrap(pythonAdapter.thrownException),
                wrap(pythonAdapter.thrownExceptionType)
            )
        }
    }

    fun concolicRun(
        functionRef: PyObject,
        concreteArgs: List<PyObject>,
        virtualArgs: List<PyObject>,
        symbolicArgs: List<SymbolForCPython>,
        ctx: ConcolicRunContext,
        printErrorMsg: Boolean = false,
    ): PyObject {
        pythonAdapter.thrownException = 0L
        pythonAdapter.thrownExceptionType = 0L
        val result = pythonAdapter.concolicRun(
            functionRef.address,
            LongArray(concreteArgs.size) { concreteArgs[it].address },
            LongArray(virtualArgs.size) { virtualArgs[it].address },
            Array(symbolicArgs.size) { symbolicArgs[it] },
            ctx,
            SymbolicClonesOfGlobals.getNamedSymbols(),
            printErrorMsg
        )
        if (result != 0L) {
            return PyObject(result)
        }

        val op = pythonAdapter.checkForIllegalOperation()
        if (op != null) {
            throw IllegalOperationException(op)
        }

        throw CPythonExecutionException(wrap(pythonAdapter.thrownException), wrap(pythonAdapter.thrownExceptionType))
    }


    fun printPythonObject(pyObject: PyObject) {
        pythonAdapter.printPythonObject(pyObject.address)
    }

    fun getPythonObjectRepr(pyObject: PyObject, printErrorMsg: Boolean = false): String {
        return pythonAdapter.getPythonObjectRepr(pyObject.address, printErrorMsg) ?: throw CPythonExecutionException()
    }

    fun getPythonObjectStr(pyObject: PyObject): String {
        return pythonAdapter.getPythonObjectStr(pyObject.address) ?: throw CPythonExecutionException()
    }

    fun getAddressOfReprFunction(pyObject: PyObject): Long {
        return pythonAdapter.getAddressOfReprFunction(pyObject.address)
    }

    fun getPythonObjectTypeName(pyObject: PyObject): String {
        return pythonAdapter.getPythonObjectTypeName(pyObject.address)
    }

    fun getNameOfPythonType(pyObject: PyObject): String {
        return pythonAdapter.getNameOfPythonType(pyObject.address)
    }

    fun getPythonObjectType(pyObject: PyObject): PyObject {
        return PyObject(pythonAdapter.getPythonObjectType(pyObject.address))
    }

    fun isJavaException(pyObject: PyObject): Boolean {
        return pythonAdapter.javaExceptionType == pythonAdapter.getPythonObjectType(pyObject.address)
    }

    fun extractException(pyObject: PyObject): Throwable {
        require(isJavaException(pyObject))
        return pythonAdapter.extractException(pyObject.address)
    }

    fun allocateVirtualObject(virtualObject: VirtualPythonObject): PyObject {
        /*
         * Usage example:
         * pythonAdapter.allocateRawVirtualObject(virtualObject, mask), where
         * Mask is a sequence of bits, written in the reverse order and
         * packed into a ByteArray
         * (ABCDEFGHIJ -> {000000JI, HGFEDCBA})
         * So, THE LAST bit in the ByteArray (A) enables THE FIRST slot from the list.
         *
         * pythonAdapter.allocateRawVirtualObjectWithAllSlots(object) does exactly the same as
         * pythonAdapter.allocateRawVirtualObject(virtualObject, List(12) {0b11111111.toByte()}.toByteArray())
         *
         * In order to manually enable/disable some slots, use swapSlotBit or setSlotBit:
         * pythonAdapter.allocateRawVirtualObject(obj, obj.slotMask.swapSlotBit(SlotId.NbAdd))
         * pythonAdapter.allocateRawVirtualObject(obj, obj.slotMask.setSlotBit(SlotId.NbAdd, false))
         */
        val ref = pythonAdapter.allocateRawVirtualObject(virtualObject, virtualObject.slotMask)
        if (ref == 0L) {
            throw CPythonExecutionException()
        }
        return PyObject(ref)
    }

    fun makeList(elements: List<PyObject>): PyObject {
        return PyObject(pythonAdapter.makeList(elements.map { it.address }.toLongArray()))
    }

    fun allocateTuple(size: Int): PyObject {
        return PyObject(pythonAdapter.allocateTuple(size))
    }

    fun setTupleElement(tuple: PyObject, index: Int, elem: PyObject) {
        pythonAdapter.setTupleElement(tuple.address, index, elem.address)
    }

    fun getIterableElements(iterable: PyObject): List<PyObject> {
        val addresses = pythonAdapter.getIterableElements(iterable.address)
        return addresses.map { PyObject(it) }
    }

    fun decref(obj: PyObject) {
        pythonAdapter.decref(obj.address)
    }

    fun incref(obj: PyObject) {
        pythonAdapter.incref(obj.address)
    }

    fun decref(namespace: PyNamespace) {
        pythonAdapter.decref(namespace.address)
    }

    fun typeLookup(type: PyObject, name: String): PyObject? {
        val result = pythonAdapter.typeLookup(type.address, name)
        return if (result == 0L) null else PyObject(result)
    }

    fun getSymbolicDescriptor(concreteDescriptor: PyObject): MemberDescriptor? {
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

    private fun createTypeQuery(checkMethod: (Long) -> Int): (PyObject) -> Boolean = { pythonObject ->
        val result = checkMethod(pythonObject.address)
        if (result < 0) {
            error("Given Python object is not a type")
        }
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
    val typeHasSqConcat = createTypeQuery { pythonAdapter.typeHasSqConcat(it) }
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

    fun callStandardNew(type: PyObject): PyObject {
        return PyObject(pythonAdapter.callStandardNew(type.address))
    }

    fun typeHasStandardDict(type: PyObject): Boolean {
        return typeHasStandardTpGetattro(type) && typeHasStandardTpSetattro(type) && typeHasStandardNew(type)
    }

    fun restart() {
        pythonAdapter.finalizePython()
        initialize()
        SymbolicClonesOfGlobals.restart()
        val localVenvConfig = venvConfig
        if (localVenvConfig != null) {
            activateVenv(localVenvConfig)
        }
    }

    fun setVenv(config: VenvConfig) {
        venvConfig = config
        activateVenv(config)
    }

    private val approximationsPath = System.getProperty("approximations.path")
        ?: error("approximations.path not specified")

    private fun initializeId(module: String, name: String): Long {
        val namespace = getNewNamespace()
        concreteRun(namespace, "import $module")
        val ref = eval(namespace, "$module.$name")
        incref(ref)
        decref(namespace)
        return ref.address
    }

    private fun initializeMethodApproximations() {
        withAdditionalPaths(listOf(File(approximationsPath)), null) {
            ApproximationId.entries.forEach {
                it.cRef = initializeId(it.pythonModule, it.pythonName)
            }
            pythonAdapter.initializeSpecialApproximations()
        }
    }

    private fun initializeNativeIds() {
        NativeId.entries.forEach {
            it.cRef = initializeId(it.pythonModule, it.pythonName)
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
        initializeNativeIds()
    }

    private fun initializeSysPath(namespace: Long) {
        val initialModules = listOf("sys", "copy", "builtins", "ctypes", "array")
        pythonAdapter.concreteRun(namespace, "import " + initialModules.joinToString(", "), true, false)
        initialSysPath = PyObject(pythonAdapter.eval(namespace, "copy.copy(sys.path)", true, false))
        if (initialSysPath.address == 0L) {
            throw CPythonExecutionException()
        }
        initialSysModulesKeys = PyObject(pythonAdapter.eval(namespace, "sys.modules.keys()", true, false))
        if (initialSysModulesKeys.address == 0L) {
            throw CPythonExecutionException()
        }
    }

    private fun activateVenv(config: VenvConfig) {
        val script = activateThisScript(config)
        val namespace = getNewNamespace()
        concreteRun(namespace, script, printErrorMsg = true)
        initializeSysPath(namespace.address)
        decref(namespace)
    }

    private var venvConfig: VenvConfig? = null
    lateinit var initialSysPath: PyObject
    lateinit var initialSysModulesKeys: PyObject
    var pyEQ: Int = 0
    var pyNE: Int = 0
    var pyLT: Int = 0
    var pyLE: Int = 0
    var pyGT: Int = 0
    var pyGE: Int = 0
    var pyNoneRef: Long = 0L
    lateinit var emptyNamespace: PyNamespace

    init {
        initialize()
    }
}

data class PyObject(val address: Long) {
    init {
        require(address != 0L)
    }
}

data class PyNamespace(val address: Long) {
    init {
        require(address != 0L)
    }
}

data class IllegalOperationException(val operation: String) : RuntimeException()
