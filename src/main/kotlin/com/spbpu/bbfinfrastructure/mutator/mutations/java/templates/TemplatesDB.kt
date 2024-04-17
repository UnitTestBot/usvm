package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object TemplatesDB {

    val manualTemplates = mutableListOf<String>()
    val minedTemplates = mutableListOf<String>()
    val kotlinTemplates = mutableListOf<String>()
    val testTemplates = mutableListOf<String>()


    fun getTemplatesForFeature(feature: TestingFeature): List<String>? {
        val templates = getTemplates(feature.dir) ?: return null
        return templates.map { it.readText() }
    }

    fun getRandomTemplateForFeature(feature: TestingFeature): Pair<String, String>? {
        val templates = getTemplates(feature.dir) ?: return null
        return templates.randomOrNull()?.let { it.readText() to it.path }
    }

    private fun getTemplates(dir: String) =
        Files.walk(Paths.get(dir))
            .map { it.toFile() }
            .filter { it.isFile && it.extension == "tmt" }
            .toList().ifEmpty { null }


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