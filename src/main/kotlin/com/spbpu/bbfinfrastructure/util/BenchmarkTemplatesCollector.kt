package com.spbpu.bbfinfrastructure.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.PsiWhileStatement
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import java.io.File

object BenchmarkTemplatesCollector {

    fun collect(dir: String) {
        var i = 0
        val templatesSet = mutableSetOf<String>()

        for (f in File("lib/testcode").listFiles()) {
            println("HANDLE FILE ${f.name} ${i++} from ${File("lib/testcode").listFiles().size}")
            val project = Project.createJavaProjectFromFiles(listOf(f))
            val psiFile = project.files.first().psiFile
            psiFile.node.psi.getAllPSIChildrenOfFiveTypes<PsiIfStatement, PsiForStatement, PsiForeachStatement, PsiWhileStatement, PsiSwitchStatement>()
                .map { handleControlStatement(templatesSet, it) }
        }
        templatesSet
            .filter { !it.contains("BenchmarkTest") }
            .joinToString("\n---------\n").let { File("templates.txt").writeText(it) }
        println(templatesSet.size)
    }

    private fun handleControlStatement(templatesSet: MutableSet<String>, psiElement: PsiElement) {
        templatesSet.add(
            psiElement.getAllPSIDFSChildrenOfType<PsiElement>()
                .filter { it.children.isEmpty() }
                .joinToString("") {
                    handlePsiElement(it)
                }
        )
    }

    private fun handlePsiElement(psiElement: PsiElement): String = when (psiElement) {
        is PsiIdentifier -> handlePsiIndentifier(psiElement)
        else -> psiElement.text
    }

    private fun handlePsiIndentifier(psiElement: PsiIdentifier): String {
        val ref = psiElement.parent
        if (ref !is PsiReferenceExpression) {
            return psiElement.text
        }
        if (ref.isQualified) {
            return ref.referenceName ?: ref.text
        }
        val resolvedRef = try {
            ref.resolve() ?: return ref.text
        } catch (e: Throwable) {
            return "ref.text"
        }
        return when (resolvedRef) {
            is PsiLocalVariable -> "ValueTypeStart@${resolvedRef.type.presentableText}@ValueTypeEnd"
            is PsiParameter -> "ValueTypeStart@${resolvedRef.type.presentableText}@ValueTypeEnd"
            else -> "ERROR@${resolvedRef::class.java.name}@ERROR"
        }
    }

}