package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.spbpu.bbfinfrastructure.util.getTrue
import kotlin.random.Random

object JavaRandomTypeGenerator {

    fun generateRandomType() =
        if (Random.getTrue(80)) {
            "String"
        } else {
            listOf(
                "String",
                "Integer",
                "Double",
                "Float",
                "Boolean",
                "Character",
                "Byte",
                "Short",
                "Long"
            ).random()
        }
}