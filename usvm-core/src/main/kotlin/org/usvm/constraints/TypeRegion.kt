package org.usvm.constraints

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UTypeSystem
import org.usvm.util.Region
import org.usvm.util.RegionComparisonResult

/**
 * Class representing possible types which certain objects can have.
 */
open class UTypeRegion<Type>(
    val typeSystem: UTypeSystem<Type>,
    val supertypes: PersistentSet<Type> = persistentSetOf(),
    val notSupertypes: PersistentSet<Type> = persistentSetOf(),
    val subtypes: PersistentSet<Type> = persistentSetOf(),
    val notSubtypes: PersistentSet<Type> = persistentSetOf(),
    val isContradicting: Boolean = false,
) : Region<UTypeRegion<Type>> {

    /**
     * Returns region that represents empty set of types. Called when type
     * constraints contradict, for example if X <: Y and X </: Y.
     */
    protected fun contradiction() = UTypeRegion(typeSystem, isContradicting = true)
    // TODO: generate unsat core for DPLL(T)

    /**
     * Excludes from this type region types which are not subtypes of [supertype].
     * If new type constraints contradict with already existing ones,
     * then implementation must return empty region (see [contradiction]).
     *
     * The default implementation checks for the following contradictions
     * (here X is type from this region and t is [supertype]):
     *  - X <: t && t <: u && X </: u, i.e. if [notSupertypes] contains supertype of [supertype]
     *  - X <: t && X <: u && u </: t && t </: u && t and u can't be multiply inherited
     *  - t is final && t </: X && X <: t
     */
    open fun addSupertype(supertype: Type): UTypeRegion<Type> {
        if (isContradicting || supertypes.any { typeSystem.isSupertype(supertype, it) }) {
            return this
        }

        if (notSubtypes.any { typeSystem.isSupertype(it, supertype) }) {
            return contradiction()
        }

        val multipleInheritanceIsNotAllowed = !typeSystem.isMultipleInheritanceAllowedFor(supertype)
        if (multipleInheritanceIsNotAllowed) {
            // We've already checked it </: supertype
            val incomparableSupertypeWithoutMultipleInheritanceAllowedExists = supertypes.any {
                !typeSystem.isMultipleInheritanceAllowedFor(it) && !typeSystem.isSupertype(it, supertype)
            }

            if (incomparableSupertypeWithoutMultipleInheritanceAllowedExists) {
                return contradiction()
            }
        }

        val newSubtypes = if (typeSystem.isFinal(supertype)) {
            if (notSubtypes.contains(supertype)) {
                return contradiction()
            }

            // If X <: t and t is final, then X = t, or equivalently t <: X
            subtypes.add(supertype)
        } else {
            subtypes
        }

        val newSupertypes = supertypes.removeAll { typeSystem.isSupertype(it, supertype) }.add(supertype)

        return UTypeRegion(typeSystem, supertypes = newSupertypes, subtypes = newSubtypes)
    }

    /**
     * Excludes from this region subtypes of [notSupertype].
     * If new type constraints contradict with already existing ones,
     * then implementation must return empty region (see [contradiction]).
     *
     * The default implementation checks for the following contradiction
     * (here X is type from this region and t is [notSupertype]):
     *  X <: u && u <: t && X </: t, i.e. if [supertypes] contains subtype of [notSupertype]
     */
    protected open fun excludeSupertype(notSupertype: Type): UTypeRegion<Type> {
        if (isContradicting || notSupertypes.any { typeSystem.isSupertype(it, notSupertype) }) {
            return this
        }

        if (supertypes.any { typeSystem.isSupertype(notSupertype, it) }) {
            return contradiction()
        }

        val newNotSupertypes = notSupertypes.removeAll { typeSystem.isSupertype(notSupertype, it) }.add(notSupertype)

        return UTypeRegion(typeSystem, notSupertypes = newNotSupertypes)
    }

    /**
     * Excludes from this type region types which are not supertypes of [subtype].
     * If new type constraints contradict with already existing ones,
     * then implementation must return empty region (see [contradiction]).
     *
     * The default implementation checks for the following contradictions
     * (here X is type from this region and t is [subtype]):
     *  - t <: X && u <: t && u </: X, i.e. if [notSubtypes] contains subtype of [subtype]
     *  - t <: X && X <: u && t </: u
     */
    protected open fun addSubtype(subtype: Type): UTypeRegion<Type> {
        if (isContradicting || subtypes.any { typeSystem.isSupertype(it, subtype) }) {
            return this
        }

        if (notSubtypes.any { typeSystem.isSupertype(subtype, it) }) {
            return contradiction()
        }

        if (supertypes.any { !typeSystem.isSupertype(it, subtype) }) {
            return contradiction()
        }

        val newSubtypes = subtypes.removeAll { typeSystem.isSupertype(subtype, it) }.add(subtype)

        return UTypeRegion(typeSystem, subtypes = newSubtypes)
    }

    /**
     * Excludes from this region supertypes of [notSubtype].
     * If new type constraints contradict, then implementation must return empty region (see [contradiction]).
     * The default implementation checks for the following contradictions:
     *  - u <: X && t <: u && t </: X, i.e. if [subtypes] contains supertype of [notSubtype]
     *  - t is final && t </: X && X <: t
     */
    protected open fun excludeSubtype(notSubtype: Type): UTypeRegion<Type> {
        if (isContradicting || notSubtypes.any { typeSystem.isSupertype(notSubtype, it) }) {
            return this
        }

        if (subtypes.any { typeSystem.isSupertype(it, notSubtype) }) {
            return contradiction()
        }

        if (typeSystem.isFinal(notSubtype) && supertypes.contains(notSubtype)) {
            return contradiction()
        }

        val newNotSubtypes = notSubtypes.removeAll { typeSystem.isSupertype(it, notSubtype) }.add(notSubtype)

        return UTypeRegion(typeSystem, notSubtypes = newNotSubtypes)
    }

    override val isEmpty: Boolean = isContradicting

    override fun intersect(other: UTypeRegion<Type>): UTypeRegion<Type> {
        // TODO: optimize things up by not re-allocating type regions after each operation
        val result1 = other.supertypes.fold(this) { acc, t -> acc.addSupertype(t) }
        val result2 = other.notSupertypes.fold(result1) { acc, t -> acc.excludeSupertype(t) }
        val result3 = other.subtypes.fold(result2) { acc, t -> acc.addSubtype(t) }
        return other.notSubtypes.fold(result3) { acc, t -> acc.excludeSubtype(t) }
    }

    override fun subtract(other: UTypeRegion<Type>): UTypeRegion<Type> {
        if (other.notSupertypes.isNotEmpty() || other.notSubtypes.isEmpty() || other.supertypes.size + other.subtypes.size != 1) {
            TODO("For now, we are able to subtract only positive singleton type constraints")
        }

        require(other.notSupertypes.isEmpty() && other.notSubtypes.isEmpty())
        require(other.supertypes.size + other.subtypes.size == 1)

        val bannedSupertypes = other.supertypes.fold(this) { acc, t -> acc.excludeSupertype(t) }

        return other.subtypes.fold(bannedSupertypes) { acc, t -> acc.excludeSubtype(t) }
    }

    override fun compare(other: UTypeRegion<Type>): RegionComparisonResult {
        if (intersect(other).isEmpty) {
            return RegionComparisonResult.DISJOINT
        }

        if (other.subtract(this).isEmpty) {
            return RegionComparisonResult.INCLUDES
        }

        return RegionComparisonResult.INTERSECTS
    }

}
