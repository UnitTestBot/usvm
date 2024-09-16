package com.spbpu.bbfinfrastructure.mutator.mutations.go

import com.goide.psi.impl.GoElementFactory
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStatementList
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
import com.spbpu.bbfinfrastructure.mutator.mutations.MutationLocation
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.BaseTemplatesInserter
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.TemplatesParser
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.*
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.suite.GlobalTestSuite
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.psicreator.util.PythonPSIFactory
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import com.spbpu.bbfinfrastructure.util.*
import com.spbpu.bbfinfrastructure.util.exceptions.MutationFinishedException
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random


class GoTemplatesInserter : BaseTemplatesInserter() {

    private val originalPsiText = file.text

    private var addedLines = 0
    private var addedImportsLines = 0
    val mockProject = file.project
    private val testSuite = GlobalTestSuite.goTestSuite

    override fun transform() {
        repeat(1_000) {
            insertingObjectsTemplates = false
            val backupPsiText = file.text
            println("TRY $it addedProject = $addedProjects")
            try {
                tryToTransform().ifFalse {
                    checker.curFile.changePsiFile(PSICreator.getPsiForGo(backupPsiText)!!)
                }
            } catch (e: MutationFinishedException) {
                return
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForGo(backupPsiText)!!)
            }
        }
    }

    override fun tryToTransform(): Boolean {
        val (parsedTemplate, randomTemplate, pathAndIndex) =
            insertingObjectsTemplates.ifTrue { getRandomObjectTemplate() } ?: getRandomSensitivityTemplate()
            ?: return false
        val (pathToTemplateFile, _) = pathAndIndex
        val randomPlaceToInsert =
            project.configuration.mutationRegion?.run {
                when {
                    startLine != null -> getRandomPlaceToInsert(startLine + addedImportsLines)
                    else -> getRandomPlaceToInsert(null)
                }
            } ?: getRandomPlaceToInsert(null) ?: return false
        val scope = GoScopeCalculator().calcVariablesAndFunctionsFromScope(randomPlaceToInsert)
        val usedExtensions = mutableListOf<String>()
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        val filledBlocks = fillBlocks(randomTemplate, randomPlaceToInsert, scope, parsedTemplate, usedExtensions, true)
            ?: throw IllegalArgumentException()
        val replacementPsiBlock =
            if (filledBlocks.size == 1) {
                val newText = filledBlocks.first().first
                val newPsiBlock =
                    try {
                        GoElementFactory.createBlock(mockProject, "\n$newText\n").also {
                            it.lbrace.delete()
                            it.rbrace?.delete()
                        }
                    } catch (e: Throwable) {
                        throw IllegalArgumentException()
                    }
                randomPlaceToInsert.replaceThis(newPsiBlock)
                newPsiBlock
            } else {
                TODO("~[BODY]~ currently not supported")
            }
        return checkNewCode(
            mutationInfo = MutationInfo(
                mutationName = "TemplateInsertion",
                isObjectTemplate = insertingObjectsTemplates,
                mutationDescription = "Insert template from $pathToTemplateFile with name ${randomTemplate.name}",
                usedExtensions = usedExtensions,
                location = MutationLocation(file.name, randomPlaceToInsertLineNumber)
            ),
            parsedTemplate = parsedTemplate
        )
    }

    private fun checkNewCode(mutationInfo: MutationInfo, parsedTemplate: TemplatesParser.Template): Boolean {
        if (!checker.checkCompiling()) {
            throw IllegalArgumentException()
        } else {
            currentMutationChain.add(mutationInfo)
            checker.curFile.changePsiFile(PSICreator.getPsiForGo(file.text)!!)
            if (insertingObjectsTemplates && ++addedObjectsTemplates >= numToInsertObjectTemplates) {
                insertingObjectsTemplates = false
                return true
            }
            if (!insertingObjectsTemplates && ++addedSensitivityTemplates >= numToInsertSensitivityTemplates) {
                val projectToAdd = project.copy()
                projectToAdd.configuration.mutationRegion?.run {
                    projectToAdd.configuration.mutatedRegion = ToolsResultsSarifBuilder.ResultRegion(
                        endColumn = endColumn,
                        startColumn = startColumn,
                        startLine = startLine?.let { it + addedLines + addedImportsLines + parsedTemplate.imports.size },
                        endLine = endLine?.let { it + addedLines + addedImportsLines + parsedTemplate.imports.size },
                    )
                }
                addedSensitivityTemplates = 0
                addedObjectsTemplates = 0
                addedLines = 0
                addedProjects++
                testSuite.addProject(projectToAdd, currentMutationChain.toList())
                currentMutationChain.clear()
                checker.curFile.changePsiFile(PSICreator.getPsiForPython(originalPsiText)!!)
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
        val expressionGenerator = GoExpressionGenerator()
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
        if (Random.getTrue(75) && iteration < 2) {
            checkFromExtensionsAndMacros(parsedTemplate, hole)?.let {
                usedExtensions.add("$hole -> $it")
                return it
            }
        }
        if (holeType == HOLE_TYPE.CONST) {
            val literal = expressionGenerator.genVariable(scope, getTypeFromHole(hole, mappedTypes)!!)
                ?: throw IllegalStateException()
            if (hole.contains("@")) {
                mappedHoles[hole] = literal
            }
            return literal
        }
        if (holeType == HOLE_TYPE.TYPE) {
            mappedTypes[hole]?.let { return it }
            val randomType = GoTypeGenerator.generateRandomType()
            if (hole.contains("@")) {
                mappedTypes[hole] = randomType
            }
            return randomType
        }
        val type = getTypeFromHole(hole, mappedTypes) ?: run {
            GoTypeGenerator.generateRandomType().also {
                mappedTypes[hole.substringAfter("_")] = it
            }
        }
        if (type == "bool") {
            //Generate condition from extensions
            if (holeType == HOLE_TYPE.EXPR) {
                GoConditionGenerator.generate()?.let {
                    if (hole.contains("@")) {
                        mappedHoles[hole] = it
                    }
                    return it
                }
            }
        }
        val randomValueWithCompatibleType =
            if (Random.getTrue(20) || holeType == HOLE_TYPE.VAR) {
                if (Random.getTrue(20)) {
                    scope.filter { it.type == "any" }.randomOrNull()?.name
                } else {
                    scope.filter { it.type == type }.randomOrNull()?.name ?: scope.filter { it.type == "any" }
                        .randomOrNull()?.name
                }
            } else null
        if (holeType == HOLE_TYPE.VAR && randomValueWithCompatibleType == null) {
            throw IllegalArgumentException()
        }
        val resMapping =
            randomValueWithCompatibleType
                ?: expressionGenerator.genVariable(scope, type)
                ?: throw IllegalArgumentException()
        if (hole.contains("@")) {
            mappedHoles[hole] = resMapping
        }
        return resMapping
    }

    private fun getRandomPlaceToInsert(startLine: Int?): PsiWhiteSpace? {
        if (startLine == null) {
            return file.getAllPSIChildrenOfType<PsiWhiteSpace>()
                .filter { it.text.contains("\n") }
                .randomOrNull()
        }
        val parentMethod =
            file.getAllChildren().first { it.getLocationLineNumber() == startLine }.getAllParentsWithoutThis().toList()
                .firstOrNull { it is PyFunction }
        if (parentMethod == null || insertingObjectsTemplates) {
            return file.getAllPSIChildrenOfType<PsiWhiteSpace>()
                .filter { it.text.contains("\n") }
                .filter { it.getLocationLineNumber() <= startLine - 1 }
                .randomOrNull()
        }
        val fromLine = parentMethod.getAllChildren().minOf { it.getLocationLineNumber() }
        return file.getAllPSIChildrenOfType<PsiWhiteSpace>()
            .filter { it.text.contains("\n") }
            .filter { it.getLocationLineNumber().let { it in fromLine..startLine } }
            .randomOrNull()
    }

    private fun insertHelperClasses() {
        //TODO make smth with imports
        val importsToAdd = Files.walk(Paths.get(FuzzingConf.pathToTemplates))
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
        if (addedImportsLines == 0) {
            addedImportsLines += importsToAdd.size
        }
        val newImportBlock =
            if (importsToAdd.isEmpty()) {
                ""
            } else {
                importsToAdd.joinToString(separator = "\n", postfix = "\n") { "from .$it import *" }
            }
        val newPyFile = newImportBlock + file.text
        project.files.first().changePsiFile(PSICreator.getPsiForPython(newPyFile)!!)
    }

    private fun insertAuxMethods(
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

    private fun insertImports(parsedTemplate: TemplatesParser.Template) {
        if (parsedTemplate.imports.isEmpty()) return
        val importsFromTemplate =
            parsedTemplate.imports.joinToString("\n", postfix = "\n") { "import $it" }
        val newPyFile = importsFromTemplate + file.text
        project.files.first().changePsiFile(PSICreator.getPsiForPython(newPyFile)!!)
    }
}