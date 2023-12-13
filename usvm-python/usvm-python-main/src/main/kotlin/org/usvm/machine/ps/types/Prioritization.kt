package org.usvm.machine.ps.types

import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonTypeSystemWithMypyInfo
import org.utbot.python.newtyping.PythonSubtypeChecker
import org.utbot.python.newtyping.general.UtType

fun prioritizeTypes(types: List<ConcretePythonType>, graph: SymbolTypeTree, typeSystem: PythonTypeSystemWithMypyInfo): List<ConcretePythonType> {
    val bounds = graph.boundsForRoot
    return types.sortedBy {
        -calculateScore(it, bounds, typeSystem)
    }
}

private fun calculateScore(type: ConcretePythonType, bounds: List<UtType>, typeSystem: PythonTypeSystemWithMypyInfo): Int {
    val typeHint = typeSystem.typeHintOfConcreteType(type) ?: return 0
    return bounds.fold(0) { acc, bound ->
        val boundHolds = PythonSubtypeChecker.checkIfRightIsSubtypeOfLeft(bound, typeHint, typeSystem.typeHintsStorage)
        acc + if (boundHolds) 1 else 0
    }
}