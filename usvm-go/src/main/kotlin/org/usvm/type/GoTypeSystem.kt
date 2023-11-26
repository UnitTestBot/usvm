package org.usvm.type

import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.MapType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SignatureType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.jacodb.go.api.TupleType
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class GoTypeSystem(
    override val typeOperationsTimeout: Duration,
    val types: Collection<GoType>
) : UTypeSystem<GoType> {
    private val goAnyType = InterfaceType(emptyList(), "any")
    private val topTypeStream by lazy { USupportTypeStream.from(this, goAnyType) }

    override fun topTypeStream(): UTypeStream<GoType> {
        return topTypeStream
    }

    override fun findSubtypes(type: GoType): Sequence<GoType> {
        return types.asSequence().filter { isSupertype(type, it) }
    }

    override fun isInstantiable(type: GoType): Boolean = when (type) {
        is StructType, is MapType, is SliceType, is ArrayType, is BasicType -> true
        is NamedType -> isInstantiable(type.underlyingType)
        is PointerType -> isInstantiable(type.baseType)
        else -> false
    }

    override fun isFinal(type: GoType): Boolean = when (type) {
        is BasicType, is ArrayType, is SliceType, is MapType, is PointerType, is SignatureType, is TupleType -> true
        else -> false
    }

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean = when (type) {
        is BasicType, is ArrayType, is SliceType, is MapType, is PointerType, is SignatureType, is TupleType -> types.isEmpty()
        is InterfaceType -> types.none { !isFinal(it) }
        is NamedType -> hasCommonSubtype(type.underlyingType, types)
        is StructType -> types.all { it is InterfaceType || isSupertype(it, type) }
        else -> false
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean = when {
        supertype == type -> true
        supertype is NamedType && supertype.underlyingType is InterfaceType -> isSupertype(supertype.underlyingType, type)
        supertype is InterfaceType -> implements(supertype, type)
        else -> false
    }

    private fun implements(iface: InterfaceType, impl: GoType): Boolean = when {
        iface.methods.isEmpty() -> true
        impl is NamedType -> impl.methods.containsAll(iface.methods)
        impl is PointerType -> implements(iface, impl.baseType)
        else -> false
    }
}