package org.usvm.language

class Program(
    val name: String,
    val structs: List<Struct>,
    val methods: List<Method<SampleType?>>
)

class Method<out R : SampleType?>(
    val name: String,
    val argumentsTypes: List<SampleType>,
    val returnType: R,
    val body: Body?,
) {
    override fun toString(): String {
        return "$name(${argumentsTypes.joinToString()}): $returnType"
    }
}

class Body(
    var registersCount: Int,
    val stmts: List<Stmt>
)

data class Struct(
    val name: String,
    val fields: Set<Field<SampleType>>
) {
    override fun toString() = name
}

data class Field<out T : SampleType>(
    val name: String,
    val type: T,
) {
    override fun toString() = name
}

