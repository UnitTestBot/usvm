package org.usvm.machine

import io.ksmt.expr.KExpr
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
import org.usvm.UContext
import org.usvm.USort

class JcContext(
    val cp: JcClasspath,
    components: JcComponents,
) : UContext(components) {

    val voidValue by lazy { mkConcreteHeapRef(0) }
    fun mkVoidValue(): KExpr<out USort> = voidValue

    fun typeToSort(type: JcType) = when (type) {
        is JcRefType -> addressSort
        cp.float -> fp32Sort
        cp.double -> fp64Sort
        cp.long -> bv64Sort
        cp.int, cp.char, cp.byte, cp.short, cp.boolean -> bv32Sort
        else -> error("Unknown type: $type")
    }

}