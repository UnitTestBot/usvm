package org.usvm.language.types

import org.usvm.language.StructuredPythonProgram
import org.usvm.machine.interpreters.CPythonExecutionException
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.ConcretePythonInterpreter.emptyNamespace
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.machine.utils.withAdditionalPaths
import org.utbot.python.newtyping.*
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.general.DefaultSubstitutionProvider
import org.utbot.python.newtyping.general.getBoundedParameters
import org.utbot.python.newtyping.mypy.MypyInfoBuild
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class PythonTypeSystem: UTypeSystem<PythonType> {
    override val typeOperationsTimeout: Duration
        get() = 1000.milliseconds

    override fun isSupertype(supertype: PythonType, type: PythonType): Boolean {
        require(supertype !is IntDictType)
        if (supertype is VirtualPythonType)
            return supertype.accepts(type)
        return supertype == type
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
            is InternalType -> error("Should not be reachable")
            is ConcretePythonType -> {
                if (concrete != null) {
                    concrete == type
                } else {
                    types.all { isSupertype(it, type) }
                }
            }
            MockType -> concrete == null
            is VirtualPythonType -> {
                concrete == null || isSupertype(type, concrete)
            }
        }
    }

    protected var allConcreteTypes: List<ConcretePythonType> = emptyList()
    protected val addressToConcreteType = mutableMapOf<PythonObject, ConcretePythonType>()
    private val concreteTypeToAddress = mutableMapOf<ConcretePythonType, PythonObject>()
    private fun addType(type: ConcretePythonType, address: PythonObject) {
        addressToConcreteType[address] = type
        concreteTypeToAddress[type] = address
        ConcretePythonInterpreter.incref(address)
    }
    protected fun addPrimitiveType(isHidden: Boolean, module: String?, getter: () -> PythonObject): ConcretePythonType {
        val address = getter()
        require(ConcretePythonInterpreter.getPythonObjectTypeName(address) == "type")
        val type = PrimitiveConcretePythonType(
            this,
            ConcretePythonInterpreter.getNameOfPythonType(address),
            module,
            isHidden,
            getter
        )
        addType(type, address)
        return type
    }

    private fun addArrayLikeType(constraints: Set<ElementConstraint>, getter: () -> PythonObject): ArrayLikeConcretePythonType {
        val address = getter()
        require(ConcretePythonInterpreter.getPythonObjectTypeName(address) == "type")
        val type = ArrayLikeConcretePythonType(
            constraints,
            this,
            ConcretePythonInterpreter.getNameOfPythonType(address),
            getter
        )
        addType(type, address)
        return type
    }

    fun addressOfConcreteType(type: ConcretePythonType): PythonObject {
        require(type.owner == this)
        return concreteTypeToAddress[type]!!
    }

    fun concreteTypeOnAddress(address: PythonObject): ConcretePythonType? {
        return addressToConcreteType[address]
    }

    override fun findSubtypes(type: PythonType): Sequence<PythonType> {
        if (isFinal(type))
            return emptySequence()
        return (listOf(MockType) + allConcreteTypes.filter { isSupertype(type, it) }).asSequence()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

    private fun createConcreteTypeByName(name: String, isHidden: Boolean = false): ConcretePythonType =
        addPrimitiveType(isHidden, null) { ConcretePythonInterpreter.eval(emptyNamespace, name) }

    private fun createArrayLikeTypeByName(name: String, constraints: Set<ElementConstraint>): ArrayLikeConcretePythonType =
        addArrayLikeType(constraints) { ConcretePythonInterpreter.eval(emptyNamespace, name) }

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
    protected val basicTypeRefs: List<PythonObject> by lazy {
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

class BasicPythonTypeSystem: PythonTypeSystem() {
    init {
        allConcreteTypes = basicTypes
    }
}

class PythonTypeSystemWithMypyInfo(
    mypyBuild: MypyInfoBuild,
    private val program: StructuredPythonProgram
): PythonTypeSystem() {
    val typeHintsStorage = PythonTypeHintsStorage.get(mypyBuild)

    private fun typeAlreadyInStorage(typeRef: PythonObject): Boolean = addressToConcreteType.keys.contains(typeRef)

    private fun isWorkableType(typeRef: PythonObject): Boolean {
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
        allConcreteTypes = allConcreteTypes.sortedBy {
            when (it.typeModule) {
                null -> 0
                module -> 1
                else -> 2
            }
        }
    }

    init {
        withAdditionalPaths(program.additionalPaths, null) {
            allConcreteTypes = basicTypes + typeHintsStorage.simpleTypes.mapNotNull { utTypeRaw ->
                val utType = DefaultSubstitutionProvider.substituteAll(
                    utTypeRaw,
                    utTypeRaw.getBoundedParameters().map { pythonAnyType }
                )
                val moduleName = utType.pythonModuleName()
                val refGetter = {
                    val namespace = program.getNamespaceOfModule(moduleName)
                        ?: throw CPythonExecutionException()
                    ConcretePythonInterpreter.eval(namespace, utType.pythonName())
                }
                val ref = try {
                    refGetter()
                } catch (_: CPythonExecutionException) {
                    return@mapNotNull null
                }
                ConcretePythonInterpreter.incref(ref)
                if (!isWorkableType(ref))
                    return@mapNotNull null

                if (typeAlreadyInStorage(ref)) {
                    utTypeOfConcretePythonType[concreteTypeOnAddress(ref)!!] = utType
                    return@mapNotNull null
                }

                addPrimitiveType(isHidden = false, module = moduleName, refGetter).also { concreteType ->
                    utTypeOfConcretePythonType[concreteType] = utType
                    concreteTypeOfUtType[PythonTypeWrapperForEqualityCheck(utType)] = concreteType
                }
            }
        }
    }
}