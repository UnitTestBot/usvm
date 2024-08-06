package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.spbpu.bbfinfrastructure.util.FuzzingConf
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList


object TemplatesDB {

    fun getRandomObjectTemplate(): File? =
        availableTemplates
            .filter { it.path.contains("objects") }
            .randomOrNull()

    fun getRandomSensitivityTemplate(): File? =
        availableTemplates
            .filter { it.path.contains("sensitivity") }
            .randomOrNull()

    private val availableTemplates =
        Files.walk(Paths.get(FuzzingConf.dirToTemplates))
            .map { it.toFile() }
            .filter { it.isFile && it.extension == "tmt" }
            .filter { !it.path.contains("helpers") && !it.path.contains("extensions") }
            .toList()
            .ifEmpty { error("I can't fuzz without templates, sorry") }

}