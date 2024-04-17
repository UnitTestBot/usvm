package com.spbpu.bbfinfrastructure.mutator.mutations.java.templates

import com.intellij.psi.PsiClass
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
import com.spbpu.bbfinfrastructure.project.GlobalTestSuite
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.*
import com.spbpu.bbfinfrastructure.util.exceptions.MutationFinishedException
import org.jetbrains.kotlin.psi.psiUtil.parents
import kotlin.random.Random

class TemplatesInserter : Transformation() {

    private val testSuite = GlobalTestSuite.javaTestSuite
    private val originalPsiText = file.text
    private val numOfSuccessfulMutationsToAdd = 1
    private var curNumOfSuccessfulMutations = 0
    private var addedProjects = 0
    private val numberOfProjectsToCheck = 10
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

    private fun tryToTransform(): Boolean {
        val (randomTemplateFile, pathToTemplateFile) = TemplatesDB.getRandomTemplateForFeature(testingFeature) ?: return false
        val randomPlaceToInsert = file.getRandomPlaceToInsertNewLine() ?: return false
        val randomPlaceToInsertLineNumber = randomPlaceToInsert.getLocationLineNumber()
        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
        val regex = Regex("""~\[(.*?)\]~""")
        val parsedTemplates = parseTemplate(randomTemplateFile) ?: return false
        for (auxClass in parsedTemplates.auxClasses) {
            val bbfFile =
                BBFFile("${auxClass.first.substringAfterLast('.')}.java", PSICreator.getPsiForJava(auxClass.second))
            project.addFile(bbfFile)
        }
        if (parsedTemplates.imports.isNotEmpty()) {
            val oldImportList = (file as PsiJavaFile).importList?.text ?: ""
            val additionalImports = parsedTemplates.imports.joinToString("\n") { "import $it" }
            val newImportList =
                (PSICreator.getPsiForJava("$oldImportList\n$additionalImports") as PsiJavaFile).importList!!
            (file as PsiJavaFile).importList?.replaceThis(newImportList) ?: return false
        }

        val (randomTemplate, randomTemplateIndex) = parsedTemplates.templates.randomOrNullWithIndex() ?: return false
        val auxMethods = randomTemplate.auxMethods
        val randomTemplateBody = randomTemplate.templateBody
        for (auxMethod in auxMethods) {
            val psiClass = randomPlaceToInsert.parents.find { it is PsiClass } as? PsiClassImpl ?: return false
            val m = Factory.javaPsiFactory.createMethodFromText(auxMethod, null)
            val lastMethod =
                psiClass.getAllChildrenOfCurLevel().findLast { it is PsiMethod && it.containingClass == psiClass }
                    ?: return false
            lastMethod.addAfterThisWithWhitespace(m, "\n\n")
        }
        val newText = regex.replace(randomTemplateBody) { result ->
            val hole = result.groupValues.getOrNull(1) ?: throw IllegalArgumentException()
            val isVar = hole.startsWith("VAR_")
            val type = if (isVar) hole.substringAfter("VAR_") else hole
            val capturedType = JavaTypeMappings.mappings[type] ?: type
            if (capturedType == "boolean" || capturedType == "java.lang.Boolean") {
                ConditionGenerator(scope).generate()?.let { return@replace it }
            }
            val isAssign = try {
                randomTemplateBody.substring(result.groups[0]!!.range.last + 1).let {
                    it.startsWith(" =") || it.startsWith(" +=") || it.startsWith(" -=")
                }
            } catch (e: Throwable) {
                false
            }
            val randomValueWithCompatibleType =
                if (Random.getTrue(20) || isAssign || isVar) {
                    if (capturedType == "java.lang.Object") {
                        scope.randomOrNull()?.name
                    } else {
                        scope.filter { it.type == capturedType }
                            .randomOrNull()?.name
                    }
                } else null
            if ((isVar || isAssign) && randomValueWithCompatibleType == null) {
                println("CANT FIND VARIABLE OF TYPE $capturedType for assignment")
                throw IllegalArgumentException()
            }
            randomValueWithCompatibleType
                ?: ExpressionGenerator().generateExpressionOfType(scope, capturedType)
                ?: throw IllegalArgumentException()
        }
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
        if (!checker.checkCompiling()) {
            throw IllegalArgumentException()
        } else {
            currentMutationChain.add(
                MutationInfo(
                    "TemplateInsertion",
                    "Insert template from $pathToTemplateFile with index $randomTemplateIndex",
                    MutationLocation(file.name, randomPlaceToInsertLineNumber)
                )
            )
            checker.curFile.changePsiFile(PSICreator.getPsiForJava(file.text))
            if (++curNumOfSuccessfulMutations == numOfSuccessfulMutationsToAdd) {
                curNumOfSuccessfulMutations = 0
                addedProjects++
                testSuite.addProject(project.copy(), currentMutationChain.toList())
                currentMutationChain.clear()
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(originalPsiText))
            }
            if (addedProjects >= numberOfProjectsToCheck) {
                throw MutationFinishedException()
//                testSuite.flushSuiteOnServer(
//                    "/home/stepanov/BenchmarkJavaFuzz/src/main/java/org/owasp/benchmark/testcode",
//                    "/home/stepanov/BenchmarkJavaFuzz/expectedresults-1.2.csv"
//                )
//                exitProcess(0)
//
//                val projectToCheckRes = testSuite.flushOnDiskAndCheck()
//                for ((project, checkingResult) in projectToCheckRes) {
//                    println("CHECKING RES = $checkingResult")
//                }
            }
        }
        return true
    }

    private fun parseTemplate(template: String): Template? {
        val regexForAuxClasses =
            Regex("""~class\s+(\S+)\s+start~\s*(.*?)\s*~class\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
        val foundAuxClasses = regexForAuxClasses.findAll(template)
        val auxClasses = mutableListOf<Pair<String, String>>()
        val imports = mutableListOf<String>()
        for (auxClass in foundAuxClasses) {
            val className = auxClass.groupValues[1]
            val classBody = auxClass.groupValues[2].trim()
            auxClasses.add(className to classBody)
        }
        val regexForMainClass = Regex("""~main class start~\s*(.*?)\s*~main class end~""", RegexOption.DOT_MATCHES_ALL)
        val mainClassTemplateBody = regexForMainClass.find(template)?.groupValues?.lastOrNull() ?: return null
        val importsRegex = Regex("""~import (.*?)~""", RegexOption.DOT_MATCHES_ALL)
        val templateRegex = Regex("""~template start~\s*(.*?)\s*~template end~""", RegexOption.DOT_MATCHES_ALL)
        val auxMethodsRegex =
            Regex("""~function\s+(\S+)\s+start~\s*(.*?)\s*~function\s+\S+\s+end~""", RegexOption.DOT_MATCHES_ALL)
        importsRegex.findAll(mainClassTemplateBody).forEach { imports.add(it.groupValues.last()) }
        val templatesBodies =
            templateRegex.findAll(mainClassTemplateBody)
                .map {
                    val body = it.groupValues.last()
                    val auxMethods = auxMethodsRegex.findAll(body).map { it.groupValues.last() }.toList()
                    val templateBody = body.substringAfterLast("end~\n")
                    TemplateBody(auxMethods, templateBody)
                }
                .toList()
        return Template(auxClasses, imports, templatesBodies)
    }

    private class Template(
        val auxClasses: List<Pair<String, String>>,
        val imports: List<String>,
        val templates: List<TemplateBody>
    )

    private data class TemplateBody(
        val auxMethods: List<String>,
        val templateBody: String
    )
}