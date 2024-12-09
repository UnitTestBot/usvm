package org.usvm.type

import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.ChanType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.MapType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.NullType
import org.jacodb.go.api.OpaqueType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SignatureType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.jacodb.go.api.TupleType
import org.jacodb.go.api.UnionType
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
        is StructType, is MapType, is SliceType, is ArrayType -> true
        is NamedType -> isInstantiable(type.underlyingType)
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
        identical(supertype, type) -> true
        assignable(supertype, type) -> true
        else -> false
    }

    private fun identical(lhs: GoType, rhs: GoType): Boolean {
        if (lhs is NamedType != rhs is NamedType) {
            return false
        }

        @Suppress("KotlinConstantConditions")
        return when {
            lhs is ArrayType && rhs is ArrayType -> identical(lhs.elementType, rhs.elementType) && lhs.len == rhs.len
            lhs is BasicType && rhs is BasicType -> lhs.typeName == rhs.typeName
            lhs is ChanType && rhs is ChanType -> identical(lhs.elementType, rhs.elementType) && lhs.direction == rhs.direction
            lhs is InterfaceType && rhs is InterfaceType -> lhs.methods.sorted() == rhs.methods.sorted()
            lhs is MapType && rhs is MapType -> identical(lhs.keyType, rhs.keyType) && identical(lhs.valueType, rhs.valueType)
            lhs is NamedType && rhs is NamedType -> identical(lhs.underlyingType, rhs.underlyingType)
            lhs is OpaqueType && rhs is OpaqueType -> lhs.typeName == rhs.typeName
            lhs is PointerType && rhs is PointerType -> identical(lhs.baseType, rhs.baseType)
            lhs is SignatureType && rhs is SignatureType -> identical(lhs.params, rhs.params) && identical(lhs.results, rhs.results)
            lhs is SliceType && rhs is SliceType -> identical(lhs.elementType, rhs.elementType)
            lhs is StructType && rhs is StructType -> lhs.fields!!.zip(rhs.fields!!).all { identical(it.first, it.second) } && lhs.tags == rhs.tags
            lhs is TupleType && rhs is TupleType -> lhs.types.zip(rhs.types).all { identical(it.first, it.second) }
            lhs is UnionType && rhs is UnionType -> lhs.terms.zip(rhs.terms).all { identical(it.first, it.second) }
            else -> false
        }
    }

    private fun assignable(variable: GoType, value: GoType): Boolean {
        return when {
            identical(variable, value) -> true
            !(variable is NamedType && value is NamedType) && identical(variable.underlying(), value.underlying()) -> true
            variable is InterfaceType && implements(variable, value) -> true
            variable.underlying() is InterfaceType && implements(variable.underlying() as InterfaceType, value) -> true
            value is NullType && (variable is PointerType || variable is SignatureType || variable is SliceType || variable is MapType || variable is ChanType || variable is InterfaceType) -> true
            else -> false
        }
    }

    private fun implements(iface: InterfaceType, impl: GoType): Boolean = when {
        iface.methods.isEmpty() -> true
        impl is NamedType -> impl.methods.containsAll(iface.methods)
        else -> false
    }
}