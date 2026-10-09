package org.usvm.type

import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.MapType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class GoTypeSystem(
    override val typeOperationsTimeout: Duration,
    val types: Collection<GoType>,
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
        is StructType, is MapType, is SliceType, is ArrayType, is BasicType -> {
            true
        }
        is NamedType -> {
            isInstantiable(type.underlyingType)
        }
        is PointerType -> {
            // A pointer value is valid even when its pointee is an interface or a recursive pointer type.
            true
        }
        else -> {
            false
        }
    }

    override fun isFinal(type: GoType): Boolean = when (type) {
        is InterfaceType -> false
        is NamedType -> type.underlying() !is InterfaceType
        else -> true
    }

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        if (isFinal(type)) return types.all { isSupertype(it, type) }

        return findSubtypes(type).any { candidate ->
            isInstantiable(candidate) && types.all { isSupertype(it, candidate) }
        }
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean = when {
        supertype == type -> {
            true
        }
        supertype is NamedType && supertype.underlyingType is InterfaceType -> {
            isSupertype(
                supertype.underlyingType,
                type
            )
        }
        supertype is InterfaceType -> {
            implements(supertype, type)
        }
        else -> {
            false
        }
    }

    private fun implements(iface: InterfaceType, impl: GoType): Boolean = when {
        iface.methods.isEmpty() -> {
            true
        }
        impl is NamedType -> {
            impl.methods.containsAll(iface.methods)
        }
        impl is PointerType -> {
            impl.baseType.underlying() !is InterfaceType && implements(iface, impl.baseType)
        }
        else -> {
            false
        }
    }
}
