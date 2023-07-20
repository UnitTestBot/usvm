package org.usvm.constraints

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USymbolicHeapRef
import org.usvm.memory.map
import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeRegion
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.uctx

interface UTypeEvaluator<Type> {
    fun evalIsSubtype(ref: UHeapRef, supertype: Type): UBoolExpr
    fun evalIsSupertype(ref: UHeapRef, subtype: Type): UBoolExpr
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
open class UTypeConstraints<Type>(
    private val typeSystem: UTypeSystem<Type>,
    private val equalityConstraints: UEqualityConstraints,
    protected val concreteRefToType: MutableMap<UConcreteHeapAddress, Type> = mutableMapOf(),
    symbolicRefToTypeRegion: MutableMap<USymbolicHeapRef, UTypeRegion<Type>> = mutableMapOf(),
) : UTypeEvaluator<Type> {
    init {
        equalityConstraints.subscribe(::intersectRegions)
    }

    val symbolicRefToTypeRegion get(): Map<USymbolicHeapRef, UTypeRegion<Type>> = _symbolicRefToTypeRegion

    private val _symbolicRefToTypeRegion = symbolicRefToTypeRegion

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

    protected fun contradiction() {
        isContradicting = true
    }

    /**
     * Binds concrete heap address [ref] to its [type].
     */
    fun allocate(ref: UConcreteHeapAddress, type: Type) {
        concreteRefToType[ref] = type
    }

    fun getTypeRegion(symbolicRef: USymbolicHeapRef, useRepresentative: Boolean = true): UTypeRegion<Type> {
        val representative = if (useRepresentative) equalityConstraints.equalReferences.find(symbolicRef) else symbolicRef
        return _symbolicRefToTypeRegion[representative] ?: topTypeRegion
    }


    private fun setTypeRegion(symbolicRef: USymbolicHeapRef, value: UTypeRegion<Type>) {
        _symbolicRefToTypeRegion[equalityConstraints.equalReferences.find(symbolicRef)] = value
    }

    /**
     * Constraints **either** the [ref] is null **or** the [ref] isn't null and the type of the [ref] to
     * be a subtype of the [type]. If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to cast types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSubtype] function.
     */
    internal fun addSupertype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UNullRef -> return

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType.getValue(ref.address)
                if (!typeSystem.isSupertype(type, concreteType)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCanBeEqualNull(ref) { it.addSupertype(type) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * Constraints **both** the type of the [ref] to be a not subtype of the [type] and the [ref] not equals null.
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to exclude types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSubtype] function.
     */
    internal fun excludeSupertype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UNullRef -> contradiction() // the [ref] can't be equal to null

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType.getValue(ref.address)
                if (typeSystem.isSupertype(type, concreteType)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCannotBeEqualNull(ref) { it.excludeSupertype(type) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * Constraints the [ref] isn't null and the type of the [ref] to
     * be a supertype of the [type]. If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to cast types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSupertype] function.
     */
    internal fun addSubtype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UNullRef -> contradiction()

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType.getValue(ref.address)
                if (!typeSystem.isSupertype(concreteType, type)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCannotBeEqualNull(ref) { it.addSubtype(type) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * Constraints **either** the [ref] is null or the [ref] isn't null and the type of
     * the [ref] to be a not supertype of the [type].
     * If it is impossible within current type and equality constraints,
     * then type constraints become contradicting (@see [isContradicting]).
     *
     * NB: this function **must not** be used to exclude types in interpreters.
     * To do so you should add corresponding constraint using [evalIsSupertype] function.
     */
    internal fun excludeSubtype(ref: UHeapRef, type: Type) {
        when (ref) {
            is UNullRef -> return

            is UConcreteHeapRef -> {
                val concreteType = concreteRefToType.getValue(ref.address)
                if (typeSystem.isSupertype(concreteType, type)) {
                    contradiction()
                }
            }

            is USymbolicHeapRef -> {
                updateRegionCanBeEqualNull(ref) { it.excludeSubtype(type) }
            }

            else -> error("Provided heap ref must be either concrete or purely symbolic one, found $ref")
        }
    }

    /**
     * @return a type stream corresponding to the [ref].
     */
    internal fun getTypeStream(ref: UHeapRef): UTypeStream<Type> =
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

    private fun intersectRegions(to: USymbolicHeapRef, from: USymbolicHeapRef) {
        val region = getTypeRegion(from, useRepresentative = false).intersect(getTypeRegion(to))
        updateRegionCanBeEqualNull(to, region::intersect)
    }

    protected fun updateRegionCannotBeEqualNull(
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
        for ((key, value) in _symbolicRefToTypeRegion.entries) {
            // TODO: cache intersections?
            if (key != ref && value.intersect(newRegion).isEmpty) {
                // If we have two inputs of incomparable reference types, then they are non equal
                equalityConstraints.makeNonEqual(ref, key)
            }
        }
        setTypeRegion(ref, newRegion)
    }

    protected fun updateRegionCanBeEqualNull(
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
        for ((key, value) in _symbolicRefToTypeRegion.entries) {
            // TODO: cache intersections?
            if (key != ref && value.intersect(newRegion).isEmpty) {
                // If we have two inputs of incomparable reference types, then they are non equal
                equalityConstraints.makeNonEqualOrBothNull(ref, key)
            }
        }
        setTypeRegion(ref, newRegion)
    }

    /**
     * Evaluates the [ref] <: [supertype] in the current [UTypeConstraints]. Always returns true on null references.
     */
    override fun evalIsSubtype(ref: UHeapRef, supertype: Type): UBoolExpr =
        ref.map(
            concreteMapper = { concreteRef ->
                val concreteType = concreteRefToType.getValue(concreteRef.address)
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
                    symbolicRef.ctx.falseExpr
                } else {
                    symbolicRef.uctx.mkIsSubtypeExpr(symbolicRef, supertype)
                }
            },
            ignoreNullRefs = false
        )

    override fun evalIsSupertype(ref: UHeapRef, subtype: Type): UBoolExpr =
        ref.map(
            concreteMapper = { concreteRef ->
                val concreteType = concreteRefToType.getValue(concreteRef.address)
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
    fun clone(equalityConstraints: UEqualityConstraints) =
        UTypeConstraints(
            typeSystem,
            equalityConstraints,
            concreteRefToType.toMutableMap(),
            _symbolicRefToTypeRegion.toMutableMap()
        )
}
