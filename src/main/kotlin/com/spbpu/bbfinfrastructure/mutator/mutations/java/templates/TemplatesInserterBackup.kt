//package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates
//
//import com.intellij.psi.PsiClass
//import com.intellij.psi.PsiElement
//import com.intellij.psi.PsiJavaFile
//import com.intellij.psi.PsiMethod
//import com.intellij.psi.impl.source.PsiClassImpl
//import com.spbpu.bbfinfrastructure.mutator.mutations.MutationInfo
//import com.spbpu.bbfinfrastructure.mutator.mutations.MutationLocation
//import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ConditionGenerator
//import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ExpressionGenerator
//import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.JavaScopeCalculator
//import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
//import com.spbpu.bbfinfrastructure.project.BBFFile
//import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
//import com.spbpu.bbfinfrastructure.psicreator.PSICreator
//import com.spbpu.bbfinfrastructure.psicreator.util.Factory
//import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
//import com.spbpu.bbfinfrastructure.util.*
//import com.spbpu.bbfinfrastructure.util.exceptions.MutationFinishedException
//import com.spbpu.bbfinfrastructure.util.statistic.StatsManager
//import org.jetbrains.kotlin.descriptors.runtime.structure.primitiveByWrapper
//import org.jetbrains.kotlin.psi.psiUtil.parents
//import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
//import kotlin.random.Random
//
//open class TemplatesInserterBackup : Transformation() {
//
//    private val testSuite = GlobalTestSuite.javaTestSuite
//    private val originalPsiText = file.text
//    private val numOfSuccessfulMutationsToAdd = CompilerArgs.numberOfMutationsPerFile
//    private var curNumOfSuccessfulMutations = 0
//    private var addedProjects = 0
//    private val numberOfProjectsToCheck = CompilerArgs.numberOfMutantsPerFile
//    private val currentMutationChain = mutableListOf<MutationInfo>()
//    private val testingFeature = TestingFeature.RANDOM
//
//
//    override fun transform() {
//        repeat(1_000) {
//            val fileBackupText = file.text
//            println("TRY $it")
//            try {
//                tryToTransform()
//            } catch (e: MutationFinishedException) {
//                return
//            } catch (e: Throwable) {
//                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
//            }
//        }
//    }
//
//    private fun getRandomTemplate(): Triple<Template, TemplateBody, Pair<String, Int>>? {
//        return if (CompilerArgs.badTemplatesOnlyMode) {
//            StatsManager.currentBadTemplatesList.randomOrNull()?.let {
//                val index = it.second.substringAfter(' ').toInt()
//                val body = it.first.templates.get(index)
//                Triple(it.first, body, it.second.substringBefore(' ') to index)
//            }
//        } else {
//            val (randomTemplateFile, pathToTemplateFile) = TemplatesDB.getRandomTemplateForFeature(testingFeature)
//                ?: return null
//            val parsedTemplate = parseTemplate(randomTemplateFile) ?: return null
//            val (randomTemplate, randomTemplateIndex) = parsedTemplate.templates.randomOrNullWithIndex() ?: return null
//            Triple(parsedTemplate, randomTemplate, pathToTemplateFile to randomTemplateIndex)
//        }
//    }
//
//
//    protected open fun tryToTransform(): Boolean {
//        val (parsedTemplate, randomTemplate, pathAndIndex) = getRandomTemplate() ?: return false
//        val (pathToTemplateFile, randomTemplateIndex) = pathAndIndex
//        val randomPlaceToInsert =
//            project.configuration.mutationRegion?.run {
//                when {
//                    startLine != null && endLine != null -> file.getRandomPlaceToInsertNewLine(startLine, endLine)
//                    startLine != null -> file.getRandomPlaceToInsertNewLine(startLine!!)
//                    else -> file.getRandomPlaceToInsertNewLine()
//                }
//            } ?: file.getRandomPlaceToInsertNewLine()
//        if (randomPlaceToInsert == null) return false
//        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
//        var currentBlockLineNumber = randomPlaceToInsertLineNumber
//        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
//        val mappedHoles = mutableMapOf<String, String>()
//        val mappedTypes = mutableMapOf<String, String>()
//        val filledBlocks = randomTemplate.templateBody.split("~[BODY]~")
//            .map {
//                val endOfBlock = file.getRandomPlaceToInsertNewLine(currentBlockLineNumber) ?: return false
//                fillTemplateBody(
//                    mappedTypes,
//                    mappedHoles,
//                    scope,
//                    it
//                ) to currentBlockLineNumber.also { currentBlockLineNumber = endOfBlock.getLocationLineNumber() }
//            }
//        val replacementPsiBlock =
//            if (filledBlocks.size == 1) {
//                val newText = filledBlocks.first().first
//                val newPsiBlock =
//                    try {
//                        Factory.javaPsiFactory.createCodeBlockFromText("{\n$newText\n}", null).also {
//                            it.lBrace!!.delete()
//                            it.rBrace!!.delete()
//                        }
//                    } catch (e: Throwable) {
//                        return false
//                    }
//                randomPlaceToInsert.replaceThis(newPsiBlock)
//                newPsiBlock
//            } else {
//                val newBody = filledBlocks.zipWithNext().joinToString("") {
//                    val startLine = it.first.second
//                    val endLine = it.second.second
//                    val bodyFromFile =
//                        file.getNodesBetweenWhitespaces(startLine, endLine)
//                            .filter { it.children.isEmpty() }
//                            .joinToString("") { it.text }
//                    "${it.first.first}$bodyFromFile"
//                } + filledBlocks.last().first + "\n"
//                val newPsiBlock =
//                    try {
//                        Factory.javaPsiFactory.createCodeBlockFromText("{\n$newBody\n}", null).also {
//                            it.lBrace!!.delete()
//                            it.rBrace!!.delete()
//                        }
//                    } catch (e: Throwable) {
//                        return false
//                    }
//                val start = filledBlocks.first().second
//                val end = filledBlocks.last().second
//                val nodesBetween = file.getNodesBetweenWhitespaces(start, end)
//                val nodeToReplace =
//                    nodesBetween
//                        .filter { it.parent !in nodesBetween }
//                        .mapIndexed { index, psiElement -> if (index > 0) psiElement.delete() else psiElement }
//                        .first() as PsiElement
//                nodeToReplace.replaceThis(newPsiBlock)
//                newPsiBlock
//            }
//        insertClasses(parsedTemplate)
//        insertImports(parsedTemplate)
//        insertAuxMethods(replacementPsiBlock, randomTemplate).let { if (!it) return false }
//        return checkNewCode(
//            MutationInfo(
//                mutationName = "TemplateInsertion",
//                mutationDescription = "Insert template from $pathToTemplateFile with index $randomTemplateIndex",
//                location = MutationLocation(file.name, randomPlaceToInsertLineNumber)
//            )
//        ).ifTrue {
//            project.configuration.mutatedRegion?.run {
//                project.configuration.mutatedRegion = ToolsResultsSarifBuilder.ResultRegion(
//                    endColumn = endColumn,
//                    startColumn = startColumn,
//                    startLine = startLine,
//                    endLine = endLine!! + replacementPsiBlock.text.count { it == '\n' },
//                )
//            }
//            StatsManager.saveMutationHistory(pathToTemplateFile, randomTemplateIndex)
//            true
//        } ?: false
//    }
//
//    protected fun checkNewCode(mutationInfo: MutationInfo?): Boolean {
//        return if (!checker.checkCompiling()) {
//            throw IllegalArgumentException()
//        } else {
//            mutationInfo?.let { currentMutationChain.add(it) }
//            checker.curFile.changePsiFile(PSICreator.getPsiForJava(file.text))
//            if (++curNumOfSuccessfulMutations == numOfSuccessfulMutationsToAdd) {
//                curNumOfSuccessfulMutations = 0
//                addedProjects++
//                testSuite.addProject(project.copy(), currentMutationChain.toList())
//                currentMutationChain.clear()
//                checker.curFile.changePsiFile(PSICreator.getPsiForJava(originalPsiText))
//            }
//            if (addedProjects >= numberOfProjectsToCheck) {
//                throw MutationFinishedException()
//            }
//            true
//        }
//    }
//
//    private fun fillTemplateBody(
//        mappedTypes: MutableMap<String, String>,
//        mappedHoles: MutableMap<String, String>,
//        scope: List<JavaScopeCalculator.JavaScopeComponent>,
//        randomTemplateBody: String
//    ): String {
//        val regex = Regex("""~\[(.*?)\]~""")
//        val expressionGenerator = ExpressionGenerator()
//        val newText = regex.replace(randomTemplateBody) replace@{ result ->
//            val hole = result.groupValues.getOrNull(1) ?: throw IllegalArgumentException()
//            if (hole.contains("@")) {
//                mappedHoles[hole]?.let { return@replace it }
//            }
//            val holeType =
//                when {
//                    hole.startsWith("TYPE") -> HOLE_TYPE.TYPE
//                    hole.startsWith("VAR_") -> HOLE_TYPE.VAR
//                    hole.startsWith("CONST_") -> HOLE_TYPE.CONST
//                    else -> HOLE_TYPE.EXPR
//                }
//            if (holeType == HOLE_TYPE.CONST) {
//                val literal = expressionGenerator.genConstant(getTypeFromHole(hole, mappedTypes)!!)!!
//                mappedHoles[hole] = literal
//                return@replace literal
//            }
//            if (holeType == HOLE_TYPE.TYPE) {
//                mappedTypes[hole]?.let { return@replace it }
//                val randomType = RandomTypeGenerator.generateRandomType()
//                mappedTypes[hole] = randomType
//                return@replace randomType
//            }
//            val type = getTypeFromHole(hole, mappedTypes) ?: run {
//                RandomTypeGenerator.generateRandomType().also {
//                    mappedTypes[hole.substringAfter("_")] = it
//                }
//            }
//            val capturedType = JavaTypeMappings.mappings[type] ?: type
//            if (capturedType == "boolean" || capturedType == "java.lang.Boolean") {
//                if (holeType == HOLE_TYPE.EXPR) {
//                    ConditionGenerator(scope).generate()?.let {
//                        if (hole.contains("@")) {
//                            mappedHoles[hole] = it
//                        }
//                        return@replace it
//                    }
//                }
//            }
//            val randomValueWithCompatibleType =
//                if (Random.getTrue(20) || holeType == HOLE_TYPE.VAR) {
//                    if (capturedType == "java.lang.Object") {
//                        scope.randomOrNull()?.name
//                    } else {
//                        getValueOfTypeFromScope(scope, capturedType)
//                    }
//                } else null
//            if (holeType == HOLE_TYPE.VAR && randomValueWithCompatibleType == null) {
//                throw IllegalArgumentException()
//            }
//            val resMapping =
//                randomValueWithCompatibleType
//                    ?: expressionGenerator.generateExpressionOfType(scope, capturedType)
//                    ?: throw IllegalArgumentException()
//            if (hole.contains("@")) {
//                mappedHoles[hole] = resMapping
//            }
//            resMapping
//        }
//        return newText
//    }
//
//    protected fun insertClasses(parsedTemplates: Template) {
//        for (auxClass in parsedTemplates.auxClasses) {
//            val bbfFile =
//                BBFFile("${auxClass.first.substringAfterLast('.')}.java", PSICreator.getPsiForJava(auxClass.second))
//            project.addFile(bbfFile)
//        }
//    }
//
//    protected fun insertImports(parsedTemplates: Template) {
//        if (parsedTemplates.imports.isNotEmpty()) {
//            val oldImportList = (file as PsiJavaFile).importList?.text ?: ""
//            val additionalImports = parsedTemplates.imports.joinToString("\n") { "import $it" }
//            val newImportList =
//                (PSICreator.getPsiForJava("$oldImportList\n$additionalImports") as PsiJavaFile).importList!!
//            (file as PsiJavaFile).importList?.replaceThis(newImportList) ?: return
//        }
//    }
//
//    protected fun insertAuxMethods(
//        randomPlaceToInsert: PsiElement,
//        randomTemplate: TemplatesInserterBackup.TemplateBody
//    ): Boolean {
//        val auxMethods = randomTemplate.auxMethods
//        for (auxMethod in auxMethods) {
//            val psiClass = randomPlaceToInsert.parents.find { it is PsiClass } as? PsiClassImpl ?: return false
//            val m = Factory.javaPsiFactory.createMethodFromText(auxMethod, null)
//            val lastMethod =
//                psiClass.getAllChildrenOfCurLevel().findLast { it is PsiMethod && it.containingClass == psiClass }
//                    ?: return false
//            lastMethod.addAfterThisWithWhitespace(m, "\n\n")
//        }
//        return true
//    }
//
//    protected fun parseTemplate(template: String): Template? {
//        val regexForAuxClasses =
//            Regex("""~class\s+(\S+)\s+start~\s*(.*?)\s*~class\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
//        val foundAuxClasses = regexForAuxClasses.findAll(template)
//        val auxClasses = mutableListOf<Pair<String, String>>()
//        val imports = mutableListOf<String>()
//        for (auxClass in foundAuxClasses) {
//            val className = auxClass.groupValues[1]
//            val classBody = auxClass.groupValues[2].trim()
//            auxClasses.add(className to classBody)
//        }
//        val regexForMainClass = Regex("""~main class start~\s*(.*?)\s*~main class end~""", RegexOption.DOT_MATCHES_ALL)
//        val mainClassTemplateBody = regexForMainClass.find(template)?.groupValues?.lastOrNull() ?: return null
//        val importsRegex = Regex("""~import (.*?)~""", RegexOption.DOT_MATCHES_ALL)
//        val templateRegex = Regex("""~template start~\s*(.*?)\s*~template end~""", RegexOption.DOT_MATCHES_ALL)
//        val auxMethodsRegex =
//            Regex("""~function\s+(\S+)\s+start~\s*(.*?)\s*~function\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
//        importsRegex.findAll(mainClassTemplateBody).forEach { imports.add(it.groupValues.last()) }
//        val templatesBodies =
//            templateRegex.findAll(mainClassTemplateBody)
//                .map {
//                    val body = it.groupValues.last()
//                    val auxMethods = auxMethodsRegex.findAll(body).map { it.groupValues.last() }.toList()
//                    val templateBody = body.substringAfterLast("end~\n")
//                    TemplateBody(auxMethods, templateBody)
//                }
//                .toList()
//        return Template(auxClasses, imports, templatesBodies)
//    }
//
//    private fun getTypeFromHole(hole: String, mappedTypes: Map<String, String>) =
//        if (hole.contains("TYPE")) {
//            mappedTypes[hole.substringAfter("_")]
//        } else {
//            hole.substringAfter("_").substringBefore("@")
//        }
//
//    private fun getValueOfTypeFromScope(scope: List<JavaScopeCalculator.JavaScopeComponent>, type: String): String? {
//        val jType =
//            try {
//                this::class.java.classLoader.loadClass(type)
//            } catch (e: Throwable) {
//                null
//            }
//        return jType?.let { jTypeNotNull ->
//            scope.filter { it.type == jTypeNotNull.name || it.type == jTypeNotNull.primitiveByWrapper?.name }
//                .randomOrNull()?.name
//        } ?: scope.filter { it.type == type }.randomOrNull()?.name
//    }
//
//
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
//
//    private enum class HOLE_TYPE {
//        VAR, EXPR, TYPE, CONST
//    }
//}