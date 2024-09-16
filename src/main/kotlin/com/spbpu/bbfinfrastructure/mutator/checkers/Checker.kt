package com.spbpu.bbfinfrastructure.mutator.checkers

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.spbpu.bbfinfrastructure.compiler.COMPILE_STATUS
import com.spbpu.bbfinfrastructure.compiler.CommonCompiler
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.LANGUAGE
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.test.ErrorCollector
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import org.apache.log4j.Logger

//Project adaptation
open class Checker(private val compilers: List<CommonCompiler>, private val withTracesCheck: Boolean = true) {

    constructor(compiler: CommonCompiler) : this(listOf(compiler))

    //Back compatibility
    fun checkTextCompiling(text: String): Boolean = checkCompiling(Project.createFromCode(text))
    fun checkCompiling(file: PsiFile): Boolean = checkTextCompiling(file.text)

    fun checkCompilationOfProject(project: Project, curFile: BBFFile? = null): Boolean {
        // log.debug("Compilation checking started")
        val allTexts = project.files.map { it.psiFile.text }.joinToString()
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, curFile)) {
            if (FuzzingConf.testMode) {
                ErrorCollector.putError("Syntax error! Can't parse code with template")
            }
//            println("Wrong syntax or breaks conditions")
            return false
        }
        val statuses = compileAndGetStatuses(project)
        val isOK = statuses.all { it == COMPILE_STATUS.OK }
        return isOK
    }

    private fun createPsiAndCheckOnErrors(text: String, language: LANGUAGE): Boolean =
        when (language) {
            LANGUAGE.JAVA -> PSICreator.getPsiForJava(text)
            LANGUAGE.PYTHON -> PSICreator.getPsiForPython(text)
            LANGUAGE.GO -> PSICreator.getPsiForGo(text)
            else -> Factory.psiFactory.createFile(text)
        }?.let { tree ->
            tree.getAllPSIChildrenOfType<PsiErrorElement>().isEmpty() && additionalConditions.all { it.invoke(tree) }
        } ?: false

    //FALSE IF ERROR
    private fun checkSyntaxCorrectnessAndAddCond(project: Project, curFile: BBFFile?) =
        curFile?.let {
            createPsiAndCheckOnErrors(curFile.text, curFile.getLanguage())
        } ?: project.files.any { createPsiAndCheckOnErrors(it.text, it.getLanguage()) }


    private fun compileAndGetStatuses(project: Project): List<COMPILE_STATUS> =
        compilers.map { it.tryToCompileWithStatus(project) }

    fun checkCompiling(project: Project): Boolean {
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, null)) {
            // log.debug("Wrong syntax or breaks conditions")
            return false
        }
        val statuses = compileAndGetStatuses(project)
        return if (statuses.all { it == COMPILE_STATUS.OK }) {
            true
        } else {
            false
        }
    }

    val additionalConditions: MutableList<(PsiFile) -> Boolean> = mutableListOf()
}