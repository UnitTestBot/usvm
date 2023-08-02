package org.usvm.types

/**
 * A base interface, instantiated in target machines. Provides type information and [topTypeStream],
 * representing all possible types in the system.
 */
interface UTypeSystem<Type> {

    /**
     * @return true if [type] <: [supertype].
     */
    fun isSupertype(supertype: Type, type: Type): Boolean

    /**
     * @return true if [type] can be supertype for some type together with some incomparable type u.
     */
    fun isMultipleInheritanceAllowedFor(type: Type): Boolean

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
