package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

object RandomTypeGenerator {

    fun generateRandomType() =
        listOf( "String",
            "Integer" ,
            "Double",
            "Float",
            "Boolean",
            "Character",
            "Byte",
            "Short",
            "Long"
            ).random()
}