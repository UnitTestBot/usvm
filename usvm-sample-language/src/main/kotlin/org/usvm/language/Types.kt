package org.usvm.language

sealed interface SampleType
sealed interface PrimitiveType : SampleType

object IntType : PrimitiveType {
    override fun toString(): String {
        return "Int"
    }
}
object BooleanType : PrimitiveType {
    override fun toString(): String {
        return "Boolean"
    }
}

sealed interface RefType : SampleType

class StructType(
    val struct: Struct
) : RefType {
    override fun toString(): String {
        return struct.name
    }
}

val Null = Struct("null", emptySet())

class ArrayType<T : SampleType>(
    val elementType: T
) : RefType {
    override fun toString(): String {
        return "$elementType[]"
    }
}