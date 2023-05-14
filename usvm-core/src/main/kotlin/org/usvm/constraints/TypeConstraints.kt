package org.usvm.constraints

import org.usvm.*

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
        symbolicTypes[equalityConstraints.equalReferences.find(symbolicRef)] ?: UTypeRegion(typeSystem)

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
        UTypeConstraints(typeSystem, equalityConstraints, concreteTypes.toMutableMap(), symbolicTypes.toMutableMap())
}
