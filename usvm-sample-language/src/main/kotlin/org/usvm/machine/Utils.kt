package org.usvm.machine

import org.usvm.UContextBv32Size
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.language.ArrayType
import org.usvm.language.BooleanType
import org.usvm.language.IntType
import org.usvm.language.SampleType
import org.usvm.language.StructType
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx

fun UContextBv32Size.typeToSort(type: SampleType) = when (type) {
    BooleanType -> boolSort
    IntType -> bv32Sort
    is ArrayType<*> -> addressSort
    is StructType -> addressSort
}

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}
