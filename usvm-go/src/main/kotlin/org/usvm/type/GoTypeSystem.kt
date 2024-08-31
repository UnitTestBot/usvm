package org.usvm.type

import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.MapType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class GoTypeSystem(
    override val typeOperationsTimeout: Duration
) : UTypeSystem<GoType> {
    private val goAnyType = InterfaceType()
    private val topTypeStream by lazy { USupportTypeStream.from(this, goAnyType) }

    override fun topTypeStream(): UTypeStream<GoType> {
        return topTypeStream
    }

    override fun findSubtypes(type: GoType): Sequence<GoType> {
        // TODO proper computations
        return emptySequence()
    }

    override fun isInstantiable(type: GoType): Boolean = when(type) {
        is StructType, is MapType, is SliceType, is ArrayType -> true
        is NamedType -> isInstantiable(type.underlyingType)
        else -> false
    }

    override fun isFinal(type: GoType): Boolean {
        return type !is InterfaceType
    }

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        // TODO proper computations
        return true
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean {
        // TODO proper computations
        return true
    }
}