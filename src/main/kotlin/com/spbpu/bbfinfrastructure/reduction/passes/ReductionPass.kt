package com.spbpu.bbfinfrastructure.reduction.passes

import com.spbpu.bbfinfrastructure.project.JavaTestSuite
import com.spbpu.bbfinfrastructure.project.Project

interface ReductionPass {
    fun reduce(project: Project): Project

    fun check(projects: List<Project>): Project {
        val javaTestSuite = JavaTestSuite()
        projects.forEach { javaTestSuite.addProject(it, false) }
        javaTestSuite.flushOnDiskAndCheck()
        return projects.first()
    }

}