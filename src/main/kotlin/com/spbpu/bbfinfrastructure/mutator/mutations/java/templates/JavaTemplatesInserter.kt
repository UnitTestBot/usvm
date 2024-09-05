package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiClassImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationLocation
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaConditionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaExpressionGenerator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaScopeCalculator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ScopeComponent
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.suite.GlobalTestSuite
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import com.spbpu.bbfinfrastructure.util.*
import com.spbpu.bbfinfrastructure.util.exceptions.MutationFinishedException
import com.spbpu.bbfinfrastructure.util.statistic.StatsManager
import org.jetbrains.kotlin.descriptors.runtime.structure.primitiveByWrapper
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random

open class JavaTemplatesInserter : BaseTemplatesInserter() {

    private val testSuite = GlobalTestSuite.javaTestSuite
    private val originalPsiText = file.text

    override fun transform() {
        repeat(1_000) {
            //Add all helpers
            insertClasses((file as PsiJavaFile).packageName)
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

    override fun tryToTransform(): Boolean {
        val (parsedTemplate, randomTemplate, pathAndIndex) =
            insertingObjectsTemplates.ifTrue { getRandomObjectTemplate() } ?: getRandomSensitivityTemplate()
            ?: return false
        val (pathToTemplateFile, randomTemplateIndex) = pathAndIndex
        val randomPlaceToInsert =
            project.configuration.mutationRegion?.run {
                when {
                    startLine != null && endLine != null -> file.getRandomPlaceToInsertNewLine(
                        startLine,
                        endLine,
                        !insertingObjectsTemplates
                    )

                    startLine != null -> file.getRandomPlaceToInsertNewLine(startLine, !insertingObjectsTemplates)
                    else -> file.getRandomPlaceToInsertNewLine(!insertingObjectsTemplates)
                }
            } ?: file.getRandomPlaceToInsertNewLine(!insertingObjectsTemplates)
        if (randomPlaceToInsert == null) return false
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        val scope = JavaScopeCalculator().calcVariablesAndFunctionsFromScope(randomPlaceToInsert)
        val usedExtensions = mutableListOf<String>()

        //TODO CAN WE REPLACE ~[BODY]~ by another template????
        val filledBlocks = fillBlocks(
            randomTemplate = randomTemplate,
            randomPlaceToInsert = randomPlaceToInsert,
            scope = scope,
            parsedTemplate = parsedTemplate,
            usedExtensions = usedExtensions
        ) ?: return false
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
                        throw IllegalArgumentException()
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
                        Factory.javaPsiFactory.createCodeBlockFromText("{\n${newBody.trim()}\n}", null).also {
                            it.lBrace!!.delete()
                            it.rBrace!!.delete()
                        }
                    } catch (e: Throwable) {
                        throw IllegalArgumentException()
                    }
                val start = filledBlocks.first().second
                val end = filledBlocks.last().second
                val nodesBetween = file.getNodesBetweenWhitespaces(start, end)
                if (nodesBetween.any { it.children.any { it !in nodesBetween } }) {
                    throw IllegalArgumentException()
                }
                val nodeToReplace =
                    nodesBetween
                        .filter { it.parent !in nodesBetween }
                        .mapIndexed { index, psiElement -> if (index > 0) psiElement.delete() else psiElement }
                        .first() as PsiElement
                nodeToReplace.replaceThis(newPsiBlock)
                newPsiBlock
            }
        insertImports(parsedTemplate)
        val numberOfAddedImports = parsedTemplate.imports.size
        insertAuxMethods(replacementPsiBlock, parsedTemplate, randomTemplate).let { if (!it) return false }
        return checkNewCode(
            MutationInfo(
                mutationName = "TemplateInsertion",
                isObjectTemplate = insertingObjectsTemplates,
                mutationDescription = "Insert template from $pathToTemplateFile with name ${randomTemplate.name}",
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



    private fun checkNewCode(mutationInfo: MutationInfo?): Boolean {
        if (!checker.checkCompiling()) {
            throw IllegalArgumentException()
        } else {
            mutationInfo?.let { currentMutationChain.add(it) }
            checker.curFile.changePsiFile(PSICreator.getPsiForJava(file.text))
            if (insertingObjectsTemplates && ++addedObjectsTemplates >= numToInsertObjectTemplates) {
                insertingObjectsTemplates = false
                return true
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
            return true
        }
    }


    override fun getReplacementForHole(
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
                ?: javaExpressionGenerator.generateExpressionOfType(scope, capturedType)
                ?: throw IllegalArgumentException()
        if (hole.contains("@")) {
            mappedHoles[hole] = resMapping
        }
        return resMapping
    }

    protected fun insertClasses(originalPackageDirective: String) {
        Files.walk(Paths.get(FuzzingConf.pathToTemplates))
            .map { it.toFile() }
            .filter { it.path.contains("helpers") && it.isFile }
            .toArray()
            .map { it as File }
            .toList()
            .flatMap {
                it.readText().split(Regex("~class .* start~\n"))
                    .filter { it.trim().isNotEmpty() }
                    .map { it.substringBefore("~class") }
            }
            .map {
                val psiForClass =
                    PSICreator.getPsiForJava(it) as PsiJavaFile
                psiForClass.packageName = originalPackageDirective
                val auxClassName = (psiForClass.getAllChildren().first { it is PsiClass } as PsiClass).name
                if (project.files.any { it.name == "$auxClassName.java" }) {
                    return@map
                }
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
}