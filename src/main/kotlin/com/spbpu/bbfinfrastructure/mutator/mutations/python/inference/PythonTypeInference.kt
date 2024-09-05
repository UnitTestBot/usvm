package com.spbpu.bbfinfrastructure.mutator.mutations.python.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.psi.*
import com.spbpu.bbfinfrastructure.compiler.python.MyPyExecutor
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.PythonScopeCalculator
import com.spbpu.bbfinfrastructure.mutator.mutations.java.util.ScopeComponent
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.PythonPSIFactory
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.util.getAllPSIDFSChildrenOfType
import com.spbpu.bbfinfrastructure.util.getLocationLineNumber
import com.spbpu.bbfinfrastructure.util.replaceThis
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.io.File

class PythonTypeInference {

    //Key is parent function name
    //When key is null it means that variable is global
    val inferredTypes = mutableMapOf<String?, MutableSet<ScopeComponent>>()


    fun inferTypes(project: Project, pathToBenchmark: String) {
        project.configuration.originalUri ?: return
        val pathToFile = "$pathToBenchmark/${project.configuration.originalUri}"
        val backupText = project.files.first().text
        try {
            doInfer(project, pathToBenchmark)
        } finally {
            File(pathToFile).writeText(backupText)
        }
        return
    }

    private fun doInfer(project: Project, pathToBenchmark: String) {
        val directoryWithFile = project.configuration.getDirOfOriginalFile()
        val file = project.files.first()
        val psiCopy = (file.copy().psiFile).let {
            PSICreator.getPsiForPython("import math\n${it.text}")
        } as PyFile
        for (assigment in psiCopy.getAllPSIChildrenOfType<PyAssignmentStatement> { it.leftHandSideExpression is PyTargetExpression }) {
            val isInFunction = assigment.parents.any { it is PyFunction }
            val l = assigment.leftHandSideExpression?.text ?: continue
            var offset = 0
            try {
                if (assigment.prevSibling is PsiWhiteSpace) {
                    assigment.prevSibling.text?.substringAfterLast('\n')
                        ?.forEach {
                            when (it) {
                                ' ' -> offset++
                                '\t' -> offset += 4
                            }
                        }
                } else {
                    throw IllegalArgumentException()
                }
            } catch (e: Throwable) {
                psiCopy.getAllPSIDFSChildrenOfType<PsiElement>()
                    .takeWhile { it != assigment }
                    .lastOrNull { it is PsiWhiteSpace }
                    ?.text?.substringAfterLast('\n')
                    ?.forEach {
                        when (it) {
                            ' ' -> offset++
                            '\t' -> offset += 4
                        }
                    } ?: continue
            }
            if (!isInFunction) {
                if (offset != 0) continue
                val testAssigment = PythonPSIFactory.createPythonStatementList("${assigment.text}\n$l = 1") ?: continue
                assigment.replaceThis(testAssigment)
            } else {
                if (offset == 0) continue
                val pyFunc = assigment.parents.first { it is PyFunction } as PyFunction
                if (pyFunc.annotation == null) {
                    val anno = PythonPSIFactory.createReturnTypeAnnotation("int")
                    pyFunc.addAfter(anno, pyFunc.parameterList)
                }
                val testIfAsString =
                    with(StringBuilder()) {
                        repeat(offset) {
                            append(' ')
                        }
                        appendLine(assigment.text)
                        repeat(offset) {
                            append(' ')
                        }
                        appendLine("if math.exp(1.0):")
                        repeat(offset + 4) {
                            append(' ')
                        }
                        append("return $l")
                    }.toString()
                val testIf = PythonPSIFactory.createPythonStatementList(testIfAsString) ?: continue
                assigment.replaceThis(testIf)
            }
        }
        val rebuiltPSI = PSICreator.getPsiForPython(psiCopy.text)!!
        val pathToFile = "$pathToBenchmark/${project.configuration.originalUri}"
        File(pathToFile).writeText(rebuiltPSI.text)
        val myPyOutput = MyPyExecutor().exec(directoryWithFile)
        myPyOutput.split("\n").forEach { str ->
            val line = str.substringAfter(":").substringBefore(":").toIntOrNull() ?: return@forEach
            val regexForGlobal = Regex("""variable has type "(.*)"""")
            val regexForLocal = Regex("""got ".*", expected "(.*)"""")
            val inferredType = if (regexForGlobal.containsMatchIn(str)) {
                str.substringAfter("variable has type \"").substringBefore("\"").split(" | ").first()
            } else if (regexForLocal.containsMatchIn(str)) {
                str.substringAfter("got \"").substringBefore("\"").split(" | ").first()
            } else {
                return@forEach
            }
            val targetAssigment =
                rebuiltPSI
                    .getAllPSIDFSChildrenOfType<PyAssignmentStatement>()
                    .firstOrNull { it.getLocationLineNumber().let { it == line - 1 || it == line - 2 } }
                    ?: return@forEach
            val parentFunction = targetAssigment.parents.firstIsInstanceOrNull<PyFunction>()?.name
            val scopeComponent =
                PythonScopeCalculator.PythonScopeComponent(targetAssigment.leftHandSideExpression!!.text, inferredType)
            inferredTypes.getOrPut(parentFunction) { mutableSetOf() }.add(scopeComponent)
        }
    }


}