package com.spbpu.bbfinfrastructure.mutator.mutations.java

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType

class ReplaceStringConstant: Transformation() {
    override fun transform() {
        val stringConstant = file.getAllPSIChildrenOfType<PsiJavaTokenImpl>()
            .filter { it.elementType.index == 288.toShort() }
            .first()
        val newConstant = JavaPsiFacade.getInstance(file.project).elementFactory.createExpressionFromText("\"Bye bye, world!\"", null)
        checker.replaceNodeIfPossible(stringConstant, newConstant)
    }
}