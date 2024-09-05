package com.spbpu.bbfinfrastructure.test

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.JavaRandomTypeGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.TemplatesParser
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaConditionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaExpressionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaScopeCalculator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ScopeComponent
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.*
import org.jetbrains.kotlin.descriptors.runtime.structure.primitiveByWrapper
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import kotlin.random.Random

class TestTemplatesInserter : Transformation() {

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
        parsedTemplates: TemplatesParser.Template,
        randomTemplateBody: TemplatesParser.TemplateBody
    ): Boolean {
        repeat(1_000) {
            val fileBackupText = file.text
            try {
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
                tryToTransform(parsedTemplates, randomTemplateBody).ifTrue {
                    checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
                    return true
                }
//                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
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
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        var currentBlockLineNumber = randomPlaceToInsertLineNumber
        val scope = JavaScopeCalculator().calcVariablesAndFunctionsFromScope(randomPlaceToInsert)
        val mappedHoles = mutableMapOf<String, String>()
        val mappedTypes = mutableMapOf<String, String>()
        val usedExtensions = mutableListOf<String>()
        //TODO CAN WE REPLACE ~[BODY]~ by another template????
        val filledBlocks = randomTemplate.templateBody.split("~[BODY]~")
            .map { block ->
                val endOfBlock = file.getRandomPlaceToInsertNewLine(currentBlockLineNumber) ?: return false
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
        val replacementPsiBlock =
            if (filledBlocks.size == 1) {
                val newText = filledBlocks.first().first
                val newPsiBlock =
                    try {
                        Factory.javaPsiFactory.createCodeBlockFromText("{\n$newText\n}", null).also {
                            it.lBrace!!.delete()
                            it.rBrace!!.delete()
                        }
                    } catch (e: Throwable) {
                        return false
                    }
                randomPlaceToInsert.replaceThis(newPsiBlock)
                newPsiBlock
            } else {
                val newBody = filledBlocks.zipWithNext().joinToString("") {
                    val startLine = it.first.second
                    val endLine = it.second.second
                    val bodyFromFile =
                        file.getNodesBetweenWhitespaces(startLine, endLine)
                            .filter { it.children.isEmpty() }
                            .joinToString("") { it.text }
                    "${it.first.first}$bodyFromFile"
                } + filledBlocks.last().first + "\n"
                val newPsiBlock =
                    try {
                        Factory.javaPsiFactory.createCodeBlockFromText("{\n$newBody\n}", null).also {
                            it.lBrace!!.delete()
                            it.rBrace!!.delete()
                        }
                    } catch (e: Throwable) {
                        return false
                    }
                val start = filledBlocks.first().second
                val end = filledBlocks.last().second
                val nodesBetween = file.getNodesBetweenWhitespaces(start, end)
                val nodeToReplace =
                    nodesBetween
                        .filter { it.parent !in nodesBetween }
                        .mapIndexed { index, psiElement -> if (index > 0) psiElement.delete() else psiElement }
                        .first() as PsiElement
                nodeToReplace.replaceThis(newPsiBlock)
                newPsiBlock
            }
        insertClasses(parsedTemplate, (file as PsiJavaFile).packageName)
        insertImports(parsedTemplate)
        insertAuxMethods(replacementPsiBlock, parsedTemplate, randomTemplate).let { if (!it) return false }
        return checkNewCode()
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
        val javaExpressionGenerator = JavaExpressionGenerator()
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
            val literal = javaExpressionGenerator.genConstant(getTypeFromHole(hole, mappedTypes)!!)!!
            if (hole.contains("@")) {
                mappedHoles[hole] = literal
            }
            return literal
        }
        if (holeType == HOLE_TYPE.TYPE) {
            mappedTypes[hole]?.let { return it }
            val randomType = JavaRandomTypeGenerator.generateRandomType()
            if (hole.contains("@")) {
                mappedTypes[hole] = randomType
            }
            return randomType
        }
        val type = getTypeFromHole(hole, mappedTypes) ?: run {
            JavaRandomTypeGenerator.generateRandomType().also {
                mappedTypes[hole.substringAfter("_")] = it
            }
        }
        val capturedType = JavaTypeMappings.mappings[type] ?: type
        if (capturedType == "boolean" || capturedType == "java.lang.Boolean") {
            if (holeType == HOLE_TYPE.EXPR) {
                JavaConditionGenerator(scope).generate()?.let {
                    if (hole.contains("@")) {
                        mappedHoles[hole] = it
                    }
                    return it
                }
            }
        }
        val randomValueWithCompatibleType =
            if (Random.getTrue(20) ||  holeType == HOLE_TYPE.VAR) {
                if (capturedType == "java.lang.Object") {
                    scope.randomOrNull()?.name
                } else {
                    getValueOfTypeFromScope(scope, capturedType)
                }
            } else null
        if (holeType == HOLE_TYPE.VAR && randomValueWithCompatibleType == null) {
            throw IllegalArgumentException()
        }
        val resMapping =
            randomValueWithCompatibleType
                ?: javaExpressionGenerator.generateExpressionOfType(scope, capturedType)
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

    protected fun insertClasses(parsedTemplate: TemplatesParser.Template, originalPackageDirective: String) {
        for ((auxClassName, auxClassBody) in parsedTemplate.auxClasses) {
            if (project.files.any { it.name == "$auxClassName.java" }) {
                continue
            }
            val psiForClass =
                PSICreator.getPsiForJava(auxClassBody) as PsiJavaFile
            //Set package directive
            psiForClass.packageName = originalPackageDirective
            val bbfFile =
                BBFFile("$auxClassName.java", psiForClass)
            project.addFile(bbfFile)
        }
    }

    protected fun insertImports(parsedTemplate: TemplatesParser.Template) {
        if (parsedTemplate.imports.isNotEmpty()) {
            val oldImportList = (file as PsiJavaFile).importList?.text ?: ""
            val additionalImports = parsedTemplate.imports.joinToString("\n") { "import $it" }
            val newImportList =
                (PSICreator.getPsiForJava("$oldImportList\n$additionalImports") as PsiJavaFile).importList!!
            (file as PsiJavaFile).importList?.replaceThis(newImportList) ?: return
        }
    }

    protected fun insertAuxMethods(
        randomPlaceToInsert: PsiElement,
        randomTemplate: TemplatesParser.Template,
        templateBody: TemplatesParser.TemplateBody
    ): Boolean {
        val methodsToAdd = templateBody.auxMethodsNames
        val auxMethods =
            randomTemplate.auxMethods
                .filter { it.key in methodsToAdd }
        for ((_, auxMethod) in auxMethods) {
            val psiClass = randomPlaceToInsert.parents.find { it is PsiClass } as? PsiClassImpl ?: return false
            val m = Factory.javaPsiFactory.createMethodFromText(auxMethod.trim(), null)
            val lastMethod =
                psiClass.getAllChildrenOfCurLevel().findLast { it is PsiMethod && it.containingClass == psiClass }
                    ?: return false
            lastMethod.addAfterThisWithWhitespace(m, "\n\n")
        }
        return true
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