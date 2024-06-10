package org.usvm.machine.types

import org.usvm.language.StructuredPyProgram
import org.usvm.machine.CPythonExecutionException
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter.emptyNamespace
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.types.streams.PyMockTypeStream
import org.usvm.machine.types.streams.TypeFilter
import org.usvm.machine.utils.withAdditionalPaths
import org.usvm.python.model.PyIdentifier
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.utpython.types.PythonTypeHintsStorage
import org.utpython.types.PythonTypeWrapperForEqualityCheck
import org.utpython.types.general.DefaultSubstitutionProvider
import org.utpython.types.general.UtType
import org.utpython.types.general.getBoundedParameters
import org.utpython.types.mypy.MypyInfoBuild
import org.utpython.types.pythonAnyType
import org.utpython.types.pythonModuleName
import org.utpython.types.pythonName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class PythonTypeSystem : UTypeSystem<PythonType> {
    override val typeOperationsTimeout: Duration
        get() = 1000.milliseconds

    override fun isSupertype(supertype: PythonType, type: PythonType): Boolean =
        when (supertype) {
            is InternalType -> error("Should not be reachable")
            is VirtualPythonType -> supertype.accepts(type)
            is MockType, is ConcretePythonType -> supertype == type
        }

    override fun isFinal(type: PythonType): Boolean {
        return isInstantiable(type)
    }

    override fun isInstantiable(type: PythonType): Boolean {
        return type is ConcretePythonType || type is MockType
    }

    override fun hasCommonSubtype(type: PythonType, types: Collection<PythonType>): Boolean {
        require(types.count { it is ConcretePythonType } <= 1) { "Error in Python's hasCommonSubtype implementation" }
        val concrete = types.firstOrNull { it is ConcretePythonType }
        val containsMock = types.any { it is MockType }
        require((concrete == null) || !containsMock) { "Error in Python's hasCommonSubtype implementation" }
        return when (type) {
            is InternalType -> {
                error("Should not be reachable")
            }
            is ConcretePythonType -> {
                if (concrete != null) {
                    concrete == type
                } else {
                    types.all { isSupertype(it, type) }
                }
            }
            MockType -> {
                concrete == null
            }
            is VirtualPythonType -> {
                concrete == null || isSupertype(type, concrete)
            }
        }
    }

    abstract val allConcreteTypes: MutableList<ConcretePythonType>
    protected val addressToConcreteType = mutableMapOf<PyObject, ConcretePythonType>()
    private val concreteTypeToAddress = mutableMapOf<ConcretePythonType, PyObject>()
    private fun addType(type: ConcretePythonType, address: PyObject) {
        addressToConcreteType[address] = type
        concreteTypeToAddress[type] = address
        ConcretePythonInterpreter.incref(address)
    }
    protected fun addPrimitiveType(isHidden: Boolean, id: PyIdentifier, getter: () -> PyObject): ConcretePythonType {
        val address = getter()
        require(ConcretePythonInterpreter.getPythonObjectTypeName(address) == "type")
        val type = PrimitiveConcretePythonType(
            this,
            ConcretePythonInterpreter.getNameOfPythonType(address),
            id,
            isHidden,
            getter
        )
        addType(type, address)
        return type
    }

    private fun addArrayLikeType(
        constraints: Set<ElementConstraint>,
        id: PyIdentifier,
        getter: () -> PyObject,
    ): ArrayLikeConcretePythonType {
        val address = getter()
        require(ConcretePythonInterpreter.getPythonObjectTypeName(address) == "type")
        val type = ArrayLikeConcretePythonType(
            constraints,
            this,
            ConcretePythonInterpreter.getNameOfPythonType(address),
            id,
            null,
            getter
        )
        addType(type, address)
        return type
    }

    fun addressOfConcreteType(type: ConcretePythonType): PyObject {
        require(type.owner == this)
        return concreteTypeToAddress[type] ?: error("All concrete types must have addresses")
    }

    fun concreteTypeOnAddress(address: PyObject): ConcretePythonType? {
        return addressToConcreteType[address]
    }

    override fun findSubtypes(type: PythonType): Sequence<PythonType> {
        if (isFinal(type)) {
            return emptySequence()
        }
        return (listOf(MockType) + allConcreteTypes.filter { isSupertype(type, it) }).asSequence()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return PyMockTypeStream(this, TypeFilter(this, emptySet(), emptySet(), emptySet(), emptySet()))
    }

    private fun createConcreteTypeByName(name: String, isHidden: Boolean = false): ConcretePythonType =
        addPrimitiveType(
            isHidden,
            PyIdentifier("builtins", name)
        ) { ConcretePythonInterpreter.eval(emptyNamespace, name) }

    private fun createArrayLikeTypeByName(
        name: String,
        constraints: Set<ElementConstraint>,
    ): ArrayLikeConcretePythonType =
        addArrayLikeType(
            constraints,
            PyIdentifier("builtins", name)
        ) { ConcretePythonInterpreter.eval(emptyNamespace, name) }

    val pythonInt = createConcreteTypeByName("int")
    val pythonBool = createConcreteTypeByName("bool")
    val pythonFloat = createConcreteTypeByName("float")
    val pythonObjectType = createConcreteTypeByName("object")
    val pythonNoneType = createConcreteTypeByName("type(None)")
    val pythonList = createArrayLikeTypeByName("list", setOf(NonRecursiveConstraint))
    val pythonListIteratorType = createConcreteTypeByName("type(iter([]))", isHidden = true)
    val pythonTuple = createArrayLikeTypeByName("tuple", setOf(NonRecursiveConstraint))
    val pythonTupleIteratorType = createConcreteTypeByName("type(iter(tuple()))", isHidden = true)
    val pythonRange = createConcreteTypeByName("range", isHidden = true)
    val pythonRangeIterator = createConcreteTypeByName("type(range(1).__iter__())", isHidden = true)
    val pythonStr = createConcreteTypeByName("str")
    val pythonSlice = createConcreteTypeByName("slice")
    val pythonDict = createConcreteTypeByName("dict")
    val pythonSet = createConcreteTypeByName("set")
    val pythonEnumerate = createConcreteTypeByName("enumerate", isHidden = true)

    protected val basicTypes: List<ConcretePythonType> by lazy {
        concreteTypeToAddress.keys.filter { !it.isHidden }
    }
    protected val basicTypeRefs: List<PyObject> by lazy {
        basicTypes.map(::addressOfConcreteType)
    }

    fun restart() {
        concreteTypeToAddress.keys.forEach { type ->
            val newAddress = type.addressGetter()
            concreteTypeToAddress[type] = newAddress
            addressToConcreteType[newAddress] = type
            ConcretePythonInterpreter.incref(newAddress)
        }
    }
}

class BasicPythonTypeSystem : PythonTypeSystem() {
    override val allConcreteTypes = basicTypes.toMutableList()
}

class PythonTypeSystemWithMypyInfo(
    mypyBuild: MypyInfoBuild,
    private val program: StructuredPyProgram,
) : PythonTypeSystem() {
    val typeHintsStorage = PythonTypeHintsStorage.get(mypyBuild)

    private fun typeAlreadyInStorage(typeRef: PyObject): Boolean = addressToConcreteType.keys.contains(typeRef)

    private fun isWorkableType(typeRef: PyObject): Boolean {
        return ConcretePythonInterpreter.getPythonObjectTypeName(typeRef) == "type" &&
            (ConcretePythonInterpreter.typeHasStandardNew(typeRef) || basicTypeRefs.contains(typeRef))
    }

    private val utTypeOfConcretePythonType = mutableMapOf<ConcretePythonType, UtType>()
    private val concreteTypeOfUtType = mutableMapOf<PythonTypeWrapperForEqualityCheck, ConcretePythonType>()

    fun typeHintOfConcreteType(type: ConcretePythonType): UtType? = utTypeOfConcretePythonType[type]
    fun concreteTypeFromTypeHint(type: UtType): ConcretePythonType? {
        val wrappedType = PythonTypeWrapperForEqualityCheck(type)
        return concreteTypeOfUtType[wrappedType]
    }

    fun resortTypes(module: String) {
        allConcreteTypes.sortBy {
            if (it in basicTypes) {
                0
            } else if (it.typeModule == module) {
                1
            } else {
                2
            }
        }
    }

    override val allConcreteTypes: MutableList<ConcretePythonType> by lazy {
        withAdditionalPaths(program.additionalPaths, null) {
            basicTypes + typeHintsStorage.simpleTypes.mapNotNull { utTypeRaw ->
                val utType = DefaultSubstitutionProvider.substituteAll(
                    utTypeRaw,
                    utTypeRaw.getBoundedParameters().map { pythonAnyType }
                )
                val moduleName = utType.pythonModuleName()
                val name = utType.pythonName()
                val refGetter = {
                    val namespace = program.getNamespaceOfModule(moduleName)
                        ?: throw CPythonExecutionException()
                    ConcretePythonInterpreter.eval(namespace, name)
                }
                val ref = try {
                    refGetter()
                } catch (_: CPythonExecutionException) {
                    return@mapNotNull null
                }
                ConcretePythonInterpreter.incref(ref)
                if (!isWorkableType(ref)) {
                    return@mapNotNull null
                }

                if (typeAlreadyInStorage(ref)) {
                    val concreteType = concreteTypeOnAddress(ref)
                        ?: error("ref's concrete type must be known after typeAlreadyInStorage check")
                    utTypeOfConcretePythonType[concreteType] = utType
                    return@mapNotNull null
                }

                addPrimitiveType(isHidden = false, PyIdentifier(moduleName, name), refGetter).also { concreteType ->
                    utTypeOfConcretePythonType[concreteType] = utType
                    concreteTypeOfUtType[PythonTypeWrapperForEqualityCheck(utType)] = concreteType
                }
            }
        }.toMutableList()
    }
}
