package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

object TemplatesParser {

    fun parse(pathToTemplate: String): Template {
        val templateTextWithoutComments =
            File(pathToTemplate).readText().removeComments()
        val extensions = parseExtensions(templateTextWithoutComments)
        val mainBodyPattern = Pattern.compile("~main body start~(.*?)~main body end~", Pattern.DOTALL)
        val mainBodyMatcher = mainBodyPattern.matcher(templateTextWithoutComments)
        if (mainBodyMatcher.find()) {
            val mainBody = mainBodyMatcher.group(1)
            val auxMethods = parseAuxMethods(mainBody)
            val auxClasses = parseAuxClasses(mainBody)
            val imports = parseImports(mainBody)
            val templateBodies = parseTemplateBodies(mainBody)
            return Template(
                auxClasses = auxClasses,
                imports = imports,
                templates = templateBodies,
                auxMethods = auxMethods,
                extensions = extensions
            )
        }
        error("Cant build template for $pathToTemplate file")
    }

    private fun parseTemplateBodies(mainBodyText: String): List<TemplateBody> {
        val templatesPattern = Pattern.compile("~template (.*?) start~(.*?)~template(.*?)end~", Pattern.DOTALL)
        val templatesMatcher = templatesPattern.matcher(mainBodyText)
        val templateBodies = mutableListOf<TemplateBody>()
        while(templatesMatcher.find()) {
            val templateName = templatesMatcher.group(1)
            val templateBody = templatesMatcher.group(2)
            val templateAuxMethods =
                templateBody.split("\n")
                    .filter { it.startsWith("~helper function") }
                    .map { it.substringAfter("~helper function").substringBefore("~").trim() }
            val finalTemplateBody = templateBody
                .split("\n")
                .filterNot { it.startsWith("~helper function") }
                .joinToString("\n").trim()
            templateBodies.add(TemplateBody(templateName, finalTemplateBody, templateAuxMethods))
        }
        return templateBodies
    }
    private fun parseImports(mainBodyText: String): List<String> =
        mainBodyText.split("\n")
            .filter { it.startsWith("~import") }
            .map { it.substringAfter("~import ").substringBefore("~").trim() }

    private fun parseAuxClasses(mainBodyText: String): Map<String, String> {
        val auxClasses = mutableMapOf<String, String>()
        val helperClassImportPattern = Pattern.compile("~helper import (.*?)~", Pattern.DOTALL)
        val helperClassImportMatcher = helperClassImportPattern.matcher(mainBodyText)
        while (helperClassImportMatcher.find()) {
            val classPath = helperClassImportMatcher.group(1)
            val classText = findAuxFileSource(classPath).readText().removeComments()
            val classPattern = Pattern.compile("~class (.*?) start~(.*?)~class(.*?)end~", Pattern.DOTALL)
            val classMatcher = classPattern.matcher(classText)
            if (classMatcher.find()) {
                val className = classMatcher.group(1)
                val classBody = classMatcher.group(2).trim()
                auxClasses[className] = classBody
            } else {
                error("Can't parce class $classPath")
            }
        }
        return auxClasses
    }


    private fun parseAuxMethods(mainBodyText: String): Map<String, String> {
        val helperFunctionsPattern =
            Pattern.compile("~helper functions start~(.*?)~helper functions end~", Pattern.DOTALL)
        val helperFunctionsMatcher = helperFunctionsPattern.matcher(mainBodyText)
        val auxMethods = mutableMapOf<String, String>()
        if (helperFunctionsMatcher.find()) {
            val functions = helperFunctionsMatcher.group(1)
            val functionPattern = Pattern.compile("~function (.*?) start~(.*?)~function(.*?)end~", Pattern.DOTALL)
            val matcher = functionPattern.matcher(functions)
            while (matcher.find()) {
                val functionName = matcher.group(1)
                val functionBody = matcher.group(2).trim()
                auxMethods[functionName] = functionBody
            }
        }
        return auxMethods
    }

    private fun parseExtensions(templateTextWithoutComments: String): Map<String, List<String>> {
        val extensionPattern = Pattern.compile("~extensions start~(.*?)~extensions end~", Pattern.DOTALL)
        val extensionMatcher = extensionPattern.matcher(templateTextWithoutComments)
        val extensions = mutableMapOf<String, MutableList<String>>()
        if (extensionMatcher.find()) {
            extensionMatcher.group(1)
                .split("\n")
                .map { it.trim() }
                .filterNot { it.isEmpty() }
                .forEach { line ->
                    when {
                        line.startsWith("~extensions import ") -> {
                            val import =
                                line.substringAfter("~extensions import ")
                                    .substringBefore("~")
                                    .trim()
                            if (import == "all") {
                                Files.walk(Paths.get("templates/extensions"))
                                    .toList()
                                    .map { it.toFile() }
                                    .filter { it.isFile }
                                    .forEach { parseExtension(it, extensions) }
                            } else {
                                parseExtension(findAuxFileSource(import), extensions)
                            }
                        }

                        else -> parseExtension(line, extensions)
                    }
                }
        }
        return extensions
    }

    private fun parseExtension(file: File, extensions: MutableMap<String, MutableList<String>>) {
        file
            .readText()
            .removeComments()
            .split("\n")
            .map { it.trim() }
            .filterNot { it.isEmpty() }
            .forEach { parseExtension(it, extensions) }
    }

    private fun parseExtension(line: String, extensions: MutableMap<String, MutableList<String>>) {
        line.split("->").map { it.trim() }.let {
            val key = it[0].substringAfter("~[").substringBefore("]~")
            extensions.getOrPut(key) { mutableListOf() }.add(it[1])
        }
    }

    private fun String.removeComments() = this
            .split("\n")
            .filterNot { it.startsWith("#") }
            .joinToString("\n")

    private fun findAuxFileSource(path: String): File {
        val f = File(path)
        return if (!f.exists()) {
            File("templates/$path").let {
                if (!it.exists()) {
                    if (path.contains(".tmt")) {
                        error("cant find file with aux file $path")
                    }
                    else {
                        findAuxFileSource("$path.tmt")
                    }
                }
                else  {
                    it
                }
            }
        } else {
            f
        }
    }

    class Template(
        val auxClasses: Map<String, String>,
        val auxMethods: Map<String, String>,
        val imports: List<String>,
        val extensions: Map<String, List<String>>,
        val templates: List<TemplateBody>
    )

    data class TemplateBody(
        val name: String,
        val templateBody: String,
        val auxMethodsNames: List<String>
    )


}