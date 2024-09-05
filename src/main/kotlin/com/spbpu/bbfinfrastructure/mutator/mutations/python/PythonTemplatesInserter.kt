package com.spbpu.bbfinfrastructure.mutator.mutations.python

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
import com.spbpu.bbfinfrastructure.mutator.mutations.python.inference.PythonTypeInference
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.suite.GlobalTestSuite
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
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


class PythonTemplatesInserter : BaseTemplatesInserter() {

    val inferredTypesForFile =
        with(PythonTypeInference()) {
            inferTypes(project, FuzzingConf.pathToBenchmarkToFuzz)
            inferredTypes
        }

    private val testSuite = GlobalTestSuite.pythonTestSuite


    private val originalPsiText = file.text
    private var originalPsiTextWithAddedImports = ""

    private var addedLines = 0
    private var addedImportsLines = 0

    override fun transform() {
        insertHelperClasses()
        originalPsiTextWithAddedImports = file.text
        repeat(1_000) {
            insertingObjectsTemplates = false
            val backupPsiText = file.text
            println("TRY $it addedProject = $addedProjects")
            try {
                tryToTransform().ifFalse {
                    checker.curFile.changePsiFile(PSICreator.getPsiForPython(backupPsiText)!!)
                }
            } catch (e: MutationFinishedException) {
                return
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForPython(backupPsiText)!!)
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
        val scope =
            PythonScopeCalculator().calcVariablesAndFunctionsFromScope(randomPlaceToInsert).let { calculatedScope ->
                val targetMethod = randomPlaceToInsert.parents.firstIsInstanceOrNull<PyFunction>()?.name
                val inferredTypes = inferredTypesForFile[targetMethod]?.also {
                    it.addAll(inferredTypesForFile[null] ?: setOf())
                }
                calculatedScope.flatMap { scopeComponent ->
                    val inferredScopeComponent =
                        inferredTypes?.find { it.name == scopeComponent.name } ?: scopeComponent
                    if (inferredScopeComponent.type in listOf("Any", "int", "str")) return@flatMap listOf(
                        inferredScopeComponent
                    )
                    val dirToProject =
                        FuzzingConf.pathToBenchmarkToFuzz + "/" + project.configuration.originalUri!!.substringBeforeLast(
                            '/'
                        )
                    val classDefinition = Files.walk(Paths.get(dirToProject)).toList()
                        .map { it.toFile() }
                        .filter { it.path.endsWith(".py") }
                        .flatMap {
                            PSICreator.getPsiForPython(it.readText())?.getAllPSIChildrenOfType<PyClass>() ?: listOf()
                        }
                        .find { it.name == inferredScopeComponent.type }
                        ?: return@flatMap listOf(inferredScopeComponent)
                    listOf(inferredScopeComponent) + classDefinition.classAttributes.mapNotNull {
                        it.name?.let {
                            PythonScopeCalculator.PythonScopeComponent(
                                "${scopeComponent.name}.${it}",
                                "Any"
                            )
                        }
                    }
                }
            }
        val usedExtensions = mutableListOf<String>()
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        val filledBlocks = fillBlocks(randomTemplate, randomPlaceToInsert, scope, parsedTemplate, usedExtensions, true)
            ?: throw IllegalArgumentException()
        val replacementPsiBlock =
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
        insertImports(parsedTemplate)
        val mutatedFileUri = "${FuzzingConf.pathToBenchmarkToFuzz}/${project.configuration.originalUri!!}"
        try {
            if (PSICreator.getPsiForPython(file.text)!!.getAllChildren().any { it is PsiErrorElement }) {
                throw IllegalArgumentException()
            }
            File(mutatedFileUri).writeText(file.text)
            if (!checker.checkCompiling(project)) throw IllegalStateException()
            addedLines += replacementPsiBlock.text.count { it == '\n' }
            currentMutationChain.add(
                MutationInfo(
                    mutationName = "TemplateInsertion",
                    isObjectTemplate = insertingObjectsTemplates,
                    mutationDescription = "Insert template from $pathToTemplateFile with name ${randomTemplate.name}",
                    usedExtensions = usedExtensions,
                    location = MutationLocation(file.name, randomPlaceToInsertLineNumber)
                )
            )
            //StatsManager.saveMutationHistory(pathToTemplateFile, randomTemplateIndex)
            if (addedProjects >= numberOfProjectsToCheck) {
                throw MutationFinishedException()
            }
            checker.curFile.changePsiFile(PSICreator.getPsiForPython(file.text)!!)
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
                checker.curFile.changePsiFile(PSICreator.getPsiForPython(originalPsiTextWithAddedImports)!!)
            }
            return true
        } finally {
            File(mutatedFileUri).writeText(originalPsiText)
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
        if (Random.getTrue(75) && iteration < 2) {
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
                if (Random.getTrue(20)) {
                    scope.filter { it.type == "Any" }.randomOrNull()?.name
                } else {
                    scope.filter { it.type == type }.randomOrNull()?.name ?: scope.filter { it.type == "Any" }
                        .randomOrNull()?.name
                }
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