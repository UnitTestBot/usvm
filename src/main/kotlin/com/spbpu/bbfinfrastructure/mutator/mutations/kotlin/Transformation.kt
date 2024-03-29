package com.spbpu.bbfinfrastructure.mutator.mutations.kotlin

import com.intellij.psi.PsiFile
import com.spbpu.bbfinfrastructure.mutator.checkers.MutationChecker
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import org.apache.log4j.Logger
import org.jetbrains.kotlin.resolve.BindingContext

abstract class Transformation {
    abstract fun transform()

    companion object {
        lateinit var checker: MutationChecker
        val file: PsiFile
            get() = checker.curFile.psiFile
        val project: Project
            get() = checker.project
        var ctx: BindingContext? = null
//        internal val log = Logger.getLogger("mutatorLogger")

        fun updateCtx() {
            ctx = PSICreator.analyze(file, project)
        }
    }

}