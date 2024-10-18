package org.usvm.constraints

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USymbolicHeapRef
import org.usvm.UNullRef
import org.usvm.collections.immutable.getOrDefault
import org.usvm.isStatic
import org.usvm.isStaticHeapRef
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.toMutableMap
import org.usvm.memory.mapWithStaticAsConcrete
import org.usvm.merging.MutableMergeGuard
import org.usvm.merging.UOwnedMergeable
import org.usvm.solver.UExprTranslator
import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeRegion
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.uctx


interface UTypeEvaluator<Type> {

    /**
     * Check that [ref] = `null` or type([ref]) <: [supertype].
     * Note that T <: T always holds.
     * */
    fun evalIsSubtype(ref: UHeapRef, supertype: Type): UBoolExpr

    /**
     * Check that [ref] != `null` and [subtype] <: type([ref]).
     * Note that T <: T always holds.
     * */
    fun evalIsSupertype(ref: UHeapRef, subtype: Type): UBoolExpr
    fun getTypeStream(ref: UHeapRef): UTypeStream<Type>
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
    private var ownership: MutabilityOwnership,
    private val typeSystem: UTypeSystem<Type>,
    private val equalityConstraints: UEqualityConstraints,
    private var concreteRefToType: UPersistentHashMap<UConcreteHeapAddress, Type> = persistentHashMapOf(),
    private var symbolicRefToTypeRegion: UPersistentHashMap<USymbolicHeapRef, UTypeRegion<Type>> = persistentHashMapOf(),
) : UTypeEvaluator<Type>, UOwnedMergeable<UTypeConstraints<Type>, MutableMergeGuard> {
    private val ctx: UContext<*> get() = equalityConstraints.ctx

    fun changeOwnership(ownership: MutabilityOwnership) {
        this.ownership = ownership
    }

    init {
        equalityConstraints.subscribeEquality(::intersectRegions)
    }
    val inputRefToTypeRegion: Map<UHeapRef, UTypeRegion<Type>>
        get(): Map<UHeapRef, UTypeRegion<Type>> {
            @Suppress("UNCHECKED_CAST")
            val inputTypeRegions: MutableMap<UHeapRef, UTypeRegion<Type>> =
                symbolicRefToTypeRegion.toMutableMap() as MutableMap<UHeapRef, UTypeRegion<Type>>

            // Add all static refs
            for ((address, type) in concreteRefToType) {
                if (!address.isStatic) {
                    continue
                }

                inputTypeRegions[ctx.mkConcreteHeapRef(address)] = UTypeRegion.fromSingleType(typeSystem, type)
            }

            return inputTypeRegions
        }

    /**
     * Returns true if the current type and equality constraints are unsatisfiable (syntactically).
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
        concreteRefToType = concreteRefToType.put(ref, type, ownership)

        equalityConstraints.updateDisequality(ctx.mkConcreteHeapRef(ref))
    }

    /**
     * Checks that the static ref [staticRef] can be equal by reference to the symbolic ref [symbolicRef]
     * according to their types, i.e., does a [UTypeStream] for the [symbolicRef] `contain` a concrete type of the [staticRef].
     */
    internal fun canStaticRefBeEqualToSymbolic(staticRef: UConcreteHeapRef, symbolicRef: USymbolicHeapRef): Boolean {
        require(isStaticHeapRef(staticRef)) {
            "Expected static ref but $staticRef was passed"
        }

        // It is important to use a type region instead of a type stream
        // since it is able to find simple semantic contradiction
        // significantly improving performance
        val typeRegion = getTypeRegion(symbolicRef)
        if (typeRegion.isEmpty) {
            // An empty type region is possible only for the null ref, and static ref could not be equal to it
            return false
        }

        val concreteType = concreteRefToType[staticRef.address] ?: error("Unknown type of the static ref $staticRef")

        return !typeRegion.addSupertype(concreteType).isEmpty
    }

    /**
     * @param useRepresentative use the representative from the [equalityConstraints] for the [symbolicRef]. The false
     * value used, when we intersect regions on their refs union.
     * @see intersectRegions
     */
    private fun getTypeRegion(symbolicRef: USymbolicHeapRef, useRepresentative: Boolean = true): UTypeRegion<Type> {
        val representative = if (useRepresentative) {
            equalityConstraints.findRepresentative(symbolicRef)
        } else {
            symbolicRef
        }

        // The representative can be a static ref, so we need to construct a type region from its concrete type.
        if (representative is UConcreteHeapRef) {
            return concreteRefToType[representative.address]?.let {
                UTypeRegion.fromSingleType(typeSystem, it)
            } ?: topTypeRegion
        }

        return symbolicRefToTypeRegion.getOrDefault(representative as USymbolicHeapRef, topTypeRegion)
    }


    private fun setTypeRegion(symbolicRef: USymbolicHeapRef, value: UTypeRegion<Type>) {
        val representative = equalityConstraints.findRepresentative(symbolicRef)
        if (representative is UConcreteHeapRef) {
            // No need to set a type region for static refs as they already have a concrete type.
            return
        }

        symbolicRefToTypeRegion = symbolicRefToTypeRegion.put(representative as USymbolicHeapRef, value, ownership)
    }

    /**
     * Constraints **either** the [ref] is null **or** the [ref] isn't null and the type of the [ref] to
     * be a subtype of the [supertype]. If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to cast types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSubtype] function.
     */
    internal fun addSupertype(ref: UHeapRef, supertype: Type) {
        when (ref) {
            is UNullRef -> return

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType[ref.address]!!
                if (!typeSystem.isSupertype(supertype, concreteType)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCanBeEqualNull(ref) { it.addSupertype(supertype) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * Constraints **both** the type of the [ref] to be a not subtype of the [supertype] and the [ref] not equals null.
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to exclude types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSubtype] function.
     */
    internal fun excludeSupertype(ref: UHeapRef, supertype: Type) {
        when (ref) {
            is UNullRef -> contradiction() // the [ref] can't be equal to null

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType[ref.address]!!
                if (typeSystem.isSupertype(supertype, concreteType)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCannotBeEqualNull(ref) { it.excludeSupertype(supertype) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * Constraints the [ref] isn't null and the type of the [ref] to
     * be a supertype of the [subtype]. If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to cast types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSupertype] function.
     */
    internal fun addSubtype(ref: UHeapRef, subtype: Type) {
        when (ref) {
            is UNullRef -> contradiction()

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType[ref.address]!!
                if (!typeSystem.isSupertype(concreteType, subtype)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCannotBeEqualNull(ref) { it.addSubtype(subtype) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * Constraints **either** the [ref] is null or the [ref] isn't null and the type of
     * the [ref] to be a not supertype of the [subtype].
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to exclude types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSupertype] function.
     */
    internal fun excludeSubtype(ref: UHeapRef, subtype: Type) {
        when (ref) {
            is UNullRef -> return

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType[ref.address]!!
                if (typeSystem.isSupertype(concreteType, subtype)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCanBeEqualNull(ref) { it.excludeSubtype(subtype) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * @return a type stream corresponding to the [ref].
     */
    override fun getTypeStream(ref: UHeapRef): UTypeStream<Type> =
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType[ref.address]
                val typeStream = if (concreteType == null) {
                    typeSystem.topTypeStream()
                } else {
                    USingleTypeStream(typeSystem, concreteType)
                }
                typeStream
            }

            is UNullRef -> error("Null ref should be handled explicitly earlier")

            is USymbolicHeapRef -> {
                getTypeRegion(ref).typeStream
            }

            else -> error("Unexpected ref: $ref")
        }

    private fun intersectRegions(to: UHeapRef, from: UHeapRef) {
        when {
            to is USymbolicHeapRef && from is USymbolicHeapRef -> {
                // For both symbolic refs we need to intersect both regions
                val region = getTypeRegion(from, useRepresentative = false).intersect(getTypeRegion(to))
                updateRegionCanBeEqualNull(to, region::intersect)
            }

            to is UConcreteHeapRef && from is UConcreteHeapRef -> {
                // For both concrete refs we need to check types are the same
                val toType = concreteRefToType[to.address]
                val fromType = concreteRefToType[from.address]

                if (toType != fromType) {
                    contradiction()
                }
            }

            to is UConcreteHeapRef -> {
                // Here we have a pair of symbolic-concrete refs
                val concreteToType = concreteRefToType[to.address]!!
                val symbolicFromType = getTypeRegion(from as USymbolicHeapRef, useRepresentative = false)

                if (symbolicFromType.addSupertype(concreteToType).isEmpty) {
                    contradiction()
                }
            }

            from is UConcreteHeapRef -> {
                // Here to is symbolic and from is concrete
                val concreteType = concreteRefToType[from.address]!!
                val symbolicType = getTypeRegion(to as USymbolicHeapRef)
                // We need to set only the concrete type instead of all these symbolic types - make it using both subtype/supertype
                val regionFromConcreteType = symbolicType.addSubtype(concreteType).addSupertype(concreteType)

                // static could not be equal null
                updateRegionCannotBeEqualNull(to, regionFromConcreteType::intersect)
            }

            else -> error("Unexpected refs $to, $from")
        }
    }

    private inline fun updateRegionCannotBeEqualNull(
        ref: USymbolicHeapRef,
        regionMapper: (UTypeRegion<Type>) -> UTypeRegion<Type>,
    ) {
        val region = getTypeRegion(ref)
        val newRegion = regionMapper(region)
        if (newRegion == region) {
            return
        }
        equalityConstraints.makeNonEqual(ref, ref.uctx.nullRef)
        if (newRegion.isEmpty || equalityConstraints.isContradicting) {
            contradiction()
            return
        }
        for ((key, value) in symbolicRefToTypeRegion) {
            // TODO: cache intersections?
            if (key != ref && value.intersect(newRegion).isEmpty) {
                // If we have two inputs of incomparable reference types, then they are non equal
                equalityConstraints.makeNonEqual(ref, key)
            }
        }
        setTypeRegion(ref, newRegion)
    }

    private inline fun updateRegionCanBeEqualNull(
        ref: USymbolicHeapRef,
        regionMapper: (UTypeRegion<Type>) -> UTypeRegion<Type>,
    ) {
        val region = getTypeRegion(ref)
        val newRegion = regionMapper(region)
        if (newRegion == region) {
            return
        }
        if (newRegion.isEmpty) {
            equalityConstraints.makeEqual(ref, ref.uctx.nullRef)
        }
        for ((key, value) in symbolicRefToTypeRegion) {
            // TODO: cache intersections?
            if (key != ref && value.intersect(newRegion).isEmpty) {
                // If we have two inputs of incomparable reference types, then they are non equal or both null
                equalityConstraints.makeNonEqualOrBothNull(ref, key)
            }
        }
        setTypeRegion(ref, newRegion)
    }

    /**
     * Evaluates the [ref] <: [supertype] in the current [UTypeConstraints]. Always returns true on null references.
     */
    override fun evalIsSubtype(ref: UHeapRef, supertype: Type): UBoolExpr =
        ref.mapWithStaticAsConcrete(
            concreteMapper = { concreteRef ->
                val concreteType = concreteRefToType[concreteRef.address]!!
                if (typeSystem.isSupertype(supertype, concreteType)) {
                    concreteRef.ctx.trueExpr
                } else {
                    concreteRef.ctx.falseExpr
                }
            },
            symbolicMapper = mapper@{ symbolicRef ->
                if (symbolicRef == symbolicRef.uctx.nullRef) {
                    // accordingly to the [UIsSubtypeExpr] specification, [nullRef] always satisfies the [type]
                    return@mapper symbolicRef.ctx.trueExpr
                }
                val typeRegion = getTypeRegion(symbolicRef)

                if (typeRegion.addSupertype(supertype).isEmpty) {
                    symbolicRef.uctx.mkEq(symbolicRef, symbolicRef.uctx.nullRef)
                } else {
                    symbolicRef.uctx.mkIsSubtypeExpr(symbolicRef, supertype)
                }
            },
            ignoreNullRefs = false
        )

    override fun evalIsSupertype(ref: UHeapRef, subtype: Type): UBoolExpr =
        ref.mapWithStaticAsConcrete(
            concreteMapper = { concreteRef ->
                val concreteType = concreteRefToType[concreteRef.address]!!
                if (typeSystem.isSupertype(concreteType, subtype)) {
                    concreteRef.ctx.trueExpr
                } else {
                    concreteRef.ctx.falseExpr
                }
            },
            symbolicMapper = mapper@{ symbolicRef ->
                if (symbolicRef == symbolicRef.uctx.nullRef) {
                    // accordingly to the [UIsSupertypeExpr] specification, on [nullRef] return false
                    return@mapper symbolicRef.ctx.falseExpr
                }
                val typeRegion = getTypeRegion(symbolicRef)

                if (typeRegion.addSubtype(subtype).isEmpty) {
                    symbolicRef.ctx.falseExpr
                } else {
                    symbolicRef.uctx.mkIsSupertypeExpr(symbolicRef, subtype)
                }
            },
            ignoreNullRefs = false
        )

    /**
     * Creates a mutable copy of these constraints connected to new instance of [equalityConstraints].
     */
    fun clone(
        equalityConstraints: UEqualityConstraints,
        thisOwnership: MutabilityOwnership,
        cloneOwnership: MutabilityOwnership
    ) = UTypeConstraints(
        cloneOwnership,
        typeSystem,
        equalityConstraints,
        concreteRefToType,
        symbolicRefToTypeRegion
    ).also { this.ownership = thisOwnership }

    /**
     * Check if this [UTypeConstraints] can be merged with [other] type constraints.
     *
     * Ignores [equalityConstraints]. Verifies, that [inputRefToTypeRegion] equals to
     * [other]`.symbolicRefToTypeRegion`. Merges the type constraints for concrete references.
     *
     * @return the merged type constraints.
     */
    override fun mergeWith(
        other: UTypeConstraints<Type>,
        by: MutableMergeGuard,
        thisOwnership: MutabilityOwnership,
        otherOwnership: MutabilityOwnership,
        mergedOwnership: MutabilityOwnership
    ): UTypeConstraints<Type>? {
        // TODO: should we check equality constraints?
        if (symbolicRefToTypeRegion != other.symbolicRefToTypeRegion) {
            return null
        }
        val mergedConcreteRefs = concreteRefToType.putAll(other.concreteRefToType, mergedOwnership)
        this.ownership = thisOwnership
        other.ownership = otherOwnership
        return UTypeConstraints(mergedOwnership, typeSystem, equalityConstraints, mergedConcreteRefs, symbolicRefToTypeRegion)
    }


    @Suppress("UNUSED_PARAMETER")
    fun constraints(translator: UExprTranslator<Type, *>): Sequence<UBoolExpr> {
        return emptySequence()
    }
}
