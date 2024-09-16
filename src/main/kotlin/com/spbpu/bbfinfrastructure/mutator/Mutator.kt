package com.spbpu.bbfinfrastructure.mutator

import com.spbpu.bbfinfrastructure.mutator.mutations.go.GoTemplatesInserter
import com.spbpu.bbfinfrastructure.mutator.mutations.java.templates.*
import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.*
import com.spbpu.bbfinfrastructure.mutator.mutations.python.PythonTemplatesInserter
import com.spbpu.bbfinfrastructure.project.LANGUAGE
import com.spbpu.bbfinfrastructure.project.Project
import kotlin.random.Random

class Mutator(val project: Project) {

    private fun executeMutation(t: Transformation, probPercentage: Int = 50) {
        if (Random.nextInt(0, 100) < probPercentage) {
//            log.debug("Cur transformation ${t::class.simpleName}")
            t.transform()
        }
    }


    fun startMutate() {
        for (bbfFile in project.files) {
//            log.debug("Mutation of ${bbfFile.name} started")
            Transformation.checker.curFile = bbfFile
            when (bbfFile.getLanguage()) {
                LANGUAGE.JAVA -> startJavaMutations()
                LANGUAGE.KOTLIN -> startKotlinMutations()
                LANGUAGE.PYTHON -> startPythonMutations()
                LANGUAGE.GO -> startGoMutations()
                LANGUAGE.KJAVA -> TODO()
                LANGUAGE.UNKNOWN -> TODO()
                LANGUAGE.CSHARP -> TODO()
            }
//            log.debug("End")
        }
    }


    //Stub
    private fun startJavaMutations() {
        println("STARTING JAVA MUTATIONS")
//        if (Random.getTrue(50)) {
//            val mutation = listOf(
//                IfTemplateBasedMutation(),
//                ForTemplateBasedMutation(),
//                WhileTemplateBasedMutation()
//            ).random()
//            executeMutation(mutation, 100)
//        } else {
            executeMutation(JavaTemplatesInserter(), 100)
//        }
        println("END JAVA MUTATIONS")
//        log.debug("Verify = ${verify()}")
        return
    }

    private fun startPythonMutations() {
        println("STARTING PYTHON MUTATIONS")
        executeMutation(PythonTemplatesInserter(), 100)
        println("END PYTHON MUTATIONS")
    }

    private fun startGoMutations() {
        println("STARTING PYTHON MUTATIONS")
        executeMutation(GoTemplatesInserter(), 100)
        println("END PYTHON MUTATIONS")
    }


    private fun startKotlinMutations() {
        executeMutation(AddLoop(), 100)
    }


    private fun verify(): Boolean {
        val res = Transformation.checker.checkCompiling(project)
        if (!res) {
//            log.debug("Cant compile project ${project}")
            System.exit(1)
        }
        return res
    }


    //    private val log = Logger.getLogger("bugFinderLogger")
    private val checker
        get() = Transformation.checker

}