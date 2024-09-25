package com.spbpu.bbfinfrastructure.test

import com.intellij.psi.*
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyStatementList
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.TemplatesParser
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.PythonConditionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.PythonExpressionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.PythonScopeCalculator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ScopeComponent
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.mutator.mutations.python.PythonTypeGenerator
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.PythonPSIFactory
import com.spbpu.bbfinfrastructure.util.*
import org.jetbrains.kotlin.descriptors.runtime.structure.primitiveByWrapper
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random

class PythonTestTemplatesInserter : Transformation() {

    private val originalPsiText = file.text
    private var addedImports = 0

    fun testTransform(templatePath: String, templateBodyIndex: Int): Boolean {
        val parsedTemplates = TemplatesParser.parse(templatePath)
        for ((ind, randomTemplateBody) in parsedTemplates.templates.withIndex()) {
            if (ind != templateBodyIndex) continue
            tryToAddTemplate(parsedTemplates, randomTemplateBody).ifFalse {
                return false
            }
        }
        return true
    }

    private fun tryToAddTemplate(
        parsedTemplate: TemplatesParser.Template,
        randomTemplateBody: TemplatesParser.TemplateBody
    ): Boolean {
        repeat(25) {
            try {
                checker.curFile.changePsiFile(PSICreator.getPsiForPython(originalPsiText)!!)
                insertClassesAndImports(parsedTemplate)
                tryToTransform(parsedTemplate, randomTemplateBody).ifTrue {
                    checker.curFile.changePsiFile(PSICreator.getPsiForPython(originalPsiText)!!)
                    println("OK")
                    return true
                }
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForPython(originalPsiText)!!)
            }
        }
        return false
    }

    private fun tryToTransform(
        parsedTemplate: TemplatesParser.Template,
        randomTemplate: TemplatesParser.TemplateBody
    ): Boolean {
        val randomPlaceToInsert = file.getRandomPlaceToInsertNewLine()
        if (randomPlaceToInsert == null) return false
        val scope = PythonScopeCalculator()
            .calcVariablesAndFunctionsFromScope(randomPlaceToInsert)
            .filter { it.name != "app" }
        val usedExtensions = mutableListOf<String>()
        val filledBlocks = fillBlocks(randomTemplate, randomPlaceToInsert, scope, parsedTemplate, usedExtensions, true)
            ?: throw IllegalArgumentException()
        if (filledBlocks.size == 1) {
            val newText = filledBlocks.first().first
            val offset = randomPlaceToInsert.text.count { it == ' ' }
            val newBl =
                StringBuilder().apply {
                    repeat(offset) {
                        append(' ')
                    }
                    appendLine("absolutely_random_int_222 = 1")
                    newText.trimStart().split("\n").forEach {
                        repeat(offset) {
                            append(' ')
                        }
                        appendLine(it)
                    }
                }.toString()
            val newBlockPsi = (PythonPSIFactory.createPythonStatementList(newBl) as PyStatementList)
            randomPlaceToInsert.addBeforeThisSafe(newBlockPsi)
            file.getAllPSIChildrenOfType<PyAssignmentStatement>()
                .first { it.text == "absolutely_random_int_222 = 1" }
                .delete()
            newBlockPsi
        } else {
            TODO("~[BODY]~ currently not supported")
        }
        insertAuxMethods(parsedTemplate, randomTemplate).ifFalse { throw IllegalStateException() }
        val mutatedFileUri = "${FuzzingConf.pathToBenchmarkToFuzz}/${project.configuration.originalUri!!}"
        val dirToSaveHelpers =
            "${FuzzingConf.pathToBenchmarkToFuzz}/${project.configuration.originalUri!!}".substringBeforeLast('/')
        try {
            if (PSICreator.getPsiForPython(file.text)!!.getAllChildren().any { it is PsiErrorElement }) {
                throw IllegalArgumentException()
            }
            File(mutatedFileUri).writeText(file.text)
            project.files.getAllWithout(0).forEach { bbfFile ->
                with(File("$dirToSaveHelpers/${bbfFile.name}")) {
                    if (!exists()) {
                        writeText(bbfFile.psiFile.text)
                    }
                }
            }
            if (!checker.checkCompiling(project)) throw IllegalStateException()
            checker.curFile.changePsiFile(PSICreator.getPsiForPython(file.text)!!)
            return true
        } finally {
            File(mutatedFileUri).writeText(originalPsiText)
//            project.files.getAllWithout(0).forEach { bbfFile ->
//                File("$dirToSaveHelpers/${bbfFile.name}").delete()
//            }
        }
    }

    protected fun insertClassesAndImports(parsedTemplate: TemplatesParser.Template) {
        val importsFromHelpersToAdd = Files.walk(Paths.get(FuzzingConf.pathToTemplates))
            .map { it.toFile() }
            .filter { it.path.contains("helpers") && it.isFile }
            .toArray()
            .map { it as File }
            .toList()
            .map {
                val helperClasses = it.readText()
                    .split(Regex("~class .* start~\n"))
                    .filter { it.trim().isNotEmpty() }
                    .joinToString("\n\n\n") { it.substringBefore("~class").trim() }
                val auxClassName = it.nameWithoutExtension
                val psiForClass = PSICreator.getPsiForPython(helperClasses)!!
                val bbfFile = BBFFile("$auxClassName.py", psiForClass)
                project.addFile(bbfFile)
                it.nameWithoutExtension
            }
        val importsFromTemplate =
            parsedTemplate.imports.joinToString("\n", postfix = "\n") {
                if (it.startsWith("from")) {
                    it
                } else {
                    "import $it"
                }
            }
        addedImports += importsFromHelpersToAdd.size
        addedImports += parsedTemplate.imports.size
        val importBlockFromHelpers =
            if (importsFromHelpersToAdd.isEmpty()) {
                ""
            } else {
                importsFromHelpersToAdd.joinToString(separator = "\n", postfix = "\n") { "from $it import *" }
            }
        val newPyFile = importBlockFromHelpers + importsFromTemplate + file.text
        project.files.first().changePsiFile(PSICreator.getPsiForPython(newPyFile)!!)
    }

    protected fun insertAuxMethods(
        randomTemplate: TemplatesParser.Template,
        templateBody: TemplatesParser.TemplateBody
    ): Boolean {
        val methodsToAdd = templateBody.auxMethodsNames.ifEmpty { return true }
        val auxMethods =
            randomTemplate.auxMethods
                .filter { it.key in methodsToAdd }.values
        val fileTextWithAuxMethods = "${file.text}\n\n${auxMethods.joinToString("\n\n")}"
        val newPsiFile = PSICreator.getPsiForPython(fileTextWithAuxMethods)!!
        if (newPsiFile.getAllChildren().any { it is PsiErrorElement }) {
            return false
        } else {
            checker.curFile.changePsiFile(newPsiFile)
        }
        return true
    }

    private fun fillBlocks(
        randomTemplate: TemplatesParser.TemplateBody,
        randomPlaceToInsert: PsiElement,
        scope: List<ScopeComponent>,
        parsedTemplate: TemplatesParser.Template,
        usedExtensions: MutableList<String>,
        isPython: Boolean = false
    ): List<Pair<String, Int>>? {
        val mappedHoles = mutableMapOf<String, String>()
        val mappedTypes = mutableMapOf<String, String>()
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        var currentBlockLineNumber = randomPlaceToInsertLineNumber
        return randomTemplate.templateBody.split("~[BODY]~")
            .map { block ->
                val endOfBlock = file.getRandomPlaceToInsertNewLine(currentBlockLineNumber, false)
                    ?: return null
                var fillIteration = 0
                var filledTemplate =
                    fillTemplateBody(
                        mappedTypes = mappedTypes,
                        mappedHoles = mappedHoles,
                        scope = scope,
                        randomTemplateBody = block,
                        parsedTemplate = parsedTemplate,
                        iteration = fillIteration++,
                        usedExtensions = usedExtensions
                    )
                while (filledTemplate.contains("~[")) {
                    filledTemplate = fillTemplateBody(
                        mappedTypes = mappedTypes,
                        mappedHoles = mappedHoles,
                        scope = scope,
                        randomTemplateBody = filledTemplate,
                        parsedTemplate = parsedTemplate,
                        iteration = fillIteration++,
                        usedExtensions = usedExtensions
                    )
                }
                filledTemplate to currentBlockLineNumber.also {
                    currentBlockLineNumber = endOfBlock.getLocationLineNumber()
                }
            }
    }


    protected fun checkNewCode(): Boolean {
        return if (!checker.checkCompiling()) {
            throw IllegalArgumentException()
        } else {
            true
        }
    }

    private fun fillTemplateBody(
        mappedTypes: MutableMap<String, String>,
        mappedHoles: MutableMap<String, String>,
        scope: List<ScopeComponent>,
        randomTemplateBody: String,
        parsedTemplate: TemplatesParser.Template,
        iteration: Int,
        usedExtensions: MutableList<String>
    ): String {
        val stack = mutableListOf<Int>() // Стек для хранения индексов открывающих скобок
        var newText = randomTemplateBody
        var i = 0
        while (i < newText.length) {
            if (newText.startsWith("~[", i)) {
                stack.add(i)
            }
            if (newText.startsWith("]~", i)) {
                val startIndex = stack.removeLast()
                val token = newText.substring(startIndex + 2, i)
                val replacement = getReplacementForHole(
                    hole = token,
                    mappedTypes = mappedTypes,
                    mappedHoles = mappedHoles,
                    scope = scope,
                    randomTemplateBody = randomTemplateBody,
                    parsedTemplate = parsedTemplate,
                    iteration = iteration,
                    usedExtensions = usedExtensions
                )
                newText = newText.replaceRange(startIndex, i + 2, replacement)
                val diff = token.length - replacement.length
                i = i - diff - 3
            }
            i++
        }

        return newText
    }

    private fun getReplacementForHole(
        hole: String,
        mappedTypes: MutableMap<String, String>,
        mappedHoles: MutableMap<String, String>,
        scope: List<ScopeComponent>,
        randomTemplateBody: String,
        parsedTemplate: TemplatesParser.Template,
        iteration: Int,
        usedExtensions: MutableList<String>
    ): String {
        val expressionGenerator = PythonExpressionGenerator()
        if (hole.contains("@")) {
            mappedHoles[hole]?.let { return it }
        }
        val holeType =
            when {
                hole.startsWith("TYPE") -> HOLE_TYPE.TYPE
                hole.startsWith("VAR_") -> HOLE_TYPE.VAR
                hole.startsWith("CONST_") -> HOLE_TYPE.CONST
                hole.startsWith("EXPR_") -> HOLE_TYPE.EXPR
                else -> HOLE_TYPE.MACRO
            }
        if (holeType == HOLE_TYPE.MACRO) {
            val replacement = checkFromExtensionsAndMacros(parsedTemplate, hole.substringBefore("@"))
            if (hole.contains("@") && replacement != null) {
                mappedHoles[hole] = replacement
            }
            return replacement?.also { usedExtensions.add("$hole -> $it") }
                ?: error("Can't find replacement for hole $hole")
        }
        if (Random.getTrue(50) && iteration < 3) {
            checkFromExtensionsAndMacros(parsedTemplate, hole)?.let {
                usedExtensions.add("$hole -> $it")
                return it
            }
        }
        if (holeType == HOLE_TYPE.CONST) {
            val literal = expressionGenerator.gen(scope, getTypeFromHole(hole, mappedTypes)!!)
                ?: expressionGenerator.genConstant()!!
            if (hole.contains("@")) {
                mappedHoles[hole] = literal
            }
            return literal
        }
        if (holeType == HOLE_TYPE.TYPE) {
            mappedTypes[hole]?.let { return it }
            val randomType = PythonTypeGenerator.generateRandomType()
            if (hole.contains("@")) {
                mappedTypes[hole] = randomType
            }
            return randomType
        }
        val type = getTypeFromHole(hole, mappedTypes) ?: run {
            PythonTypeGenerator.generateRandomType().also {
                mappedTypes[hole.substringAfter("_")] = it
            }
        }
        if (type == "bool") {
            //Generate condition from extensions
            if (holeType == HOLE_TYPE.EXPR) {
                PythonConditionGenerator().generate()?.let {
                    if (hole.contains("@")) {
                        mappedHoles[hole] = it
                    }
                    return it
                }
            }
        }
        val randomValueWithCompatibleType =
            if (Random.getTrue(20) || holeType == HOLE_TYPE.VAR) {
                scope.filter { it.type == type }.randomOrNull()?.name ?: scope.filter { it.type == "Any" }
                    .randomOrNull()?.name
            } else null
        if (holeType == HOLE_TYPE.VAR && randomValueWithCompatibleType == null) {
            throw IllegalArgumentException()
        }
        val resMapping =
            randomValueWithCompatibleType
                ?: expressionGenerator.generateExpressionOfType(scope, type)
                ?: throw IllegalArgumentException()
        if (hole.contains("@")) {
            mappedHoles[hole] = resMapping
        }
        return resMapping
    }

    private fun checkFromExtensionsAndMacros(template: TemplatesParser.Template, hole: String): String? {
        val extensions = template.extensions[hole] ?: listOf()
        val macros = template.macros[hole] ?: listOf()
        return (extensions + macros).randomOrNull()
    }

    private fun getTypeFromHole(hole: String, mappedTypes: Map<String, String>) =
        if (hole.contains("TYPE")) {
            mappedTypes[hole.substringAfter("_")]
        } else {
            hole.substringAfter("_").substringBefore("@")
        }

    private fun getValueOfTypeFromScope(scope: List<ScopeComponent>, type: String): String? {
        val jType =
            try {
                this::class.java.classLoader.loadClass(type)
            } catch (e: Throwable) {
                null
            }
        return jType?.let { jTypeNotNull ->
            scope.filter { it.type == jTypeNotNull.name || it.type == jTypeNotNull.primitiveByWrapper?.name }
                .randomOrNull()?.name
        } ?: scope.filter { it.type == type }.randomOrNull()?.name
    }


    private enum class HOLE_TYPE {
        VAR, EXPR, TYPE, CONST, MACRO
    }

    override fun transform() {
        TODO("Not yet implemented")
    }
}