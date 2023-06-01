package org.usvm.constraints

interface UTypeStream<Type> {
    fun filterBySupertype(type: Type): UTypeStream<Type>

    fun filterBySubtype(type: Type): UTypeStream<Type>

    fun filterByNotSupertype(type: Type): UTypeStream<Type>

    fun filterByNotSubtype(type: Type): UTypeStream<Type>

    fun take(n: Int, result: MutableCollection<Type>): Boolean

    val isEmpty: Boolean
}

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

// TODO add common part with caching
