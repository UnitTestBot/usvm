package org.usvm.types

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import org.usvm.regions.Region

/**
 * Class representing possible types which certain objects can have.
 */
class UTypeRegion<Type>(
    val typeSystem: UTypeSystem<Type>,
    val typeStream: UTypeStream<Type>,
    val supertypes: PersistentSet<Type> = persistentHashSetOf(),
    val notSupertypes: PersistentSet<Type> = persistentHashSetOf(),
    val subtypes: PersistentSet<Type> = persistentHashSetOf(),
    val notSubtypes: PersistentSet<Type> = persistentHashSetOf(),
) : Region<UTypeRegion<Type>> {
    /**
     * Returns region that represents empty set of types. Called when type
     * constraints contradict, for example if X <: Y and X </: Y.
     */
    private fun contradiction() = UTypeRegion(typeSystem, emptyTypeStream())

    override val isEmpty: Boolean get() = typeStream.isEmpty ?: false // Timeout here means that this type region **may** be not empty

    private val <Type> UTypeRegion<Type>.size: Int
        get() = supertypes.size + notSupertypes.size + subtypes.size + notSubtypes.size

    /**
     * Excludes from this type region types which are not subtypes of [supertype].
     * If new type constraints contradict with already existing ones,
     * then implementation must return empty region (see [contradiction]).
     *
     * The default implementation checks for the following contradictions
     * (here X is type from this region and t is [supertype]):
     *  - X <: t && t <: u && X </: u, i.e. if [notSupertypes] contains supertype of [supertype]
     *  - X <: t && u <: X && u </: t
     *  - X <: t && X <: u && u </: t && t </: u && t and u can't be multiply inherited
     *  - t is final && t </: X && X <: t
     *
     * Also, if u <: X && X <: t && u <: t, then X = u = t.
     */
    fun addSupertype(supertype: Type): UTypeRegion<Type> {
        // X <: it && it <: supertype -> nothing changes
        if (isEmpty || supertypes.any { typeSystem.isSupertype(supertype, it) }) {
            return this
        }

        // X </: it && supertype <: it -> X </: supertype
        if (notSupertypes.any { typeSystem.isSupertype(it, supertype) }) {
            return contradiction()
        }

        // it <: X && it </: supertype -> X </: supertype
        if (subtypes.any { !typeSystem.isSupertype(supertype, it) }) {
            return contradiction()
        }

        if (!typeSystem.hasCommonSubtype(supertype, supertypes)) {
            return contradiction()
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

        // it <: X && supertype <: it -> X == supertype
        if (newSubtypes.any { typeSystem.isSupertype(it, supertype) }) {
            return checkSingleTypeRegion(supertype)
        }

        val newSupertypes = supertypes.removeAll { typeSystem.isSupertype(it, supertype) }.add(supertype)
        val newTypeStream = typeStream.filterBySupertype(supertype)

        return clone(newTypeStream, supertypes = newSupertypes, subtypes = newSubtypes)
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
    fun excludeSupertype(notSupertype: Type): UTypeRegion<Type> {
        if (isEmpty || notSupertypes.any { typeSystem.isSupertype(it, notSupertype) }) {
            return this
        }

        if (supertypes.any { typeSystem.isSupertype(notSupertype, it) }) {
            return contradiction()
        }

        val newNotSupertypes = notSupertypes.removeAll { typeSystem.isSupertype(notSupertype, it) }.add(notSupertype)
        val newTypeStream = typeStream.filterByNotSupertype(notSupertype)

        return clone(newTypeStream, notSupertypes = newNotSupertypes)
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
     *
     *  Also, if t <: X && X <: u && u <: t, then X = u = t.
     */
    fun addSubtype(subtype: Type): UTypeRegion<Type> {
        // it <: X && subtype <: it -> nothing changes
        if (isEmpty || subtypes.any { typeSystem.isSupertype(it, subtype) }) {
            return this
        }

        // it </: X && it <: subtype -> subtype </: X
        if (notSubtypes.any { typeSystem.isSupertype(subtype, it) }) {
            return contradiction()
        }

        // X <: it && subtype </: it -> subtype </: X
        if (supertypes.any { !typeSystem.isSupertype(it, subtype) }) {
            return contradiction()
        }

        // X <: it && it <: subtype -> X <: subtype -> X == subtype
        if (supertypes.any { typeSystem.isSupertype(subtype, it) }) {
            return checkSingleTypeRegion(subtype)
        }

        val newSubtypes = subtypes.removeAll { typeSystem.isSupertype(subtype, it) }.add(subtype)
        val newTypeStream = typeStream.filterBySubtype(subtype)

        return clone(newTypeStream, subtypes = newSubtypes)
    }

    /**
     * Excludes from this region supertypes of [notSubtype].
     * If new type constraints contradict, then implementation must return empty region (see [contradiction]).
     * The default implementation checks for the following contradictions:
     *  - u <: X && t <: u && t </: X, i.e. if [subtypes] contains supertype of [notSubtype]
     *  - t is final && t </: X && X <: t
     */
    fun excludeSubtype(notSubtype: Type): UTypeRegion<Type> {
        if (isEmpty || notSubtypes.any { typeSystem.isSupertype(notSubtype, it) }) {
            return this
        }

        if (subtypes.any { typeSystem.isSupertype(it, notSubtype) }) {
            return contradiction()
        }

        if (typeSystem.isFinal(notSubtype) && supertypes.contains(notSubtype)) {
            return contradiction()
        }

        val newNotSubtypes = notSubtypes.removeAll { typeSystem.isSupertype(it, notSubtype) }.add(notSubtype)
        val newTypeStream = typeStream.filterByNotSubtype(notSubtype)

        return clone(newTypeStream, notSubtypes = newNotSubtypes)
    }

    override fun intersect(other: UTypeRegion<Type>): UTypeRegion<Type> {
        if (this == other) {
            return this
        }
        // TODO: optimize things up by not re-allocating type regions after each operation
        val otherSize = other.size
        val thisSize = this.size
        val (smallRegion, largeRegion) = if (otherSize < thisSize) {
            other to this
        } else {
            this to other
        }
        if (smallRegion.isEmpty) {
            return smallRegion
        }

        val result1 = smallRegion.supertypes.fold(largeRegion) { acc, t -> acc.addSupertype(t) }
        val result2 = smallRegion.notSupertypes.fold(result1) { acc, t -> acc.excludeSupertype(t) }
        val result3 = smallRegion.subtypes.fold(result2) { acc, t -> acc.addSubtype(t) }
        return smallRegion.notSubtypes.fold(result3) { acc, t -> acc.excludeSubtype(t) }
    }

    override fun subtract(other: UTypeRegion<Type>): UTypeRegion<Type> {
        if (isEmpty || other.isEmpty) {
            return this
        }
        if (other.notSupertypes.isNotEmpty() || other.notSubtypes.isEmpty() || other.supertypes.size + other.subtypes.size != 1) {
            TODO("For now, we are able to subtract only positive singleton type constraints")
        }

        require(other.notSupertypes.isEmpty() && other.notSubtypes.isEmpty())
        require(other.supertypes.size + other.subtypes.size == 1)

        val bannedSupertypes = other.supertypes.fold(this) { acc, t -> acc.excludeSupertype(t) }

        return other.subtypes.fold(bannedSupertypes) { acc, t -> acc.excludeSubtype(t) }
    }

    override fun compare(other: UTypeRegion<Type>): Region.ComparisonResult {
        if (intersect(other).isEmpty) {
            return Region.ComparisonResult.DISJOINT
        }

        if (other.subtract(this).isEmpty) {
            return Region.ComparisonResult.INCLUDES
        }

        return Region.ComparisonResult.INTERSECTS
    }

    private fun checkSingleTypeRegion(type: Type): UTypeRegion<Type> {
        if (!typeSystem.isInstantiable(type)) {
            return contradiction()
        }

        // X <: it && type </: it && X == type -> X <: X && X </: X
        if (supertypes.any { !typeSystem.isSupertype(it, type) }) {
            return contradiction()
        }

        // X </: it && type <: it && X == type -> X </: it && X <: it
        if (notSupertypes.any { typeSystem.isSupertype(it, type) }) {
            return contradiction()
        }

        // it <: X && it </: type && X == type -> it <: X && it </: X
        if (subtypes.any { !typeSystem.isSupertype(type, it) }) {
            return contradiction()
        }

        // it </: X && it <: type && X == type -> it </: X && it <: X
        if (notSubtypes.any { typeSystem.isSupertype(type, it) }) {
            return contradiction()
        }

        return clone(
            USingleTypeStream(typeSystem, type),
            supertypes = persistentHashSetOf(type),
            subtypes = persistentHashSetOf(type)
        )
    }

    override fun union(other: UTypeRegion<Type>): UTypeRegion<Type> {
        TODO("Not yet implemented")
    }

    private fun clone(
        typeStream: UTypeStream<Type>,
        supertypes: PersistentSet<Type> = this.supertypes,
        notSupertypes: PersistentSet<Type> = this.notSupertypes,
        subtypes: PersistentSet<Type> = this.subtypes,
        notSubtypes: PersistentSet<Type> = this.notSubtypes,
    ) = UTypeRegion(typeSystem, typeStream, supertypes, notSupertypes, subtypes, notSubtypes)

    companion object {
        fun <Type> fromSingleType(typeSystem: UTypeSystem<Type>, type: Type): UTypeRegion<Type> = UTypeRegion(
            typeSystem,
            USingleTypeStream(typeSystem, type),
            supertypes = persistentHashSetOf(type),
            subtypes = persistentHashSetOf(type)
        )
    }
}
