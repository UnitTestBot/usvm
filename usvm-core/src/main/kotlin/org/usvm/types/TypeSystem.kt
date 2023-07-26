package org.usvm.types

/**
 * A base interface, instantiated in target machines. Provides type information and [topTypeStream],
 * representing all possible types in the system.
 */
interface UTypeSystem<Type> {

    /**
     * @return true if t <: u.
     */
    fun isSupertype(u: Type, t: Type): Boolean

    /**
     * @return true if [t] can be supertype for some type together with some incomparable type u.
     */
    fun isMultipleInheritanceAllowedFor(t: Type): Boolean

    /**
     * @return true if there is no type u distinct from [t] and subtyping [t].
     */
    fun isFinal(t: Type): Boolean

    /**
     * @return true if [t] is instantiable, meaning it can be created via constructor.
     */
    fun isInstantiable(t: Type): Boolean

    /**
     * @return a sequence of **direct** inheritors of the [t].
     */
    fun findSubtypes(t: Type): Sequence<Type>


    /**
     * @return the top type stream, including all the types in the system.
     */
    fun topTypeStream(): UTypeStream<Type>
}
