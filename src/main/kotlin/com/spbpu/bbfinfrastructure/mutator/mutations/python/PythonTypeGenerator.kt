package com.spbpu.bbfinfrastructure.mutator.mutations.python

import com.spbpu.bbfinfrastructure.util.getTrue
import kotlin.random.Random

object PythonTypeGenerator {

    fun generateRandomType() =
        if (Random.getTrue(80)) {
            "str"
        } else {
            listOf(
                "int",
                "float",
                "bool",
                "double",
            ).random()
        }
}