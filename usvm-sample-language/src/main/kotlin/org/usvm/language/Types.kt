package org.usvm.language

sealed interface SampleType

sealed interface PrimitiveType : SampleType

object IntType : PrimitiveType {
    override fun toString(): String = "Int"
}

object BooleanType : PrimitiveType {
    override fun toString(): String = "Boolean"
}

sealed interface RefType : SampleType

class StructType(
    val struct: Struct
) : RefType {
    override fun toString(): String = struct.name
}

val Null = Struct("null", emptySet())

class ArrayType<T : SampleType>(
    val elementType: T
) : RefType {
    override fun toString(): String = "[]$elementType"
}