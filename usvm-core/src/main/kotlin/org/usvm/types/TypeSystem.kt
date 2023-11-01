package org.usvm.types

import kotlin.time.Duration

/**
 * A base interface, instantiated in target machines. Provides type information and [topTypeStream],
 * representing all possible types in the system.
 */
interface UTypeSystem<Type> {
    /**
     * A timeout used for heavy operations with types.
     */
    // TODO this timeout must not exceed time budget for the MUT
    val typeOperationsTimeout: Duration

    /**
     * @return true if [type] <: [supertype].
     */
    fun isSupertype(supertype: Type, type: Type): Boolean

    /**
     * @return true if [types] and [type] can be supertypes for some type together.
     * It is guaranteed that [type] is not a supertype for any type from [types]
     * and that [types] have common subtype.
     */
    fun hasCommonSubtype(type: Type, types: Collection<Type>): Boolean

    /**
     * @return true if there is no type u distinct from [type] and subtyping [type].
     */
    fun isFinal(type: Type): Boolean

    /**
     * @return true if [type] is instantiable, meaning it can be created via constructor.
     */
    fun isInstantiable(type: Type): Boolean

    /**
     * @return a sequence of **direct** inheritors of the [type].
     */
    fun findSubtypes(type: Type): Sequence<Type>


    /**
     * @return the top type stream, including all the types in the system.
     */
    fun topTypeStream(): UTypeStream<Type>
}
