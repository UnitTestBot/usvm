package com.spbpu.bbfinfrastructure.mutator.mutations.java

import com.intellij.psi.PsiFile
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.project.JavaTestSuite
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.getRandomPlaceToInsertNewLine
import com.spbpu.bbfinfrastructure.util.replaceThis
import kotlin.system.exitProcess

class TemplatesInserter : Transformation() {

    private val testSuite = JavaTestSuite()
    private val originalPsiText = file.text
    private val numOfSuccessfulMutationsToAdd = 10
    private var curNumOfSuccessfulMutations = 0
    private val numberOfProjectsToCheck = 100


    override fun transform() {
        repeat(1_000_000) {
//            println("TRY $it")
            try {
                tryToTransform()
            } catch (e: Throwable) {
//                println("${e.message}${e.stackTraceToString()}")
            }
        }

    }

    private fun tryToTransform(): Boolean {
        val fileBackup = file.copy() as PsiFile
        val randomTemplate = (TemplatesDB.kotlinTemplates + TemplatesDB.minedTemplates).randomOrNull() ?: return false
        val randomPlaceToInsert = file.getRandomPlaceToInsertNewLine() ?: return false
        val scope = JavaScopeCalculator(file, project).calcScope(randomPlaceToInsert)
        val regex = Regex("""ValueTypeStart@(.*?)@ValueTypeEnd""")
        val newText = regex.replace(randomTemplate) { result ->
            val capturedType = result.groupValues[1]
            val randomValueWithCompatibleType =
                if (capturedType == "Object") {
                    scope.randomOrNull()
                } else {
                    scope.filter { it.type.presentableText == capturedType }
                        .randomOrNull()
                }
            if (randomValueWithCompatibleType == null) {
//                println("Can't find variable with type $capturedType")
            }
            randomValueWithCompatibleType?.name ?: throw IllegalArgumentException()
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
            checker.curFile.changePsiFile(fileBackup)
            return false
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

                val projectToCheckRes = testSuite.flushOnDiskAndCheck()
                for ((project, checkingResult) in projectToCheckRes) {
                    println("CHECKING RES = $checkingResult")
                }
            }
        }
        return true
    }
}