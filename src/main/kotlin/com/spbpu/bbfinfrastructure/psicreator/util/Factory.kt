package com.spbpu.bbfinfrastructure.psicreator.util

import com.intellij.psi.JavaPsiFacade
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import org.jetbrains.kotlin.psi.KtPsiFactory

object Factory {
    val file = PSICreator.getPSIForText("")
    val psiFactory = KtPsiFactory(file.project)
    val javaPsiFactory = JavaPsiFacade.getInstance(file.project).elementFactory

    fun KtPsiFactory.tryToCreateExpression(text: String) =
        try {
            psiFactory.createExpressionIfPossible(text)
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
}
