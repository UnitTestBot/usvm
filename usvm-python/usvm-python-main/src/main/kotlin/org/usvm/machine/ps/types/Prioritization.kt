package org.usvm.machine.ps.types

import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.TypeRating
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.utils.MAX_CONCRETE_TYPES_TO_CONSIDER
import org.usvm.types.TypesResult
import org.utpython.types.PythonSubtypeChecker
import org.utpython.types.general.UtType

fun makeTypeRating(state: PyState, delayedFork: DelayedFork): TypeRating? {
    val candidates = when (val types = delayedFork.possibleTypes.take(MAX_CONCRETE_TYPES_TO_CONSIDER)) {
        is TypesResult.SuccessfulTypesResult -> types.mapNotNull { it as? ConcretePythonType }
        is TypesResult.TypesResultWithExpiredTimeout, is TypesResult.EmptyTypesResult ->
            return null
    }
    val (resultList, hints) = if (state.typeSystem is PythonTypeSystemWithMypyInfo) {
        val typeGraph = SymbolTypeTree(state, state.typeSystem, delayedFork.symbol)
        prioritizeTypes(candidates, typeGraph, state.typeSystem) to typeGraph.boundsForRoot.size
    } else {
        candidates to 0
    }
    return TypeRating(resultList.toMutableList(), hints)
}

fun prioritizeTypes(
    types: List<ConcretePythonType>,
    graph: SymbolTypeTree,
    typeSystem: PythonTypeSystemWithMypyInfo,
): List<ConcretePythonType> {
    val bounds = graph.boundsForRoot
    return types.sortedBy {
        -calculateScore(it, bounds, typeSystem)
    }
}

private fun calculateScore(
    type: ConcretePythonType,
    bounds: List<UtType>,
    typeSystem: PythonTypeSystemWithMypyInfo,
): Int {
    val typeHint = typeSystem.typeHintOfConcreteType(type) ?: return 0
    return bounds.fold(0) { acc, bound ->
        val boundHolds = PythonSubtypeChecker.checkIfRightIsSubtypeOfLeft(bound, typeHint, typeSystem.typeHintsStorage)
        acc + if (boundHolds) 1 else 0
    }
}
