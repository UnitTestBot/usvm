package org.usvm

interface UTypeSystem<Type> {

    /**
     * Returns true if t <: u.
     */
    fun isSupertype(u: Type, t: Type): Boolean

    /**
     * Returns true if [t] can be supertype for some type together with some incomparable type u.
     */
    fun isMultipleInheritanceAllowedFor(t: Type): Boolean

    /**
     * Returns true if there is no type u distinct from [t] and subtyping [t].
     */
    fun isFinal(t: Type): Boolean
}
