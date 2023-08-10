package org.usvm.language.types

import org.usvm.language.StructuredPythonProgram
import org.usvm.machine.interpreters.CPythonExecutionException
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.utbot.python.newtyping.PythonTypeHintsBuild
import org.utbot.python.newtyping.mypy.MypyInfoBuild
import org.utbot.python.newtyping.pythonModuleName
import org.utbot.python.newtyping.pythonName

abstract class PythonTypeSystem: UTypeSystem<PythonType> {
    protected var allConcreteTypes: List<ConcretePythonType> = emptyList()

    open fun restart() {}

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

    // TODO: will not work for several analyzes
    val addressToConcreteType: Map<PythonObject, ConcretePythonType> by lazy {
        allConcreteTypes.associateBy { it.asObject }
    }

    override fun findSubtypes(type: PythonType): Sequence<PythonType> {
        if (isFinal(type))
            return emptySequence()
        return (listOf(TypeOfVirtualObject) + allConcreteTypes.filter { isSupertype(type, it) }).asSequence()
    }

    override fun topTypeStream(): UTypeStream<PythonType> {
        return USupportTypeStream.from(this, PythonAnyType)
    }

    protected val basicTypes = listOf(
        pythonInt,
        pythonBool,
        pythonObjectType,
        pythonNoneType,
        pythonList
    )
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

    private fun isWorkingType(type: ConcretePythonType): Boolean {
        return ConcretePythonInterpreter.getPythonObjectTypeName(type.asObject) == "type" &&
                (ConcretePythonInterpreter.typeHasStandardNew(type.asObject) || basicTypes.contains(type))
    }

    override fun restart() {
        allConcreteTypes = typeHintsStorage.simpleTypes.mapNotNull { utType ->
            val ref = try {
                val namespace = program.getNamespaceOfModule(utType.pythonModuleName())
                ConcretePythonInterpreter.eval(namespace, utType.pythonName())
            } catch (_: CPythonExecutionException) {
                return@mapNotNull null
            }
            val result = ConcretePythonType(ConcretePythonInterpreter.getNameOfPythonType(ref), ref)
            if (isWorkingType(result)) result else null
        }
        println("Restarted!")
    }
}