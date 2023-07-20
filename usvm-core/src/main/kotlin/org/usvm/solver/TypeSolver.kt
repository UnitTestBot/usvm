package org.usvm.solver

import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UIsSubtypeExpr
import org.usvm.USymbolicHeapRef
import org.usvm.constraints.UTypeConstraints
import org.usvm.model.UTypeModel
import org.usvm.types.UTypeRegion
import org.usvm.types.UTypeSystem
import org.usvm.uctx

data class TypeSolverQuery<Type>(
    val typeConstraints: UTypeConstraints<Type>,
    val logicalConstraints: Collection<UBoolExpr>,
    val symbolicToConcrete: (USymbolicHeapRef) -> UConcreteHeapRef,
    val isExprToInterpreted: (UIsSubtypeExpr<Type>) -> Boolean,
)

class UTypeUnsatResult<Type>(
    val referenceDisequalitiesDisjuncts: List<UBoolExpr>,
) : UUnsatResult<UTypeModel<Type>>()

class UTypeSolver<Field, Type>(
    translator: UExprTranslator<Field, Type>,
    private val typeSystem: UTypeSystem<Type>,
) : USolver<TypeSolverQuery<Type>, UTypeModel<Type>>() {
    private val uIsExprCollector = UIsExprCollector<Field, Type>(translator.ctx)
    val topTypeRegion by lazy { UTypeRegion(typeSystem, typeSystem.topTypeStream()) }

    init {
        translator.addObserver(uIsExprCollector)
    }

    /**
     * TODO: rewrite this comment
     * Checks if the [model] satisfies this [UTypeConstraints].
     *
     * Checking works as follows:
     * 1. Groups symbolic references into clusters by their concrete interpretation in the [model] and filters out nulls
     * 2. For each cluster processes symbolic references one by one and intersects their type regions
     * 3. If the current region became empty, then we found a conflicting group, so add reference disequality
     * 4. If no conflicting references were found, builds a type model
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
     * * [UTypeModel] if the [model] satisfies this [UTypeConstraints]
     * * [UUnsatResult] if no model satisfying this [UTypeConstraints] exists
     * * [UTypeUnsatResult] with reference disequalities constraints if the [model]
     * doesn't satisfy this [UTypeConstraints], but other model may satisfy
     */
    override fun check(
        pc: TypeSolverQuery<Type>,
    ): USolverResult<UTypeModel<Type>> {
        val allIsExpressions = pc
            .logicalConstraints
            .flatMap(uIsExprCollector::collect)

        val symbolicRefToIsExprs = allIsExpressions.groupBy { it.ref as USymbolicHeapRef }

        val symbolicRefToRegion =
            symbolicRefToIsExprs.mapValues { topTypeRegion } + pc.typeConstraints.symbolicRefToTypeRegion

        val bannedRefEqualities = mutableListOf<UBoolExpr>()

        for ((ref, isExprs) in symbolicRefToIsExprs) {
            val concreteRef = pc.symbolicToConcrete(ref).address
            if (concreteRef != NULL_ADDRESS) {
                continue
            }
            for (isExpr in isExprs) {
                val holds = pc.isExprToInterpreted(isExpr)
                if (!holds) {
                    bannedRefEqualities += with(ref.uctx) { mkOr(ref neq nullRef, isExpr) }
                }
            }
        }

        val concreteRefToCluster = symbolicRefToRegion.entries
            .groupBy { (ref, _) -> pc.symbolicToConcrete(ref).address }
            .filterNot { (address, _) -> address == NULL_ADDRESS }


        // then for each group check conflicting types
        val concreteToRegionWithCluster = concreteRefToCluster.mapValues { (_, cluster) ->
            var currentRegion = topTypeRegion
            val potentialConflictingRefs = mutableListOf<USymbolicHeapRef>()
            val potentialConflictingIsExprs = mutableListOf<UBoolExpr>()

            for ((heapRef, region) in cluster) {
                var nextRegion = currentRegion.intersect(region) // add [heapRef] to the current region

                val isExpressions = symbolicRefToIsExprs.getOrElse(heapRef, ::emptyList)

                val evaluatedIsExpressions = isExpressions.map { isExpr ->
                    val holds = pc.isExprToInterpreted(isExpr)
                    if (holds) {
                        nextRegion = nextRegion.addSupertype(isExpr.supertype)
                        isExpr
                    } else {
                        nextRegion = nextRegion.excludeSupertype(isExpr.supertype)
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
                        //
                        potentialConflictingIsExprs.mapTo(disjunct) { it.not() }
                        evaluatedIsExpressions.mapTo(disjunct) { it.not() }
                    }
                    bannedRefEqualities += heapRef.ctx.mkOr(disjunct)

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
                    potentialConflictingIsExprs += evaluatedIsExpressions
                }

                currentRegion = nextRegion
            }

            currentRegion to cluster
        }

        // if there were some conflicts, return constraints on reference equalities
        if (bannedRefEqualities.isNotEmpty()) {
            return UTypeUnsatResult(bannedRefEqualities)
        }

        // otherwise, return the type assignment
        val allConcreteRefToType = concreteToRegionWithCluster.mapValues { (_, regionToCluster) ->
            val (region, cluster) = regionToCluster
            val typeStream = region.typeStream
            if (typeStream.isEmpty) {
                // the only way to reach here is when some of the clusters consists of a single reference
                // because if the cluster is bigger, then we called region.isEmpty previously at least once
                check(cluster.size == 1)
                return UUnsatResult()
            }

            typeStream
        }

        val typeModel = UTypeModel(typeSystem, allConcreteRefToType)
        return USatResult(typeModel)
    }
}
