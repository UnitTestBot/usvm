package org.usvm.types

/**
 * A base interface representing persistent type constraints and a way to access types satisfying them.
 * Consists of a conjunction of constraints of four kinds:
 *
 * 1. x <: T, i.e. object referenced in x inherits T (supertype constraints for x)
 * 2. T <: x, i.e. object referenced in x inherited by T (subtype constraints for x)
 * 3. x </: T, i.e. object referenced in x does not inherit T (notSupertype constraints for x)
 * 4. T </: x, i.e. object referenced in x is not inherited by T (notSubtype constraints for x)
 *
 * To collect types satisfying constraints use [take] function.
 */
interface UTypeStream<Type> {
    /**
     * Excludes from this type stream types which are not subtypes of [type].
     */
    fun filterBySupertype(type: Type): UTypeStream<Type>

    /**
     * Excludes from this type stream types which are not supertypes of [type].
     */
    fun filterBySubtype(type: Type): UTypeStream<Type>

    /**
     * Excludes from this type stream types which are subtypes of [type].
     */
    fun filterByNotSupertype(type: Type): UTypeStream<Type>

    /**
     * Excludes from this type stream types which are supertypes of [type].
     */
    fun filterByNotSubtype(type: Type): UTypeStream<Type>

    // TODO: probably, we can consider it always terminates
    fun take(n: Int, result: MutableCollection<Type>): Boolean

    val isEmpty: Boolean
}

/**
 * An empty type stream.
 */
class UEmptyTypeStream<Type> : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> = this

    override fun filterBySubtype(type: Type): UTypeStream<Type> = this

    override fun filterByNotSupertype(type: Type): UTypeStream<Type> = this

    override fun filterByNotSubtype(type: Type): UTypeStream<Type> = this

    override fun take(n: Int, result: MutableCollection<Type>): Boolean =
        true

    override val isEmpty: Boolean
        get() = true
}

fun <Type> UTypeStream<Type>.take(n: Int): List<Type> {
    val result = mutableListOf<Type>()
    require(take(n, result))
    return result
}

fun <Type> UTypeStream<Type>.takeFirst(): Type = take(1).single()

/**
 * Consists of just one type [singleType].
 */
class USingleTypeStream<Type>(
    private val typeSystem: UTypeSystem<Type>,
    private val singleType: Type,
) : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> =
        if (!typeSystem.isSupertype(type, singleType)) {
            UEmptyTypeStream()
        } else {
            this
        }

    override fun filterBySubtype(type: Type): UTypeStream<Type> =
        if (!typeSystem.isSupertype(singleType, type)) {
            UEmptyTypeStream()
        } else {
            this
        }

    override fun filterByNotSupertype(type: Type): UTypeStream<Type> =
        if (typeSystem.isSupertype(type, singleType)) {
            UEmptyTypeStream()
        } else {
            this
        }

    override fun filterByNotSubtype(type: Type): UTypeStream<Type> =
        if (typeSystem.isSupertype(singleType, type)) {
            UEmptyTypeStream()
        } else {
            this
        }

    override fun take(n: Int, result: MutableCollection<Type>): Boolean {
        if (n > 0) {
            result += singleType
        }
        return true
    }

    override val isEmpty: Boolean
        get() = false
}
