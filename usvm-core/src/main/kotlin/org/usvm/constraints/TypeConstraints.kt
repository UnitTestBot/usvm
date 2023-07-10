package org.usvm.constraints

import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.memory.map
import org.usvm.types.UTypeSystem
import org.usvm.model.UModel
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeRegion
import org.usvm.types.take
import org.usvm.uctx

interface UTypeEvaluator<Type> {
    fun evalIs(ref: UHeapRef, type: Type): UBoolExpr
}

class UTypeModel<Type>(
    val typeSystem: UTypeSystem<Type>,
    typeByAddr: Map<UConcreteHeapAddress, Type>,
) : UTypeEvaluator<Type> {
    private val typeByAddr = typeByAddr.toMutableMap()

    fun typeOrNull(ref: UConcreteHeapRef): Type? = typeByAddr[ref.address]

    override fun evalIs(ref: UHeapRef, type: Type): UBoolExpr =
        when (ref) {
            is UConcreteHeapRef -> {
                val evaluatedType = typeOrNull(ref)
                val holds = if (evaluatedType == null) {
                    val anyTypes = typeSystem.topTypeStream().filterBySupertype(type).take(1)
                    val typesNotEmpty = anyTypes.isNotEmpty()
                    if (typesNotEmpty) {
                        typeByAddr[ref.address] = anyTypes.single()
                    }
                    typesNotEmpty
                } else {
                    typeSystem.isSupertype(type, evaluatedType)
                }
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
    var isContradicting = false
        private set

    private val topTypeRegion by lazy {
        UTypeRegion(
            typeSystem,
            typeSystem.topTypeStream()
        )
    }

    private fun contradiction() {
        isContradicting = true
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
     * Constraints **either** the [ref] is null **or** the [ref] isn't null and the type of the [ref] to
     * be a subtype of the [type]. If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     */
    fun addSupertype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UConcreteHeapRef -> {
                require(ref.address > 0)
                val concreteType = concreteRefToType.getValue(ref.address)
                if (!typeSystem.isSupertype(type, concreteType)) {
                    contradiction()
                }
            }

            is UNullRef -> return

            else -> {
                val constraints = this[ref]
                val newConstraints = constraints.addSupertype(type)
                if (newConstraints.isContradicting) {
                    // the only left option here is to be equal to null
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
     * Constraints **both** the type of the [ref] to be a not subtype of the [type] and the [ref] not equals null.
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     */
    fun excludeSupertype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UConcreteHeapRef -> {
                require(ref.address > 0)
                val concreteType = concreteRefToType.getValue(ref.address)
                if (typeSystem.isSupertype(type, concreteType)) {
                    contradiction()
                }
            }

            is UNullRef -> contradiction() // the [ref] can't be equal to null

            else -> {
                val constraints = this[ref]
                val newConstraints = constraints.excludeSupertype(type)
                equalityConstraints.makeNonEqual(ref, ref.uctx.nullRef)
                if (newConstraints.isContradicting ||equalityConstraints.isContradicting) {
                    // the [ref] can't be equal to null
                    contradiction()
                } else {
                    // Inferring new symbolic disequalities here
                    for ((key, value) in symbolicRefToTypeRegion.entries) {
                        // TODO: cache intersections?
                        if (key != ref && value.intersect(newConstraints).isEmpty) {
                            // If we have two inputs of incomparable reference types, then they are either non equal,
                            // or both nulls
                            equalityConstraints.makeNonEqual(ref, key)
                        }
                    }
                    this[ref] = newConstraints
                }
            }
        }
    }

    /**
     * @return a type region corresponding to the [ref].
     */
    fun readTypeRegion(ref: UHeapRef): UTypeRegion<Type> =
        when (ref) {
            is UConcreteHeapRef -> {
                require(ref.address > 0)
                val concreteType = concreteRefToType[ref.address]
                val typeStream = if (concreteType == null) {
                    typeSystem.topTypeStream()
                } else {
                    USingleTypeStream(typeSystem, concreteType)
                }
                UTypeRegion(typeSystem, typeStream)
            }

            is UNullRef -> error("Null ref should be handled explicitly earlier")

            else -> {
                this[ref]
            }
        }

    private fun intersectConstraints(ref1: UHeapRef, ref2: UHeapRef) {
        this[ref1] = this[ref1].intersect(this[ref2])
    }

    /**
     * Evaluates the [ref] <: [type] in the current [UTypeConstraints]. Always returns true on null references.
     */
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
            symbolicMapper = mapper@{ symbolicRef ->
                if (symbolicRef == symbolicRef.uctx.nullRef) {
                    // accordingly to the [UIsExpr] specification, [nullRef] always satisfies the [type]
                    return@mapper symbolicRef.ctx.trueExpr
                }
                val typeRegion = this[symbolicRef]

                if (typeRegion.addSupertype(type).isContradicting) {
                    symbolicRef.ctx.falseExpr
                } else {
                    symbolicRef.uctx.mkIsExpr(symbolicRef, type)
                }
            },
            ignoreNullRefs = false
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

    /**
     * Checks if the [model] satisfies this [UTypeConstraints].
     *
     * @return [UTypeModel] if the [model] satisfies this [UTypeConstraints],
     * and constraints on reference equalities if the [model] doesn't satisfy this [UTypeConstraints].
     */
    fun verify(model: UModel): USolverResult<UTypeModel<Type>> {
        // firstly, group symbolic references by their interpretations in the [model]
        val concreteRefToTypeRegions = symbolicRefToTypeRegion
            .entries
            .groupBy { (key, _) -> (model.eval(key) as UConcreteHeapRef).address }
            .filter { it.key != NULL_ADDRESS } // we don't want to evaluate types of nulls

        val bannedRefEqualities = mutableListOf<UBoolExpr>()

        // then for each group check conflicting types
        val concreteToRegionWithCluster = concreteRefToTypeRegions.mapValues { (_, cluster) ->
            var currentRegion: UTypeRegion<Type>? = null
            val potentialConflictingRefs = mutableListOf<UHeapRef>()

            for ((heapRef, region) in cluster) {
                if (currentRegion == null) { // process the first element
                    currentRegion = region
                    potentialConflictingRefs.add(heapRef)
                } else {
                    val nextRegion = currentRegion.intersect(region) // add [heapRef] to the current region
                    if (nextRegion.isEmpty) {
                        // conflict detected, so it's impossible for [potentialConflictingRefs]
                        // to have the common type with [heapRef], therefore they can't be equal
                        val disjunct = potentialConflictingRefs.map {
                            with(it.ctx) { it.neq(heapRef) }
                        }
                        bannedRefEqualities += heapRef.ctx.mkOr(disjunct)
                    } else if (nextRegion === region) {
                        // the current [heapRef] gives the same region as the potentialConflictingRefs, so it's better
                        // to keep only the [heapRef] to minimize the disequalities amount in disjunction
                        potentialConflictingRefs.clear()
                        potentialConflictingRefs.add(heapRef)
                    } else if (nextRegion !== currentRegion) {
                        // no conflict detected, but the current region became smaller
                        potentialConflictingRefs.add(heapRef)
                    }

                    currentRegion = nextRegion
                }
            }
            checkNotNull(currentRegion)

            currentRegion to cluster
        }

        // if there were some conflicts, return constraints on reference equalities
        if (bannedRefEqualities.isNotEmpty()) {
            return UTypeUnsatResult(bannedRefEqualities)
        }

        // otherwise, return the type assignment
        val allConcreteRefToType = concreteRefToType.toMutableMap()
        concreteToRegionWithCluster.mapValuesTo(allConcreteRefToType) { (_, regionToCluster) ->
            val (region, cluster) = regionToCluster
            val resultList = mutableListOf<Type>()
            val terminated = region.typeStream.take(1, resultList)
            if (terminated) {
                if (resultList.isEmpty()) {
                    // the only way to reach here is when some of the clusters consists of a single reference
                    // because if the cluster is bigger, then we called region.isEmpty at least once previously
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
