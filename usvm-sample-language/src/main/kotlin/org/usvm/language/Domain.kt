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
)

class Body(
    var registersCount: Int,
    val stmts: List<Stmt>
)

class Struct(
    val name: String,
    val fields: Set<Field<SampleType>>
)

class Field<out T : SampleType>(
    val name: String,
    val type: T,
)

