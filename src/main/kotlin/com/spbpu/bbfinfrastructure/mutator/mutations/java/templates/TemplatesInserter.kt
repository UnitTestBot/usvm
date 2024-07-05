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
    private val numOfSuccessfulMutationsToAdd = CompilerArgs.numberOfMutationsPerFile
    private var curNumOfSuccessfulMutations = 0
    private var addedProjects = 0
    private val numberOfProjectsToCheck = CompilerArgs.numberOfMutantsPerFile
    private val currentMutationChain = mutableListOf<MutationInfo>()
    private val testingFeature = TestingFeature.RANDOM


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

    private fun getRandomTemplate(): Triple<TemplatesParser.Template, TemplatesParser.TemplateBody, Pair<String, Int>>? {
        return if (CompilerArgs.badTemplatesOnlyMode) {
            StatsManager.currentBadTemplatesList.randomOrNull()?.let {
                val index = it.second.substringAfter(' ').toInt()
                val body = it.first.templates.get(index)
                Triple(it.first, body, it.second.substringBefore(' ') to index)
            }
        } else {
            val randomTemplateFile = TemplatesDB.getRandomTemplateForFeature(testingFeature) ?: error("Cant find any template")
            val parsedTemplate = TemplatesParser.parse(randomTemplateFile.path) ?: return null
            val (randomTemplate, randomTemplateIndex) = parsedTemplate.templates.randomOrNullWithIndex() ?: return null
            Triple(parsedTemplate, randomTemplate, randomTemplateFile.path to randomTemplateIndex)
        }
    }


    protected open fun tryToTransform(): Boolean {
        val (parsedTemplate, randomTemplate, pathAndIndex) = getRandomTemplate() ?: return false
        val (pathToTemplateFile, randomTemplateIndex) = pathAndIndex
        val randomPlaceToInsert =
            project.configuration.mutationRegion?.run {
                when {
                    startLine != null && endLine != null -> file.getRandomPlaceToInsertNewLine(startLine, endLine)
                    startLine != null -> file.getRandomPlaceToInsertNewLine(startLine!!)
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
                filledTemplate to currentBlockLineNumber.also { currentBlockLineNumber = endOfBlock.getLocationLineNumber() }
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
                    endLine = endLine!! + numberOfAddedImports + replacementPsiBlock.text.count { it == '\n' },
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
            if (++curNumOfSuccessfulMutations == numOfSuccessfulMutationsToAdd) {
                curNumOfSuccessfulMutations = 0
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
        val regex = Regex("""~\[(.*?)\]~""")
        val expressionGenerator = ExpressionGenerator()
        val newText = regex.replace(randomTemplateBody) replace@{ result ->
            val hole = result.groupValues.getOrNull(1) ?: throw IllegalArgumentException()
            if (hole.contains("@")) {
                mappedHoles[hole]?.let { return@replace it }
            }
            val holeType =
                when {
                    hole.startsWith("TYPE") -> HOLE_TYPE.TYPE
                    hole.startsWith("VAR_") -> HOLE_TYPE.VAR
                    hole.startsWith("CONST_") -> HOLE_TYPE.CONST
                    hole.startsWith("EXPR_") -> HOLE_TYPE.EXPR
                    else -> HOLE_TYPE.MACROS
                }
            if (holeType == HOLE_TYPE.MACROS) {
                val replacement = checkFromExtensionsAndMacros(parsedTemplate, hole)
                return@replace replacement?.also { usedExtensions.add("$hole -> $it") } ?: error("Can't find replacement for hole $hole")
            }
            if (Random.getTrue(50) && iteration < 3) {
                checkFromExtensionsAndMacros(parsedTemplate, hole)?.let {
                    usedExtensions.add("$hole -> $it")
                    return@replace it
                }
            }
            if (holeType == HOLE_TYPE.CONST) {
                val literal = expressionGenerator.genConstant(getTypeFromHole(hole, mappedTypes)!!)!!
                if (hole.contains("@")) {
                    mappedHoles[hole] = literal
                }
                return@replace literal
            }
            if (holeType == HOLE_TYPE.TYPE) {
                mappedTypes[hole]?.let { return@replace it }
                val randomType = RandomTypeGenerator.generateRandomType()
                if (hole.contains("@")) {
                    mappedTypes[hole] = randomType
                }
                return@replace randomType
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
                        return@replace it
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
            resMapping
        }
        return newText
    }

    private fun checkFromExtensionsAndMacros(template: TemplatesParser.Template, hole: String): String? =
        template.extensions[hole]?.randomOrNull()

    protected fun insertClasses(parsedTemplate: TemplatesParser.Template, originalPackageDirective: String) {
        for ((auxClassName, auxClassBody) in parsedTemplate.auxClasses) {
            if (project.files.any { it.name == "$auxClassName.java" }) {
                continue
            }
            val psiForClass =
                PSICreator.getPsiForJava(auxClassBody) as PsiJavaFile
            //Add package directive
            val packageDirective = Factory.javaPsiFactory.createPackageStatement(originalPackageDirective)
            psiForClass.addToTheTop(packageDirective)
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
        VAR, EXPR, TYPE, CONST, MACROS
    }
}