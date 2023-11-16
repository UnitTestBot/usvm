package org.usvm.machine.utils

import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.language.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.utbot.python.newtyping.PythonCompositeTypeDescription
import org.utbot.python.newtyping.pythonDescription

fun getMembersFromType(
    type: ConcretePythonType,
    typeSystem: PythonTypeSystem
): List<String> {
    if (typeSystem !is PythonTypeSystemWithMypyInfo)
        return emptyList()
    val utType = typeSystem.typeHintOfConcreteType(type) ?: return emptyList()
    val description = utType.pythonDescription() as? PythonCompositeTypeDescription ?: return emptyList()
    return description.getNamedMembers(utType).map {
        it.meta.name
    }.filter {
        !it.startsWith("__") && ConcretePythonInterpreter.typeLookup(type.asObject, it) == null
    }
}