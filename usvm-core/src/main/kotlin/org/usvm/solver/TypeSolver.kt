package org.usvm.solver

import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UIsExpr
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.model.UTypeModel
import org.usvm.types.UTypeRegion
import org.usvm.types.UTypeSystem
import org.usvm.uctx

data class TypeSolverQuery<Type>(
    val inputToConcrete: (UHeapRef) -> UConcreteHeapRef,
    val inputRefToTypeRegion: Map<UHeapRef, UTypeRegion<Type>>,
    val isExprToInterpretation: List<Pair<UIsExpr<Type>, Boolean>>,
)

class UTypeUnsatResult<Type>(
    val conflictLemmas: List<UBoolExpr>,
) : UUnsatResult<UTypeModel<Type>>()

class UTypeSolver<Type>(
    private val typeSystem: UTypeSystem<Type>,
) : USolver<TypeSolverQuery<Type>, UTypeModel<Type>>() {
    private val topTypeRegion by lazy { UTypeRegion(typeSystem, typeSystem.topTypeStream()) }

    /**
     * Checks the [query].
     *
     * Checking works as follows:
     * 1. Groups propositional variables by input heap references and builds nullability conflict lemmas.
     * 2. Groups input references into clusters by their concrete interpretations and filters out nulls.
     * 3. For each cluster processes input references one by one, intersects their type regions according to
     * [UIsSubtypeExpr]s and [UIsSupertypeExpr]s.
     * 4. If the current region became empty, then we have found a conflicting group, so build a new reference
     * disequality lemma with negation of [UIsSubtypeExpr]s and [UIsSupertypeExpr]s.
     * 5. If no conflicting references were found, builds a type model.
     *
     * Example:
     * ```
     * a <: T1 | T2
     * b <: T2 | T3
     * c <: T3 | T1
     * d <: T1 | T2
     * e <: T2
     * cluster: (a, b, c, d, e)
     * ```
     * Suppose we have the single cluster, so we process it as follows:
     *
     * 1. Peek `a`. The current region is empty, so it becomes `T1 | T2`. Potential conflicting refs = `{a}`.
     * 2. Peek `b`. The current region becomes `T2`. Potential conflicting refs = `{a, b}`.
     * 3. Peek `c`. The intersection of the current region with `T3 | T1` is empty, so we add the following constraint:
     * `a != c || b != c || a == null || b == null || c == null`. The region becomes `T3 | T1`.
     * Potential conflicting refs = `{c}.
     * 4. Peek `d`. The current region becomes `T1`. Potential conflicting refs = `{c, d}`.
     * 5. Peek `e`. The intersection of the current region with `T2` is empty, so we add the following constraint:
     * `c != e || d != e || c == null || d == null || e == null`.
     *
     * @return The result of verification:
     * * [UTypeModel] if the [query] is satisfiable.
     * * [UUnsatResult] if the [query] is unsatisfiable in all models.
     * * [UTypeUnsatResult] with lemmas explaining conflicts if the [query] is unsatisfiable in the current model,
     * but may be satisfiable in other models.
     */
    override fun check(
        query: TypeSolverQuery<Type>,
    ): USolverResult<UTypeModel<Type>> {
        val conflictLemmas = mutableListOf<UBoolExpr>()

        val symbolicRefToIsExprs = extractIsExpressionsAndFindNullConflicts(
            query.inputToConcrete,
            query.isExprToInterpretation,
            onHolds = { ref, isExpr ->
                if (isExpr is UIsSupertypeExpr) {
                    conflictLemmas += with(ref.uctx) { mkOr(ref neq nullRef, !isExpr) }
                }
            },
            onNotHolds = { ref, isExpr ->
                if (isExpr is UIsSubtypeExpr) {
                    conflictLemmas += with(ref.uctx) { mkOr(ref neq nullRef, isExpr) }
                }
            }
        )

        val symbolicRefToRegion =
            symbolicRefToIsExprs.mapValues { topTypeRegion } +
                query.inputRefToTypeRegion


        val concreteRefToCluster = symbolicRefToRegion.entries
            .groupBy { (ref, _) -> query.inputToConcrete(ref).address }
            .filterNot { (address, _) -> address == NULL_ADDRESS }


        // then for each group check conflicting types
        val concreteToRegionWithCluster = concreteRefToCluster.mapValues { (_, cluster) ->
            checkCluster(cluster, symbolicRefToIsExprs, conflictLemmas)
        }

        // if there were some conflicts, return constraints on reference equalities
        if (conflictLemmas.isNotEmpty()) {
            return UTypeUnsatResult(conflictLemmas)
        }

        // otherwise, return the type assignment
        val allConcreteRefToType = concreteToRegionWithCluster.mapValues { (_, regionToCluster) ->
            val (region, cluster) = regionToCluster
            val typeStream = region.typeStream
            val isEmpty = typeStream.isEmpty ?: return UUnknownResult() // Timeout here may lead to an inconsistent model - avoid it
            if (isEmpty) {
                // the only way to reach here is when some of the clusters consists of a single reference
                // because if the cluster is bigger, then we called region.isEmpty previously at least once
                check(cluster.size == 1)
                return UUnsatResult()
            }

            region
        }

        val typeModel = UTypeModel(typeSystem, allConcreteRefToType)
        return USatResult(typeModel)
    }

    private fun checkCluster(
        cluster: List<Map.Entry<UHeapRef, UTypeRegion<Type>>>,
        symbolicRefToIsExpr: Map<UHeapRef, List<Pair<UIsExpr<Type>, Boolean>>>,
        conflictLemmas: MutableList<UBoolExpr>,
    ): Pair<UTypeRegion<Type>, List<Map.Entry<UHeapRef, UTypeRegion<Type>>>> {
        var currentRegion = topTypeRegion
        val potentialConflictingRefs = mutableListOf<UHeapRef>()
        val potentialConflictingIsExprs = mutableListOf<UBoolExpr>()

        for ((heapRef, region) in cluster) {
            var nextRegion = currentRegion.intersect(region) // add [heapRef] to the current region

            val isExprs = symbolicRefToIsExpr.getOrElse(heapRef, ::emptyList)

            val evaluatedIsExprs = isExprs.map { (isExpr, holds) ->
                if (holds) {
                    nextRegion = when (isExpr) {
                        is UIsSupertypeExpr -> nextRegion.addSubtype(isExpr.subtype)
                        is UIsSubtypeExpr -> nextRegion.addSupertype(isExpr.supertype)
                        else -> error("Unexpected isExpr: $isExpr")
                    }
                    isExpr
                } else {
                    nextRegion = when (isExpr) {
                        is UIsSupertypeExpr -> nextRegion.excludeSubtype(isExpr.subtype)
                        is UIsSubtypeExpr -> nextRegion.excludeSupertype(isExpr.supertype)
                        else -> error("Unexpected isExpr: $isExpr")
                    }
                    isExpr.ctx.mkNot(isExpr)
                }
            }

            if (nextRegion.isEmpty) {
                // conflict detected, so it's impossible for [potentialConflictingRefs]
                // to have the common type with [heapRef], therefore they can't be equal, or
                // some of them equals null, or some of the [potentialConflictingIsExprs] is false
                val disjunct = mutableListOf<UBoolExpr>()
                with(heapRef.uctx) {
                    // can't be equal to heapRef
                    potentialConflictingRefs.mapTo(disjunct) { it.neq(heapRef) }
                    // some of them is null
                    potentialConflictingRefs.mapTo(disjunct) { it.eq(nullRef) }
                    disjunct += heapRef.eq(nullRef)
                    // the accumulated conflicting propositional variables
                    potentialConflictingIsExprs.mapTo(disjunct) { it.not() }
                    // the new propositional variables
                    evaluatedIsExprs.mapTo(disjunct) { it.not() }
                }
                conflictLemmas += heapRef.ctx.mkOr(disjunct)

                // start a new group
                nextRegion = region
                potentialConflictingIsExprs.clear()
                potentialConflictingRefs.clear()
                potentialConflictingRefs.add(heapRef)
            } else if (nextRegion == region) {
                // the current [heapRef] gives the same region as the potentialConflictingRefs, so it's better
                // to keep only the [heapRef] to minimize the disequalities amount in the result disjunction
                potentialConflictingIsExprs.clear()
                potentialConflictingRefs.clear()
                potentialConflictingRefs.add(heapRef)
            } else if (nextRegion != currentRegion) {
                // no conflict detected, but the current region became smaller
                potentialConflictingRefs.add(heapRef)
                potentialConflictingIsExprs += evaluatedIsExprs
            }

            currentRegion = nextRegion
        }

        return currentRegion to cluster
    }

    private inline fun extractIsExpressionsAndFindNullConflicts(
        inputToConcrete: (UHeapRef) -> UConcreteHeapRef,
        isExpressions: List<Pair<UIsExpr<Type>, Boolean>>,
        onHolds: (UHeapRef, UIsExpr<Type>) -> Unit = { _, _ -> },
        onNotHolds: (UHeapRef, UIsExpr<Type>) -> Unit = { _, _ -> },
    ): Map<UHeapRef, List<Pair<UIsExpr<Type>, Boolean>>> {
        val inputRefToIsSubtypeExprs = isExpressions.groupBy { (expr, _) -> expr.ref }
        for ((ref, isSupertypeExprs) in inputRefToIsSubtypeExprs) {
            val concreteRef = inputToConcrete(ref).address
            if (concreteRef != NULL_ADDRESS) {
                continue
            }
            for ((isExpr, holds) in isSupertypeExprs) {
                if (holds) {
                    onHolds(ref, isExpr)
                } else {
                    onNotHolds(ref, isExpr)
                }
            }
        }
        return inputRefToIsSubtypeExprs
    }

}
