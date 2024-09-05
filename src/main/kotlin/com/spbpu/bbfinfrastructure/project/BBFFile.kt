package com.spbpu.bbfinfrastructure.project

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.spbpu.bbfinfrastructure.util.containsChildOfType
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.util.FuzzingConf

data class BBFFile(var name: String, var psiFile: PsiFile) {

    fun getLanguage(): LANGUAGE {
        return when {
            name.endsWith(".java") -> LANGUAGE.JAVA
            name.endsWith(".kt") -> LANGUAGE.KOTLIN
            name.endsWith(".py") -> LANGUAGE.PYTHON
            else -> LANGUAGE.UNKNOWN
        }
    }

    fun changePsiFile(newPsiFile: PsiFile, genCtx: Boolean = false) {
        if (!genCtx) {
            this.psiFile = newPsiFile
        } else {
            changePsiFile(newPsiFile.text)
        }
    }

    fun changePsiFile(newPsiFileText: String, checkCorrectness: Boolean = true, genCtx: Boolean = false): Boolean {
        val psiFile = createPSI(newPsiFileText)
        if (checkCorrectness && psiFile.containsChildOfType<PsiErrorElement>()) {
            println("NOT CORRECT")
            return false
        }
        this.psiFile = psiFile
        return true
    }

    fun isPsiWrong(): Boolean =
        createPSI(psiFile.text).getAllPSIChildrenOfType<PsiErrorElement>().isNotEmpty()

    private fun createPSI(text: String): PsiFile {
        val newPsi = when (getLanguage()) {
            LANGUAGE.JAVA -> PSICreator.getPsiForJava(text)
            else -> PSICreator.getPSIForText(text)
        }
        return newPsi
    }

    fun copy() = BBFFile(name, psiFile.copy() as PsiFile)

    override fun toString(): String =
        "// FILE: ${name.substringAfter(FuzzingConf.pathToTmpDir).substring(1)}\n\n${psiFile.text}"

    val text: String
        get() = psiFile.text
}
