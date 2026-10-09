package org.usvm.state

import io.ksmt.utils.asExpr
import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.StructType
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.readField
import org.usvm.memory.allocateGoArrayInitialized
import org.usvm.memory.arrayView
import org.usvm.memory.readGoArrayIndex
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.type.underlying

internal fun GoState.copyValue(
    value: UExpr<out USort>,
    type: GoType,
    source: GoState = this,
): UExpr<out USort> = with(ctx) {
    val underlying = type.underlying()
    if (underlying !is StructType && underlying !is ArrayType) return value

    val reference = value.asExpr(addressSort)
    if (type is NamedType) {
        return box(copyValue(source.unbox(reference, addressSort), underlying, source), type)
    }

    return when (underlying) {
        is StructType -> {
            val fields = underlying.fields.orEmpty().mapIndexed { index, fieldType ->
                copyValue(source.memory.readField(reference, index, typeToSort(fieldType)), fieldType, source)
            }
            mkTuple(type, fields = fields.toTypedArray())
        }
        is ArrayType -> {
            val view = source.arrayView(reference, type)
            val elementType = underlying.elementType
            val sort = typeToSort(elementType)
            val fields = (0 until underlying.len.toInt()).asSequence().map { index ->
                val element = source.memory.readGoArrayIndex(
                    view.backing,
                    mkSizeAddExpr(view.offset, mkSizeExpr(index)),
                    view.storageType,
                    sort,
                )
                copyValue(element, elementType, source).asExpr(sort)
            }
            memory.allocateGoArrayInitialized(type, sort, sizeSort, fields)
        }
        else -> {
            value
        }
    }
}
