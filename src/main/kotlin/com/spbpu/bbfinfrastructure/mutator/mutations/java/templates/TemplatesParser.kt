package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.spbpu.bbfinfrastructure.template.parser.TemplateLexer
import com.spbpu.bbfinfrastructure.template.parser.TemplateParser
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ConsoleErrorListener
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object TemplatesParser {

    fun parse(pathToTemplate: String): Template {
        val templateText =
            File(pathToTemplate).readText()
        val charStream = CharStreams.fromString(templateText)
        val lexer = TemplateLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = TemplateParser(tokens)
        parser.addErrorListener(ConsoleErrorListener())
        val templateFile = parser.templateFile()
        val (extensions, macros) = parseExtensions(templateFile)
        val mainClass = templateFile.mainClass()
        with(mainClass) {
            val auxMethods = helperFunctions()?.let { parseAuxMethods(it) } ?: mapOf()
            val auxClasses = helperImport()?.flatMap { helperImport ->
                val helperName = helperImport.IDENTIFIER().text
                createParser(findAuxFileSource(helperName))
                    .helperFile()
                    .helperClass()
                    .map { it.helperClassStart().IDENTIFIER().text to it.code().text }
            }?.toMap() ?: mapOf()
            val languageImports =
                languageImports()?.code()?.text
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.map {
                        if (it.trim().startsWith("import")) {
                            it.substringAfter("import ")
                        } else {
                            it
                        }
                    } ?: listOf()
            val bodies = template().map { templateBody ->
                TemplateBody(
                    templateBody.templateStart().IDENTIFIER().text,
                    templateBody.code().text,
                    auxMethods.map { it.key }
                )
            }
            val t = Template(
                auxClasses = auxClasses,
                auxMethods = auxMethods,
                imports = languageImports,
                extensions = extensions,
                macros = macros,
                templates = bodies
            )
            return Template(
                auxClasses = auxClasses,
                auxMethods = auxMethods,
                imports = languageImports,
                extensions = extensions,
                macros = macros,
                templates = bodies
            )
        }
    }

    private fun createParser(templateText: String): TemplateParser {
        val charStream = CharStreams.fromString(templateText)
        val lexer = TemplateLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        return TemplateParser(tokens)
    }

    private fun createParser(templateFile: File) = createParser(templateFile.readText())


    private fun parseAuxMethods(auxMethodsContext: TemplateParser.HelperFunctionsContext): Map<String, String> {
        val auxMethods = mutableMapOf<String, String>()
        auxMethodsContext.helperFunction()?.forEach { helperFunction ->
            val name = helperFunction.helperFunctionStart().IDENTIFIER().text
            val code = helperFunction.code().text
            auxMethods[name] = code
        }
        return auxMethods
    }

    private fun parseExtensions(templateFile: TemplateParser.TemplateFileContext): Pair<Map<String, List<String>>, Map<String, List<String>>> {
        val extensionsBlock = templateFile.extensionsBlock() ?: return mapOf<String, List<String>>() to mapOf()
        val extensions = mutableMapOf<String, MutableList<String>>()
        val macros = mutableMapOf<String, MutableList<String>>()
        with(extensionsBlock) {
            extensionImports()?.extensionImport()?.forEach { extensionImport ->
                val importIdentifier = extensionImport.IDENTIFIER().text.trim()
                if (importIdentifier == "*") {
                    Files.walk(Paths.get("${FuzzingConf.pathToTemplates}/extensions"))
                        .toList()
                        .map { it.toFile() }
                        .filter { it.isFile }
                        .forEach { parseExtensionFile(it, extensions, macros) }
                } else {
                    parseExtensionFile(findAuxFileSource(importIdentifier), extensions, macros)
                }
            }
            extensions()?.macroDefinition()?.forEach { addMacros(it, macros) }
            extensions()?.extensionDefinition()?.forEach { addExtension(it, macros) }
        }

        return extensions to macros
    }

    private fun parseExtensionFile(
        file: File,
        extensions: MutableMap<String, MutableList<String>>,
        macros: MutableMap<String, MutableList<String>>
    ) {
        val parser = createParser(file)
        with(parser.extensions()) {
            macroDefinition()?.forEach { addMacros(it, macros) }
            extensionDefinition()?.forEach { addExtension(it, extensions) }
        }
    }

    private fun addMacros(
        macro: TemplateParser.MacroDefinitionContext,
        macros: MutableMap<String, MutableList<String>>
    ) {
        val definition = "MACRO" + macro.macroArrow().IDENTIFIER().text
        val value = macro.codeString().text.trim()
        macros.getOrPut(definition) { mutableListOf() }.add(value)
    }

    private fun addExtension(
        extensionDefinition: TemplateParser.ExtensionDefinitionContext,
        extensions: MutableMap<String, MutableList<String>>
    ) {
        val definition = extensionDefinition.holeArrow().holeBody().text
        val value = extensionDefinition.codeString().text.trim()
        extensions.getOrPut(definition) { mutableListOf() }.add(value)
    }

    private fun findAuxFileSource(path: String): File =
        File("${FuzzingConf.pathToTemplates}/$path.tmt")


    class Template(
        val auxClasses: Map<String, String>,
        val auxMethods: Map<String, String>,
        val imports: List<String>,
        val extensions: Map<String, List<String>>,
        val macros: Map<String, List<String>>,
        val templates: List<TemplateBody>
    )

    data class TemplateBody(
        val name: String,
        val templateBody: String,
        val auxMethodsNames: List<String>
    )


}