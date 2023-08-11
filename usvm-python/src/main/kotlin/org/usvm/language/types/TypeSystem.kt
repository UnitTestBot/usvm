package org.usvm.language.types

import org.usvm.language.StructuredPythonProgram
import org.usvm.machine.interpreters.CPythonExecutionException
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.emptyNamespace
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.utils.withAdditionalPaths
import org.utbot.python.newtyping.PythonTypeHintsBuild
import org.utbot.python.newtyping.mypy.MypyInfoBuild
import org.utbot.python.newtyping.pythonModuleName
import org.utbot.python.newtyping.pythonName
import org.utbot.python.newtyping.pythonTypeRepresentation

abstract class PythonTypeSystem: UTypeSystem<PythonType> {

    override fun isSupertype(supertype: PythonType, type: PythonType): Boolean {
        if (supertype is VirtualPythonType)
            return supertype.accepts(type)
        return supertype == type
    }

    override fun isMultipleInheritanceAllowedFor(type: PythonType): Boolean {
        return !isInstantiable(type)
    }

    override fun isFinal(type: PythonType): Boolean {
        return isInstantiable(type)
    }

    override fun isInstantiable(type: PythonType): Boolean {
        return type is ConcretePythonType || type is TypeOfVirtualObject
    }

    protected var allConcreteTypes: List<ConcretePythonType> = emptyList()
    protected val addressToConcreteType = mutableMapOf<PythonObject, ConcretePythonType>()
    private val concreteTypeToAddress = mutableMapOf<ConcretePythonType, PythonObject>()
    protected fun addType(getter: () -> PythonObject): ConcretePythonType {
        val address = getter()
        require(ConcretePythonInterpreter.getPythonObjectTypeName(address) == "type")
        val type = ConcretePythonType(this, ConcretePythonInterpreter.getNameOfPythonType(address), getter)
        addressToConcreteType[address] = type
        concreteTypeToAddress[type] = address
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
        return (listOf(TypeOfVirtualObject) + allConcreteTypes.filter { isSupertype(type, it) }).asSequence()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

    private fun createConcreteTypeByName(name: String): ConcretePythonType =
        addType { ConcretePythonInterpreter.eval(emptyNamespace, name) }

    val pythonInt = createConcreteTypeByName("int")
    val pythonBool = createConcreteTypeByName("bool")
    val pythonObjectType = createConcreteTypeByName("object")
    val pythonNoneType = createConcreteTypeByName("type(None)")
    val pythonList = createConcreteTypeByName("list")
    val pythonTuple = createConcreteTypeByName("tuple")
    val pythonListIteratorType = createConcreteTypeByName("type(iter([]))")

    protected val basicTypes: List<ConcretePythonType> = listOf(
        pythonInt,
        pythonBool,
        pythonObjectType,
        pythonNoneType,
        pythonList
    )
    protected val basicTypeRefs: List<PythonObject> = basicTypes.map(::addressOfConcreteType)

    fun restart() {
        concreteTypeToAddress.keys.forEach { type ->
            val newAddress = type.addressGetter()
            concreteTypeToAddress[type] = newAddress
            addressToConcreteType[newAddress] = type
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
    private val typeHintsStorage = PythonTypeHintsBuild.get(mypyBuild)

    private fun typeAlreadyInStorage(typeRef: PythonObject): Boolean = addressToConcreteType.keys.contains(typeRef)

    private fun isWorkableType(typeRef: PythonObject): Boolean {
        return ConcretePythonInterpreter.getPythonObjectTypeName(typeRef) == "type" &&
                (ConcretePythonInterpreter.typeHasStandardNew(typeRef) || basicTypeRefs.contains(typeRef))
    }

    init {
        withAdditionalPaths(program.additionalPaths) {
            allConcreteTypes = basicTypes + typeHintsStorage.simpleTypes.mapNotNull { utType ->
                val refGetter = {
                    val namespace = program.getNamespaceOfModule(utType.pythonModuleName())
                    ConcretePythonInterpreter.eval(namespace, utType.pythonName())
                }
                val ref = try {
                    refGetter()
                } catch (_: CPythonExecutionException) {
                    return@mapNotNull null
                }
                if (!isWorkableType(ref) || typeAlreadyInStorage(ref))
                    return@mapNotNull null

                addType(refGetter)
            }
        }
    }
}