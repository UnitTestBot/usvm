package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationLocation
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ConditionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ExpressionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaScopeCalculator
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.suite.GlobalJavaTestSuite
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import com.spbpu.bbfinfrastructure.util.*
import com.spbpu.bbfinfrastructure.util.exceptions.MutationFinishedException
import com.spbpu.bbfinfrastructure.util.statistic.StatsManager
import org.jetbrains.kotlin.descriptors.runtime.structure.primitiveByWrapper
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import kotlin.random.Random

open class TemplatesInserter : Transformation() {

    private val testSuite = GlobalJavaTestSuite.javaTestSuite
    private val originalPsiText = file.text
    private var addedProjects = 0
    private val numberOfProjectsToCheck = FuzzingConf.numberOfMutantsPerFile
    private val currentMutationChain = mutableListOf<MutationInfo>()
    private val numToInsertObjectTemplates = Random.nextInt(1, FuzzingConf.maxNumOfObjectsTemplates + 1)
    private val numToInsertSensitivityTemplates = Random.nextInt(1, FuzzingConf.maxNumOfSensitivityTemplates + 1)
    private var insertingObjectsTemplates = true
    private var addedObjectsTemplates = 0
    private var addedSensitivityTemplates = 0


    override fun transform() {
        repeat(1_000) {
            val fileBackupText = file.text
            println("TRY $it")
            try {
                tryToTransform()
            } catch (e: MutationFinishedException) {
                return
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
            }
        }
    }

    private fun getRandomObjectTemplate(): Triple<TemplatesParser.Template, TemplatesParser.TemplateBody, Pair<String, Int>>? {
        val randomTemplateFile = TemplatesDB.getRandomObjectTemplate() ?: error("Cant find any template")
        val parsedTemplate = TemplatesParser.parse(randomTemplateFile.path)
        val (randomTemplate, randomTemplateIndex) = parsedTemplate.templates.randomOrNullWithIndex() ?: return null
        return Triple(parsedTemplate, randomTemplate, randomTemplateFile.path to randomTemplateIndex)
    }

    private fun getRandomSensitivityTemplate(): Triple<TemplatesParser.Template, TemplatesParser.TemplateBody, Pair<String, Int>>? {
        return if (FuzzingConf.badTemplatesOnlyMode) {
            StatsManager.currentBadTemplatesList.randomOrNull()?.let {
                val index = it.second.substringAfter(' ').toInt()
                val body = it.first.templates.get(index)
                Triple(it.first, body, it.second.substringBefore(' ') to index)
            }
        } else {
            val randomTemplateFile = TemplatesDB.getRandomSensitivityTemplate() ?: error("Cant find any template")
            val parsedTemplate = TemplatesParser.parse(randomTemplateFile.path)
            val (randomTemplate, randomTemplateIndex) = parsedTemplate.templates.randomOrNullWithIndex() ?: return null
            Triple(parsedTemplate, randomTemplate, randomTemplateFile.path to randomTemplateIndex)
        }
    }


    protected open fun tryToTransform(): Boolean {
        val (parsedTemplate, randomTemplate, pathAndIndex) =
            insertingObjectsTemplates.ifTrue { getRandomObjectTemplate() } ?: getRandomSensitivityTemplate()
            ?: return false
        val (pathToTemplateFile, randomTemplateIndex) = pathAndIndex
        val randomPlaceToInsert =
            project.configuration.mutationRegion?.run {
                when {
                    startLine != null && endLine != null -> file.getRandomPlaceToInsertNewLine(startLine, endLine)
                    startLine != null -> file.getRandomPlaceToInsertNewLine(startLine)
                    else -> file.getRandomPlaceToInsertNewLine()
                }
            } ?: file.getRandomPlaceToInsertNewLine()
        if (randomPlaceToInsert == null) return false
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        var currentBlockLineNumber = randomPlaceToInsertLineNumber
        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
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
        val numberOfAddedImports = parsedTemplate.imports.size
        insertAuxMethods(replacementPsiBlock, parsedTemplate, randomTemplate).let { if (!it) return false }
        return checkNewCode(
            MutationInfo(
                mutationName = "TemplateInsertion",
                mutationDescription = "Insert template from $pathToTemplateFile with index $randomTemplateIndex",
                usedExtensions = usedExtensions,
                location = MutationLocation(file.name, randomPlaceToInsertLineNumber)
            )
        ).ifTrue {
            project.configuration.mutatedRegion?.run {
                project.configuration.mutatedRegion = ToolsResultsSarifBuilder.ResultRegion(
                    endColumn = endColumn,
                    startColumn = startColumn,
                    startLine = startLine!! + numberOfAddedImports,
                    endLine = endLine!! + numberOfAddedImports + replacementPsiBlock.text.count { it == '\n' } - 2,
                )
            }
            StatsManager.saveMutationHistory(pathToTemplateFile, randomTemplateIndex)
            if (addedProjects >= numberOfProjectsToCheck) {
                throw MutationFinishedException()
            }
            true
        } ?: false
    }

    protected fun checkNewCode(mutationInfo: MutationInfo?): Boolean {
        return if (!checker.checkCompiling()) {
            throw IllegalArgumentException()
        } else {
            mutationInfo?.let { currentMutationChain.add(it) }
            checker.curFile.changePsiFile(PSICreator.getPsiForJava(file.text))
            if (++addedObjectsTemplates >= numToInsertObjectTemplates) {
                insertingObjectsTemplates = false
            }
            if (!insertingObjectsTemplates && ++addedSensitivityTemplates >= numToInsertSensitivityTemplates) {
                addedSensitivityTemplates = 0
                addedObjectsTemplates = 0
                insertingObjectsTemplates = true
                addedProjects++
                testSuite.addProject(project.copy(), currentMutationChain.toList())
                currentMutationChain.clear()
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(originalPsiText))
            }
            true
        }
    }


    private fun fillTemplateBody(
        mappedTypes: MutableMap<String, String>,
        mappedHoles: MutableMap<String, String>,
        scope: List<JavaScopeCalculator.JavaScopeComponent>,
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
        scope: List<JavaScopeCalculator.JavaScopeComponent>,
        randomTemplateBody: String,
        parsedTemplate: TemplatesParser.Template,
        iteration: Int,
        usedExtensions: MutableList<String>
    ): String {
        val expressionGenerator = ExpressionGenerator()
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
            val replacement = checkFromExtensionsAndMacros(parsedTemplate, hole)
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
            val literal = expressionGenerator.genConstant(getTypeFromHole(hole, mappedTypes)!!)!!
            if (hole.contains("@")) {
                mappedHoles[hole] = literal
            }
            return literal
        }
        if (holeType == HOLE_TYPE.TYPE) {
            mappedTypes[hole]?.let { return it }
            val randomType = RandomTypeGenerator.generateRandomType()
            if (hole.contains("@")) {
                mappedTypes[hole] = randomType
            }
            return randomType
        }
        val type = getTypeFromHole(hole, mappedTypes) ?: run {
            RandomTypeGenerator.generateRandomType().also {
                mappedTypes[hole.substringAfter("_")] = it
            }
        }
        val capturedType = JavaTypeMappings.mappings[type] ?: type
        if (capturedType == "boolean" || capturedType == "java.lang.Boolean") {
            if (holeType == HOLE_TYPE.EXPR) {
                ConditionGenerator(scope).generate()?.let {
                    if (hole.contains("@")) {
                        mappedHoles[hole] = it
                    }
                    return it
                }
            }
        }
        val randomValueWithCompatibleType =
            if (Random.getTrue(20) || holeType == HOLE_TYPE.VAR) {
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
                ?: expressionGenerator.generateExpressionOfType(scope, capturedType)
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
            val m = Factory.javaPsiFactory.createMethodFromText(auxMethod, null)
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

    private fun getValueOfTypeFromScope(scope: List<JavaScopeCalculator.JavaScopeComponent>, type: String): String? {
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

//    class Template(
//        val auxClasses: List<Pair<String, String>>,
//        val imports: List<String>,
//        val templates: List<TemplateBody>
//    )
//
//    data class TemplateBody(
//        val auxMethods: List<String>,
//        val templateBody: String
//    )

    private enum class HOLE_TYPE {
        VAR, EXPR, TYPE, CONST, MACRO
    }
}