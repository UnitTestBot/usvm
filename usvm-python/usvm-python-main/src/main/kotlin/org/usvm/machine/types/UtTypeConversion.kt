package org.usvm.machine.types

import org.utpython.types.PythonSubtypeChecker
import org.utpython.types.general.DefaultSubstitutionProvider
import org.utpython.types.general.UtType
import org.utpython.types.general.getBoundedParameters
import org.utpython.types.pythonAnyType
import org.utpython.types.typesAreEqual

fun getTypeFromTypeHint(
    hint: UtType,
    typeSystem: PythonTypeSystemWithMypyInfo,
): PythonType {
    if (typesAreEqual(hint, pythonAnyType)) {
        return PythonAnyType
    }
    val hintAfterSubstitution = DefaultSubstitutionProvider.substitute(
        hint,
        hint.getBoundedParameters().associateWith { pythonAnyType }
    )
    val fromTypeSystem = typeSystem.concreteTypeFromTypeHint(hintAfterSubstitution)
    if (fromTypeSystem != null) {
        return fromTypeSystem
    }
    val storage = typeSystem.typeHintsStorage
    val substitutedDict = DefaultSubstitutionProvider.substituteAll(
        storage.pythonDict,
        storage.pythonDict.getBoundedParameters().map { pythonAnyType }
    )
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
    } else if (PythonSubtypeChecker.checkIfRightIsSubtypeOfLeft(substitutedDict, hintAfterSubstitution, storage)) {
        typeSystem.pythonDict
    } else if (PythonSubtypeChecker.checkIfRightIsSubtypeOfLeft(substitutedList, hintAfterSubstitution, storage)) {
        typeSystem.pythonList
    } else if (PythonSubtypeChecker.checkIfRightIsSubtypeOfLeft(substitutedTuple, hintAfterSubstitution, storage)) {
        typeSystem.pythonTuple
    } else {
        PythonAnyType
    }
}
