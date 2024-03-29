package com.spbpu.bbfinfrastructure.reduction

import com.spbpu.bbfinfrastructure.project.Project

class JavaReducer {

    private val numberOfProjectsToSend = 20

    fun reduce(project: Project) {
        for (bbfFile in project.files) {
            val psiFile = bbfFile.psiFile
            val backup = psiFile.text

        }
    }

}