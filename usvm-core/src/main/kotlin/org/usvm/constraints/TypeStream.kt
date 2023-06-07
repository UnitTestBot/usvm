package org.usvm.constraints

import org.usvm.UTypeSystem

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

// TODO add common part with caching
