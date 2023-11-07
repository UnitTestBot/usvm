package org.usvm.language.types

import org.utbot.python.newtyping.general.DefaultSubstitutionProvider
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.general.getBoundedParameters
import org.utbot.python.newtyping.pythonAnyType
import org.utbot.python.newtyping.typesAreEqual

fun getTypeFromTypeHint(
    hint: UtType,
    typeSystem: PythonTypeSystemWithMypyInfo
): PythonType {
    val hintAfterSubstitution = DefaultSubstitutionProvider.substituteAll(
        hint,
        hint.getBoundedParameters().map { pythonAnyType }
    )
    val fromTypeSystem = typeSystem.concreteTypeFromTypeHint(hintAfterSubstitution)
    if (fromTypeSystem != null)
        return fromTypeSystem
    val storage = typeSystem.typeHintsStorage
    val substitutedList = DefaultSubstitutionProvider.substituteAll(
        storage.pythonList,
        storage.pythonList.getBoundedParameters().map { pythonAnyType }
    )
    val substitutedTuple = DefaultSubstitutionProvider.substituteAll(
        storage.pythonTuple,
        storage.pythonTuple.getBoundedParameters().map { pythonAnyType }
    )
    return if (typesAreEqual(hintAfterSubstitution, storage.pythonInt)) {
        typeSystem.pythonInt
    } else if (typesAreEqual(hintAfterSubstitution, storage.pythonFloat)) {
        typeSystem.pythonFloat
    } else if (typesAreEqual(hintAfterSubstitution, storage.pythonBool)) {
        typeSystem.pythonBool
    } else if (typesAreEqual(hintAfterSubstitution, substitutedList)) {
        typeSystem.pythonList
    } else if (typesAreEqual(hintAfterSubstitution, substitutedTuple)) {
        typeSystem.pythonTuple
    } else {
        PythonAnyType
    }
}