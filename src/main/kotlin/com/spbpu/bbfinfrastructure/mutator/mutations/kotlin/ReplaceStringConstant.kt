package com.spbpu.bbfinfrastructure.mutator.mutations.kotlin

import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ReplaceStringConstant: Transformation() {
    override fun transform() {
        val stringConstant = file.getAllPSIChildrenOfType<KtStringTemplateExpression>().first()
        val newConstant = Factory.psiFactory.createStringTemplate("Bye bye, world!")
        checker.replaceNodeIfPossible(stringConstant, newConstant)
    }
}