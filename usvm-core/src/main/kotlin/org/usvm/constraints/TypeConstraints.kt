package org.usvm.constraints

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.memory.UAddressCounter.Companion.NULL_ADDRESS
import org.usvm.memory.map
import org.usvm.types.UTypeSystem
import org.usvm.model.UModel
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeRegion
import org.usvm.types.takeFirst
import org.usvm.uctx

interface UTypeEvaluator<Type> {
    fun evalIs(ref: UHeapRef, type: Type): UBoolExpr
}

class UTypeModel<Type>(
    private val typeSystem: UTypeSystem<Type>,
    private val typeByAddr: Map<UConcreteHeapAddress, Type>,
) : UTypeEvaluator<Type> {
    fun typeOf(address: UConcreteHeapAddress): Type = typeByAddr[address] ?: typeSystem.topTypeStream().takeFirst()

    override fun evalIs(ref: UHeapRef, type: Type): UBoolExpr =
        when (ref) {
            is UConcreteHeapRef -> {
                val holds = typeSystem.isSupertype(type, typeOf(ref.address))
                if (holds) ref.ctx.trueExpr else ref.ctx.falseExpr
            }

            else -> error("Expecting concrete ref, but got $ref")
        }
}

/**
 * A mutable collection of type constraints. Represents a conjunction of constraints of four kinds:
 * 1. x <: T, i.e. object referenced in x inherits T (supertype constraints for x)
 * 2. T <: x, i.e. object referenced in x inherited by T (subtype constraints for x)
 * 3. x </: T, i.e. object referenced in x does not inherit T (notSupertype constraints for x)
 * 4. T </: x, i.e. object referenced in x is not inherited by T (notSubtype constraints for x)
 *
 * Adding subtype constraint for x is done via [addSupertype] method.
 * TODO: The API for rest type constraints will be added later.
 *
 * Manages allocated objects separately from input ones. Indeed, we know the type of an allocated object
 * precisely, thus we can evaluate the subtyping constraints for them concretely (modulo generic type variables).
 */
class UTypeConstraints<Type>(
    private val typeSystem: UTypeSystem<Type>,
    private val equalityConstraints: UEqualityConstraints,
    private val concreteRefToType: MutableMap<UConcreteHeapAddress, Type> = mutableMapOf(),
    private val symbolicRefToTypeRegion: MutableMap<UHeapRef, UTypeRegion<Type>> = mutableMapOf(),
) : UTypeEvaluator<Type> {
    init {
        equalityConstraints.subscribe(::intersectConstraints)
    }

    /**
     * Returns if current type and equality constraints are unsatisfiable (syntactically).
     */
    var isContradiction = false
        private set

    private val topTypeRegion by lazy {
        UTypeRegion(
            typeSystem,
            typeSystem.topTypeStream()
        )
    }

    private fun contradiction() {
        isContradiction = true
    }

    /**
     * Binds concrete heap address [ref] to its [type].
     */
    fun allocate(ref: UConcreteHeapAddress, type: Type) {
        concreteRefToType[ref] = type
    }

    private operator fun get(symbolicRef: UHeapRef) =
        symbolicRefToTypeRegion[equalityConstraints.equalReferences.find(symbolicRef)] ?: topTypeRegion


    private operator fun set(symbolicRef: UHeapRef, value: UTypeRegion<Type>) {
        symbolicRefToTypeRegion[equalityConstraints.equalReferences.find(symbolicRef)] = value
    }

    /**
     * Constraints type of [ref] to be a subtype of [type].
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradiction]).
     */
    fun addSupertype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType.getValue(ref.address)
                if (!typeSystem.isSupertype(type, concreteType)) {
                    contradiction()
                }
            }

            else -> {
                val constraints = this[ref]
                val newConstraints = constraints.addSupertype(type)
                if (newConstraints.isContradicting) {
                    equalityConstraints.addReferenceEquality(ref, ref.uctx.nullRef)
                } else {
                    // Inferring new symbolic disequalities here
                    for ((key, value) in symbolicRefToTypeRegion.entries) {
                        // TODO: cache intersections?
                        if (key != ref && value.intersect(newConstraints).isEmpty) {
                            // If we have two inputs of incomparable reference types, then they are either non equal,
                            // or both nulls
                            equalityConstraints.makeNonEqualOrBothNull(ref, key)
                        }
                    }
                    this[ref] = newConstraints
                }
            }
        }
    }

    /**
     * Constraints type of [ref] to be a noy subtype of [type].
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradiction]).
     */
    fun excludeSupertype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType.getValue(ref.address)
                if (typeSystem.isSupertype(type, concreteType)) {
                    contradiction()
                }
            }

            else -> {
                val constraints = this[ref]
                val newConstraints = constraints.excludeSupertype(type)
                if (newConstraints.isContradicting) {
                    equalityConstraints.addReferenceEquality(ref, ref.uctx.nullRef)
                } else {
                    // Inferring new symbolic disequalities here
                    for ((key, value) in symbolicRefToTypeRegion.entries) {
                        // TODO: cache intersections?
                        if (key != ref && value.intersect(newConstraints).isEmpty) {
                            // If we have two inputs of incomparable reference types, then they are either non equal,
                            // or both nulls
                            equalityConstraints.makeNonEqualOrBothNull(ref, key)
                        }
                    }
                    this[ref] = newConstraints
                }
            }
        }
    }

    fun readTypeRegion(ref: UHeapRef): UTypeRegion<Type> =
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType[ref.address]
                val typeStream = if (concreteType == null) {
                    typeSystem.topTypeStream()
                } else {
                    USingleTypeStream(typeSystem, concreteType)
                }
                UTypeRegion(typeSystem, typeStream)
            }

            else -> {
                this[ref]
            }
        }

    private fun intersectConstraints(ref1: UHeapRef, ref2: UHeapRef) {
        this[ref1] = this[ref1].intersect(this[ref2])
    }

    override fun evalIs(ref: UHeapRef, type: Type): UBoolExpr =
        ref.map(
            concreteMapper = { concreteRef ->
                val concreteType = concreteRefToType.getValue(concreteRef.address)
                if (typeSystem.isSupertype(type, concreteType)) {
                    concreteRef.ctx.trueExpr
                } else {
                    concreteRef.ctx.falseExpr
                }
            },
            symbolicMapper = { symbolicRef ->
                val typeRegion = this[symbolicRef]

                if (typeRegion.addSupertype(type).isContradicting) {
                    symbolicRef.ctx.falseExpr
                } else {
                    symbolicRef.uctx.mkIsExpr(symbolicRef, type)
                }
            }
        )


    /**
     * Creates a mutable copy of these constraints connected to new instance of [equalityConstraints].
     */
    fun clone(equalityConstraints: UEqualityConstraints) =
        UTypeConstraints(
            typeSystem,
            equalityConstraints,
            concreteRefToType.toMutableMap(),
            symbolicRefToTypeRegion.toMutableMap()
        )

    fun verify(model: UModel): USolverResult<UTypeModel<Type>> {
        val concreteRefToTypeRegions = symbolicRefToTypeRegion
            .entries
            .groupBy { (key, _) -> (model.eval(key) as UConcreteHeapRef).address }
            .filter { it.key != NULL_ADDRESS }

        val bannedRefEqualities = mutableListOf<UBoolExpr>()

        val concreteToRegionWithCluster = concreteRefToTypeRegions.mapValues { (_, cluster) ->
            var currentRegion: UTypeRegion<Type>? = null

            val potentialConflictingRefs = mutableListOf<UHeapRef>()

            for ((heapRef, region) in cluster) {
                if (currentRegion == null) {
                    currentRegion = region
                    potentialConflictingRefs.add(heapRef)
                } else {
                    val nextRegion = currentRegion.intersect(region)
                    if (nextRegion.isEmpty) {
                        val disjunct = potentialConflictingRefs.map {
                            with(it.ctx) { it.neq(heapRef) }
                        }
                        bannedRefEqualities += heapRef.ctx.mkOr(disjunct)
                    } else if (nextRegion === region) {
                        potentialConflictingRefs.clear()
                        potentialConflictingRefs.add(heapRef)
                    } else if (nextRegion !== currentRegion) {
                        potentialConflictingRefs.add(heapRef)
                    }

                    currentRegion = nextRegion
                }
            }
            checkNotNull(currentRegion)

            currentRegion to cluster
        }

        if (bannedRefEqualities.isNotEmpty()) {
            return UTypeUnsatResult(bannedRefEqualities)
        }

        val allConcreteRefToType = concreteRefToType.toMutableMap()
        concreteToRegionWithCluster.mapValuesTo(allConcreteRefToType) { (_, regionToCluster) ->
            val (region, cluster) = regionToCluster
            val resultList = mutableListOf<Type>()
            val terminated = region.typeStream.take(1, resultList)
            if (terminated) {
                if (resultList.isEmpty()) {
                    check(cluster.size == 1)
                    return UUnsatResult()
                } else {
                    resultList.single()
                }
            } else {
                return UUnknownResult()
            }
        }

        val typeModel = UTypeModel(typeSystem, allConcreteRefToType)
        return USatResult(typeModel)
    }
}

class UTypeUnsatResult<Type>(
    val expressionsToAssert: List<UBoolExpr>,
) : UUnsatResult<UTypeModel<Type>>()
