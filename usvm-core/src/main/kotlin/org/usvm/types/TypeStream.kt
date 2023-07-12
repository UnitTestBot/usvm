package org.usvm.types

/**
 * A base interface representing persistent type constraints and a function to collect
 * **instantiable** types satisfying them.
 *
 * Consists of a conjunction of constraints of four kinds:
 *
 * 1. x <: T, i.e. object referenced in x inherits T (supertype constraints for x)
 * 2. T <: x, i.e. object referenced in x inherited by T (subtype constraints for x)
 * 3. x </: T, i.e. object referenced in x does not inherit T (notSupertype constraints for x)
 * 4. T </: x, i.e. object referenced in x is not inherited by T (notSubtype constraints for x)
 *
 * To collect **instantiable** types satisfying constraints use [take] function.
 */
interface UTypeStream<Type> {
    /**
     * Excludes from this type stream types which are not subtypes of [type].
     *
     * @return the updated type stream
     */
    fun filterBySupertype(type: Type): UTypeStream<Type>

    /**
     * Excludes from this type stream types which are not supertypes of [type].
     *
     * @return the updated type stream
     */
    fun filterBySubtype(type: Type): UTypeStream<Type>

    /**
     * Excludes from this type stream types which are subtypes of [type].
     *
     * @return the updated type stream
     */
    fun filterByNotSupertype(type: Type): UTypeStream<Type>

    /**
     * Excludes from this type stream types which are supertypes of [type].
     *
     * @return the updated type stream
     */
    fun filterByNotSubtype(type: Type): UTypeStream<Type>

    /**
     * @return the collection of **instantiable** types satisfying accumulated type constraints.
     */
    fun take(n: Int): Collection<Type>

    val isEmpty: Boolean
}

/**
 * An empty type stream.
 */
class UEmptyTypeStream<Type> private constructor() : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> = this

    override fun filterBySubtype(type: Type): UTypeStream<Type> = this

    override fun filterByNotSupertype(type: Type): UTypeStream<Type> = this

    override fun filterByNotSubtype(type: Type): UTypeStream<Type> = this

    override fun take(n: Int): Collection<Type> = emptyList()

    override val isEmpty: Boolean
        get() = true

    companion object {
        @Suppress("UNCHECKED_CAST")
        operator fun <Type> invoke() = instance as UEmptyTypeStream<Type>

        val instance = UEmptyTypeStream<Nothing>()
    }
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

    override fun take(n: Int) = listOf(singleType)

    override val isEmpty: Boolean
        get() = false
}
