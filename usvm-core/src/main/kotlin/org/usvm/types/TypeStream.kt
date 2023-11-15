package org.usvm.types

import org.usvm.types.TypesResult.EmptyTypesResult
import org.usvm.types.TypesResult.SuccessfulTypesResult
import org.usvm.types.TypesResult.TypesResultWithExpiredTimeout

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
     * @return a [TypesResult] on the collection of **instantiable** types satisfying accumulated type constraints,
     * according to the [UTypeSystem.typeOperationsTimeout]:
     * * If there are no types satisfying constraints, returns [EmptyTypesResult];
     * * If there are some types found and [UTypeSystem.typeOperationsTimeout] was not expired,
     * returns [SuccessfulTypesResult];
     * * If the [UTypeSystem.typeOperationsTimeout] was expired, returns [TypesResultWithExpiredTimeout] containing
     * all types satisfying constraints that were collected before timeout expiration.
     */
    fun take(n: Int): TypesResult<Type>

    /**
     * @return whether this [UTypeStream] is empty according to [UTypeStream.take],
     * or null if [UTypeSystem.typeOperationsTimeout] was expired.
     */
    val isEmpty: Boolean?

    /**
     * Stores a supertype that satisfies current type constraints and other satisfying types are inheritors of this type.
     */
    val commonSuperType: Type?
}

sealed interface TypesResult<out Type> {
    object EmptyTypesResult : TypesResult<Nothing>, Collection<Nothing> by emptyList()

    class SuccessfulTypesResult<Type>(
        val types: Collection<Type>
    ) : TypesResult<Type>, Collection<Type> by types

    class TypesResultWithExpiredTimeout<Type>(
        val collectedTypes: Collection<Type>
    ) : TypesResult<Type>

    companion object {
        fun <Type> Collection<Type>.toTypesResult(wasTimeoutExpired: Boolean): TypesResult<Type> =
            if (wasTimeoutExpired) TypesResultWithExpiredTimeout(this) else SuccessfulTypesResult(this)
    }
}

/**
 * An empty type stream.
 */
object UEmptyTypeStream : UTypeStream<Nothing> {
    override fun filterBySupertype(type: Nothing): UTypeStream<Nothing> = this

    override fun filterBySubtype(type: Nothing): UTypeStream<Nothing> = this

    override fun filterByNotSupertype(type: Nothing): UTypeStream<Nothing> = this

    override fun filterByNotSubtype(type: Nothing): UTypeStream<Nothing> = this

    override fun take(n: Int): EmptyTypesResult = EmptyTypesResult

    override val isEmpty: Boolean
        get() = true

    override val commonSuperType: Nothing?
        get() = null
}

@Suppress("UNCHECKED_CAST")
fun <Type> emptyTypeStream(): UTypeStream<Type> = UEmptyTypeStream as UTypeStream<Type>

fun <Type> UTypeStream<Type>.first(): Type = take(1).let {
    when (it) {
        EmptyTypesResult -> throw NoSuchElementException("Collection is empty.")
        is SuccessfulTypesResult -> it.first()
        is TypesResultWithExpiredTimeout -> it.collectedTypes.first()
    }
}

fun <Type> UTypeStream<Type>.firstOrNull(): Type? = take(1).let {
    when (it) {
        EmptyTypesResult -> null
        is SuccessfulTypesResult -> it.firstOrNull()
        is TypesResultWithExpiredTimeout -> it.collectedTypes.firstOrNull()
    }
}

// Note: we try to take at least two types to ensure that we don't have no more than one type.
fun <Type> UTypeStream<Type>.single(): Type = take(2).let {
    when (it) {
        EmptyTypesResult -> throw NoSuchElementException("Collection is empty.")
        is SuccessfulTypesResult -> it.single()
        is TypesResultWithExpiredTimeout ->
            error(
                "$this type stream has unknown number of types because of timeout exceeding, " +
                        "already found types: ${it.collectedTypes}"
            )
    }
}

fun <Type> UTypeStream<Type>.singleOrNull(): Type? = take(2).let {
    when (it) {
        EmptyTypesResult -> null
        is SuccessfulTypesResult -> it.singleOrNull()
        is TypesResultWithExpiredTimeout -> null // Unknown number of types
    }
}

/**
 * Consists of just one type [singleType].
 */
class USingleTypeStream<Type>(
    private val typeSystem: UTypeSystem<Type>,
    private val singleType: Type,
) : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> =
        if (!typeSystem.isSupertype(type, singleType)) {
            emptyTypeStream()
        } else {
            this
        }

    override fun filterBySubtype(type: Type): UTypeStream<Type> =
        if (!typeSystem.isSupertype(singleType, type)) {
            emptyTypeStream()
        } else {
            this
        }

    override fun filterByNotSupertype(type: Type): UTypeStream<Type> =
        if (typeSystem.isSupertype(type, singleType)) {
            emptyTypeStream()
        } else {
            this
        }

    override fun filterByNotSubtype(type: Type): UTypeStream<Type> =
        if (typeSystem.isSupertype(singleType, type)) {
            emptyTypeStream()
        } else {
            this
        }

    override fun take(n: Int): SuccessfulTypesResult<Type> = SuccessfulTypesResult(listOf(singleType))

    override val isEmpty: Boolean
        get() = false

    override val commonSuperType: Type
        get() = singleType
}
