package org.usvm.types

/**
 * A persistent type stream interface. Represents these type constraints:
 * * [filterBySupertype]
 * * [filterBySubtype]
 * * [filterByNotSubtype]
 * * [filterByNotSupertype]
 *
 * Also provides a way to collect them via [take].
 */
interface UTypeStream<Type> {
    fun filterBySupertype(type: Type): UTypeStream<Type>

    fun filterBySubtype(type: Type): UTypeStream<Type>

    fun filterByNotSupertype(type: Type): UTypeStream<Type>

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
