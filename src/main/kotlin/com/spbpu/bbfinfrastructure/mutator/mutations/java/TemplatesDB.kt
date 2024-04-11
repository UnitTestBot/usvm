package com.spbpu.bbfinfrastructure.mutator.mutations.java

import java.io.File

object TemplatesDB {

    private val featureToTemplates = mutableMapOf<String, String>(
        "CONSTRUCTORS" to "templates/constructors",
        "PATH_SENSITIVITY" to "templates/pathSensitivity",
    )


    val manualTemplates = mutableListOf<String>()
    val minedTemplates = mutableListOf<String>()
    val kotlinTemplates = mutableListOf<String>()
    val testTemplates = mutableListOf<String>()


    fun getTemplatesForFeature(featureName: String): List<String>? {
        val dirToTemplates = featureToTemplates[featureName] ?: return null
        return File(dirToTemplates).listFiles()?.map { it.readText() }
    }

    init {
        File("manualTemplates.txt").readText().split("---------").forEach {
            manualTemplates.add(it)
        }
        File("templates.txt").readText().split("---------").forEach {
            minedTemplates.add(it)
        }
        File("kotlinTemplates.txt").readText().split("---------").forEach {
            kotlinTemplates.add(it)
        }
        File("testTemplates.txt").readText().split("---------").forEach {
            testTemplates.add(it)
        }
    }
}