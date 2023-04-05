package org.usvm.language

import kotlin.reflect.KProperty

data class Program(
    val name: String,
    val structs: List<Struct>,
    val methods: List<Method<SampleType?>>
)

data class Method<out R : SampleType?>(
    val name: String,
    val argumentsTypes: List<SampleType>,
    val returnType: R,
    val body: Body?,
)

data class Body(
    var registersCount: Int,
    val stmts: List<Stmt>
)

data class Struct(
    val name: String,
    val fields: Set<Field<SampleType>>
)

data class Field<out T : SampleType>(
    val name: String,
    val type: T,
)

