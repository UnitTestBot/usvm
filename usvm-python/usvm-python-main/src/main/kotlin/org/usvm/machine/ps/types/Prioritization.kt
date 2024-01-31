package org.usvm.machine.ps.types

import org.usvm.language.types.*
import org.usvm.language.types.streams.PyGenericTypeStream
import org.usvm.language.types.streams.PyMockTypeStream
import org.usvm.machine.DelayedFork
import org.usvm.machine.PyState
import org.usvm.machine.ps.strategies.TypeRating
import org.usvm.machine.utils.MAX_CONCRETE_TYPES_TO_CONSIDER
import org.usvm.types.TypesResult
import org.utbot.python.newtyping.PythonSubtypeChecker
import org.utbot.python.newtyping.general.DefaultSubstitutionProvider
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.general.getBoundedParameters
import org.utbot.python.newtyping.pythonAnyType

data class RatableType(
    val type: PythonType,
    val typeHint: UtType?
)

fun convertToRatableType(type: PythonType, typeSystem: PythonTypeSystem): RatableType? {
    if (type is ConcretePythonType) {
        val hint = if (typeSystem is PythonTypeSystemWithMypyInfo) {
            if (type is ArrayLikeConcretePythonType) {
                val rawHint = typeSystem.rawTypeHintOfConcreteType(type.original ?: type)
                require(rawHint != null && rawHint.getBoundedParameters().size == 1) {
                    "Bad raw type hint of generic"
                }
                val innerHint = type.innerType?.let { convertToRatableType(it, typeSystem)?.typeHint }
                    ?: pythonAnyType
                DefaultSubstitutionProvider.substituteByIndex(rawHint, 0, innerHint)
            } else {
                typeSystem.typeHintOfConcreteType(type)
            }
        } else {
            null
        }
        return RatableType(type, hint)
    }
    if (type is GenericType) {
        val outerType = type.typeWithoutInner
        val hint = if (typeSystem is PythonTypeSystemWithMypyInfo) {
            typeSystem.typeHintOfConcreteType(outerType)
        } else {
            null
        }
        return RatableType(type, hint)
    }
    return null
}

fun makeTypeRating(state: PyState, delayedFork: DelayedFork): TypeRating? {
    if (delayedFork.possibleTypes !is PyMockTypeStream && delayedFork.possibleTypes !is PyGenericTypeStream) {
        return null
    }
    if (delayedFork.possibleTypes is PyGenericTypeStream) {
        println("PyGenericTypeStream depth: ${delayedFork.possibleTypes.depth}")
    }
    val typesRaw = when (val types = delayedFork.possibleTypes.take(MAX_CONCRETE_TYPES_TO_CONSIDER)) {
        is TypesResult.SuccessfulTypesResult -> types.types
        is TypesResult.TypesResultWithExpiredTimeout, is TypesResult.EmptyTypesResult ->
            return null
    }
    val (types, roots) = when (delayedFork.possibleTypes) {
        is PyMockTypeStream -> typesRaw to listOf(delayedFork.symbol)
        is PyGenericTypeStream ->
            typesRaw.mapNotNull { (it as? ArrayLikeConcretePythonType)?.innerType } to state.getMockOwnersForArrayReadingsOfSymbol(
                delayedFork.symbol
            ).toList()

        else -> error("Should not be reachable")
    }
    val candidates = types.mapNotNull { convertToRatableType(it, state.typeSystem) }
    val (sortedTypes, hints) = if (state.typeSystem is PythonTypeSystemWithMypyInfo) {
        val typeGraph = SymbolTypeTree(state, state.typeSystem.typeHintsStorage, roots)
        prioritizeTypes(candidates, typeGraph, state.typeSystem) to typeGraph.boundsForRoots.size
    } else {
        candidates.map { it.type } to 0
    }
    val result = if (delayedFork.possibleTypes is PyGenericTypeStream) {
        sortedTypes.map {
            delayedFork.possibleTypes.genericType.typeWithoutInner.substitute(it)
        }
    } else {
        sortedTypes
    }
    return TypeRating(result.toMutableList(), hints)
}

fun prioritizeTypes(
    types: List<RatableType>,
    graph: SymbolTypeTree,
    typeSystem: PythonTypeSystemWithMypyInfo
): List<PythonType> {
    val bounds = graph.boundsForRoots
    return types
        .sortedBy { -calculateScore(it, bounds, typeSystem) }
        .map { it.type }
}

private fun calculateScore(type: RatableType, bounds: List<UtType>, typeSystem: PythonTypeSystemWithMypyInfo): Int {
    val typeHint = type.typeHint ?: return 0
    return bounds.fold(0) { acc, bound ->
        val boundHolds = PythonSubtypeChecker.checkIfRightIsSubtypeOfLeft(bound, typeHint, typeSystem.typeHintsStorage)
        acc + if (boundHolds) 1 else 0
    }
}