package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.spbpu.bbfinfrastructure.util.FuzzingConf
import com.spbpu.bbfinfrastructure.util.getTrue
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.io.File
import kotlin.random.Random

object GoTypeGenerator {

    fun generateRandomType() =
        if (Random.getTrue(80)) {
            "string"
        } else {
            listOf(
                "int",
                "float",
                "bool",
                "double",
            ).random()
        }
}

object GoConditionGenerator {
    fun generate(): String? {
        val f = File("${FuzzingConf.pathToTemplates}/extensions/conditions.tmt")
        f.exists().ifTrue {
            f.readLines()
                .filter { it.trim().isNotEmpty() }
                .randomOrNull()?.let { return it.substringAfter(" -> ") }
        } ?: return null
    }
}