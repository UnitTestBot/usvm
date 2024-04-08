package com.spbpu.bbfinfrastructure.mutator.mutations.java

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.JavaTestSuite
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.*
import kotlin.random.Random
import kotlin.system.exitProcess

class TemplatesInserter : Transformation() {

    private val testSuite = JavaTestSuite()
    private val originalPsiText = file.text
    private val numOfSuccessfulMutationsToAdd = 3
    private var curNumOfSuccessfulMutations = 0
    private val numberOfProjectsToCheck = 100


    override fun transform() {
        repeat(1_000_000) {
            val fileBackupText = file.text
            println("TRY $it")
            try {
                tryToTransform()
            } catch (e: Throwable) {
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(fileBackupText))
            }
        }

    }

    private fun tryToTransform(): Boolean {
        val randomTemplate = (TemplatesDB.manualTemplates).randomOrNull() ?: return false
        val randomPlaceToInsert = file.getRandomPlaceToInsertNewLine() ?: return false
        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
        val regex = Regex("""~\[(.*?)\]~""")
        val parsedTemplates = parseTemplate(randomTemplate) ?: return false
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
        val randomTemplateBody = parsedTemplates.templates.random()
        val newText = regex.replace(randomTemplateBody) { result ->
            val capturedType = JavaTypeMappings.mappings[result.groupValues[1]] ?: result.groupValues[1]
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
                if (Random.getTrue(20) || isAssign) {
                    if (capturedType == "Object") {
                        scope.randomOrNull()?.name
                    } else {
                        scope.filter { it.type == capturedType }
                            .randomOrNull()?.name
                    }
                } else null
            if (isAssign && randomValueWithCompatibleType == null) {
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
            checker.curFile.changePsiFile(PSICreator.getPsiForJava(file.text))
            if (++curNumOfSuccessfulMutations == numOfSuccessfulMutationsToAdd) {
                curNumOfSuccessfulMutations = 0
                testSuite.addProject(project.copy())
                checker.curFile.changePsiFile(PSICreator.getPsiForJava(originalPsiText))
            }
            if (testSuite.suiteProjects.size >= numberOfProjectsToCheck) {
                testSuite.flushSuiteOnServer(
                    "/home/stepanov/BenchmarkJavaFuzz/src/main/java/org/owasp/benchmark/testcode",
                    "/home/stepanov/BenchmarkJavaFuzz/expectedresults-1.2.csv"
                )
                exitProcess(0)
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
        importsRegex.findAll(mainClassTemplateBody).forEach { imports.add(it.groupValues.last()) }
        val templatesBodies =
            templateRegex.findAll(mainClassTemplateBody).map { it.groupValues.last() }.toList()
        return Template(auxClasses, imports, templatesBodies)
    }

    private class Template(
        val auxClasses: List<Pair<String, String>>,
        val imports: List<String>,
        val templates: List<String>
    )
}