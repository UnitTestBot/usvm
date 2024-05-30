package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList


object TemplatesDB {

    fun getTemplatesForFeature(feature: TestingFeature): List<String>? {
        val templates = getTemplates(feature.dir)?.toList() ?: return null
        return templates.map { it.readText() }
    }

    fun getRandomTemplateForFeature(feature: TestingFeature): Pair<String, String>? {
        val templates = getTemplates(feature.dir) ?: return null
        return templates.randomOrNull()?.let { it.readText() to it.path }
    }

    fun getRandomTemplateForPath(path: String): Pair<String, String>? {
        val templates = getTemplates(path) ?: return null
        return templates.randomOrNull()?.let { it.readText() to it.path }
    }

    private fun getTemplates(dir: String): List<File>? =
        Files.walk(Paths.get(dir))
            .map { it.toFile() }
            .filter { it.isFile && it.extension == "tmt" }
            .toList()
            .ifEmpty { null }

}