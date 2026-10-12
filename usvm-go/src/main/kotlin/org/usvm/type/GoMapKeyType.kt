package org.usvm.type

import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.StructType
import org.jacodb.go.api.GoType

/** Named numeric and boolean keys use their scalar payload, not their box identity. */
internal fun GoType.mapKeyStorageType(): GoType {
    val underlying = underlying()
    val isScalar = underlying is BasicType && underlying != GoBasicTypes.STRING

    return if (isScalar) underlying else this
}

/** These keys need Go value equality instead of SMT scalar or heap reference equality. */
internal fun GoType.needsMapKeyRepresentative(): Boolean = when (val type = underlying()) {
    is ArrayType, is StructType, is InterfaceType, is PointerType -> true
    else -> type == GoBasicTypes.STRING || type == GoBasicTypes.FLOAT32 || type == GoBasicTypes.FLOAT64
}
