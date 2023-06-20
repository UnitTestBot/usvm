package org.usvm.machine

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.short
import org.jacodb.api.ext.void
import org.usvm.UContext

class JcContext(
    val cp: JcClasspath,
    components: JcComponents,
) : UContext(components) {
    val voidSort get() = addressSort

    val longSort get() = bv64Sort
    val integerSort get() = bv32Sort
    val shortSort get() = bv16Sort
    val charSort get() = bv16Sort
    val byteSort get() = bv8Sort
    val booleanSort get() = boolSort

    val floatSort get() = fp32Sort
    val doubleSort get() = fp64Sort

    val voidValue by lazy { JcVoidValue(this) }

    fun mkVoidValue(): JcVoidValue = voidValue

    fun typeToSort(type: JcType) = when (type) {
        is JcRefType -> addressSort
        cp.void -> voidSort
        cp.long -> longSort
        cp.int -> integerSort
        cp.short -> shortSort
        cp.char -> charSort
        cp.byte -> byteSort
        cp.boolean -> booleanSort
        cp.float -> floatSort
        cp.double -> doubleSort
        else -> error("Unknown type: $type")
    }
}