package org.usvm.machine.utils

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.utpython.types.PythonCompositeTypeDescription
import org.utpython.types.pythonDescription

fun getMembersFromType(
    type: ConcretePythonType,
    typeSystem: PythonTypeSystem,
): List<String> {
    if (typeSystem !is PythonTypeSystemWithMypyInfo) {
        return emptyList()
    }
    val utType = typeSystem.typeHintOfConcreteType(type) ?: return emptyList()
    val description = utType.pythonDescription() as? PythonCompositeTypeDescription ?: return emptyList()
    return description.getNamedMembers(utType).map {
        it.meta.name
    }.filter {
        !it.startsWith("__") && ConcretePythonInterpreter.typeLookup(type.asObject, it) == null
    }
}
