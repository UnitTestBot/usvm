package org.usvm.constraints

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UTypeSystem
import org.usvm.model.UModel
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.uctx

interface UTypeEvaluator<Type> {
    fun evalIs(ref: UHeapRef, type: Type): UBoolExpr
}

class UTypeModel<Type>(
    private val typeSystem: UTypeSystem<Type>,
    private val typeByAddr: Map<UConcreteHeapAddress, Type>,
) : UTypeEvaluator<Type> {
    fun typeOf(address: UConcreteHeapAddress): Type = typeByAddr.getValue(address)

    override fun evalIs(ref: UHeapRef, type: Type): UBoolExpr =
        when (ref) {
            is UConcreteHeapRef -> {
                val holds = typeSystem.isSupertype(type, typeOf(ref.address))
                if (holds) ref.ctx.trueExpr else ref.ctx.falseExpr
            }

            else -> throw IllegalArgumentException("Expecting concrete ref, but got $ref")
        }
}

/**
 * A mutable collection of type constraints. Represents a conjunction of constraints of four kinds:
 * 1. x <: T, i.e. object referenced in x inherits T (supertype constraints for x)
 * 2. T <: x, i.e. object referenced in x inherited by T (subtype constraints for x)
 * 3. x </: T, i.e. object referenced in x does not inherit T (notSupertype constraints for x)
 * 4. T </: x, i.e. object referenced in x is not inherited by T (notSubtype constraints for x)
 *
 * Adding subtype constraint for x is done via [cast] method.
 * TODO: The API for rest type constraints will be added later.
 *
 * Manages allocated objects separately from input ones. Indeed, we know the type of an allocated object
 * precisely, thus we can evaluate the subtyping constraints for them concretely (modulo generic type variables).
 */
class UTypeConstraints<Type>(
    private val typeSystem: UTypeSystem<Type>,
    private val topTypeStreamFactory: () -> UTypeStream<Type>,
    private val equalityConstraints: UEqualityConstraints,
    private val concreteTypes: MutableMap<UConcreteHeapAddress, Type> = mutableMapOf(),
    private val symbolicTypes: MutableMap<UHeapRef, UTypeRegion<Type>> = mutableMapOf(),
) : UTypeEvaluator<Type> {
    init {
        equalityConstraints.subscribe(::intersectConstraints)
    }

    /**
     * Returns if current type and equality constraints are unsatisfiable (syntactically).
     */
    var isContradiction = false
        private set

    private fun contradiction() {
        isContradiction = true
    }

    /**
     * Binds concrete heap address [ref] to its [type].
     */
    fun allocate(ref: UConcreteHeapAddress, type: Type) {
        concreteTypes[ref] = type
    }

    private operator fun get(symbolicRef: UHeapRef) =
        symbolicTypes[equalityConstraints.equalReferences.find(symbolicRef)] ?: UTypeRegion(
            typeSystem,
            topTypeStreamFactory()
        )

    private operator fun set(symbolicRef: UHeapRef, value: UTypeRegion<Type>) {
        symbolicTypes[equalityConstraints.equalReferences.find(symbolicRef)] = value
    }

    /**
     * Constraints type of [ref] to be a subtype of [type].
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradiction]).
     */
    fun cast(ref: UHeapRef, type: Type) {
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteTypes.getValue(ref.address)
                if (!typeSystem.isSupertype(type, concreteType)) {
                    contradiction()
                }
            }

            else -> {
                val constraints = this[ref]
                val newConstraints = constraints.addSupertype(type)
                if (newConstraints.isContradicting) {
                    contradiction()
                } else {
                    // Inferring new symbolic disequalities here
                    for ((key, value) in symbolicTypes.entries) {
                        // TODO: cache intersections?
                        if (key != ref && value.intersect(newConstraints).isEmpty) {
                            equalityConstraints.addReferenceDisequality(ref, key)
                        }
                    }
                    this[ref] = newConstraints
                }
            }
        }
    }

    private fun intersectConstraints(ref1: UHeapRef, ref2: UHeapRef) {
        this[ref1] = this[ref1].intersect(this[ref2])
    }

    override fun evalIs(ref: UHeapRef, type: Type): UBoolExpr {
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteTypes.getValue(ref.address)
                return if (typeSystem.isSupertype(type, concreteType)) ref.ctx.trueExpr else ref.ctx.falseExpr
            }

            else -> {
                val constraints = this[ref]

                if (constraints.addSupertype(type).isContradicting) {
                    return ref.ctx.falseExpr
                }

                return ref.uctx.mkIsExpr(ref, type)
            }
        }
    }

    /**
     * Creates a mutable copy of these constraints connected to new instance of [equalityConstraints].
     */
    fun clone(equalityConstraints: UEqualityConstraints) =
        UTypeConstraints(
            typeSystem,
            topTypeStreamFactory,
            equalityConstraints,
            concreteTypes.toMutableMap(),
            symbolicTypes.toMutableMap()
        )

    fun verify(model: UModel): USolverResult<UTypeModel<Type>> {
        val concreteRefToTypeRegions = symbolicTypes.entries.groupBy { (key, _) -> (model.eval(key) as UConcreteHeapRef).address }
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

        return if (bannedRefEqualities.isNotEmpty()) {
            UTypeUnsatResult(bannedRefEqualities)
        } else {
            val resultList = mutableListOf<Type>()

            val concreteToType = concreteToRegionWithCluster.mapValues { (_, regionToCluster) ->
                val (region, cluster) = regionToCluster
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

            val typeModel = UTypeModel(typeSystem, concreteToType)
            UTypeSatResult(typeModel)
        }
    }
}

class UTypeUnsatResult<Type>(
    val expressionsToAssert: List<UBoolExpr>
) : UUnsatResult<UTypeModel<Type>>()

class UTypeSatResult<Type>(
    model: UTypeModel<Type>
) : USatResult<UTypeModel<Type>>(model)