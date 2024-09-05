package com.spbpu.bbfinfrastructure.psicreator.util

import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.jetbrains.python.psi.PyFunction
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.util.getAllChildren
import com.spbpu.bbfinfrastructure.util.replaceThis

object PythonPSIFactory {

    fun createPythonStatementList(text: String) =
        try {
            val stmtList = PSICreator
                .getPsiForPython("def lol():\n${text.split("\n").joinToString("\n") { "\t$it" }}")!!
                .children[0]
                .children[1]
            stmtList.getAllChildren().map {
                if (it is PsiWhiteSpace) {
                    val newPsiWhiteSpaceAsString = it.text.replaceFirst("\t", "")
                    val newWhiteSpace = PsiWhiteSpaceImpl(newPsiWhiteSpaceAsString)
                    it.replaceThis(newWhiteSpace)
                } else {
                    null
                }
            }
            stmtList
        } catch (e: Throwable) {
            null
        }

    fun createPythonStatement(text: String) = createPythonStatementList(text)?.children?.first()

    fun createReturnTypeAnnotation(returnType: String) =
        (PSICreator.getPsiForPython("def lol() -> $returnType:\n")!!.children[0] as PyFunction).annotation!!

}