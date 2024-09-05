package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.spbpu.bbfinfrastructure.util.FuzzingConf
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.io.File

class PythonConditionGenerator() {
    fun generate(): String? {
        val f = File("${FuzzingConf.pathToTemplates}/extensions/conditions.tmt")
        f.exists().ifTrue {
            f.readLines()
                .filter { it.trim().isNotEmpty() }
                .randomOrNull()?.let { return it.substringAfter(" -> ") }
        } ?: return null
    }
}