package org.usvm.type

import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoType

/** Named numeric and boolean keys use their scalar payload, not their box identity. */
internal fun GoType.mapKeyStorageType(): GoType {
    val underlying = underlying()
    val isScalar = underlying is BasicType && underlying != GoBasicTypes.STRING

    return if (isScalar) underlying else this
}
