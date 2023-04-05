package org.usvm.concrete.interpreter

import org.usvm.UContext
import org.usvm.language.ArrayType
import org.usvm.language.BooleanType
import org.usvm.language.IntType
import org.usvm.language.SampleType
import org.usvm.language.StructType

fun UContext.typeToSort(type: SampleType) = when (type) {
    BooleanType -> boolSort
    IntType -> bv32Sort
    is ArrayType<*> -> addressSort
    is StructType -> addressSort
}