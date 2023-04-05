package org.usvm.language

val readIntMethod = Method(
    "readInt",
    emptyList(),
    IntType,
    null
)

val readBoolMethod = Method(
    "readBool",
    emptyList(),
    BooleanType,
    null
)

val writeIntMethod = Method(
    "writeInt",
    listOf(IntType),
    null,
    null
)

val writeBoolMethod = Method(
    "writeBool",
    listOf(BooleanType),
    null,
    null
)
