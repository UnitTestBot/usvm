package org.usvm

import org.jacodb.api.JcClassType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.ext.allSuperHierarchy
import org.usvm.constraints.UTypeStream

class JcTypeStream private constructor(
    val typeSystem: JcTypeSystem,
    private val cached: MutableList<JcType>,
) : UTypeStream<JcType> {

    constructor(jcTypeSystem: JcTypeSystem) : this(jcTypeSystem, mutableListOf())

    override fun filterBySupertype(type: JcType): UTypeStream<JcType> {
        require(type is JcRefType)

        TODO("Not yet implemented")
    }

    override fun filterBySubtype(type: JcType): UTypeStream<JcType> {
        TODO("Not yet implemented")
    }

    override fun filterByNotSupertype(type: JcType): UTypeStream<JcType> {
        TODO("Not yet implemented")
    }

    override fun filterByNotSubtype(type: JcType): UTypeStream<JcType> {
        TODO("Not yet implemented")
    }

    override fun take(n: Int, result: MutableCollection<JcType>): Boolean {
        if (n <= cached.size) {
            for (type in cached) {
                result += type
            }
            return true
        }
        TODO()

    }

    override val isEmpty: Boolean
        get() = if (cached.isNotEmpty()) {
            false
        } else {
            take(1, cached)
            cached.isNotEmpty()
        }
}