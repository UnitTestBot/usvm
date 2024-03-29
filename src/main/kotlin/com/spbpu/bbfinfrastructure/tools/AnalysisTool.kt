package com.spbpu.bbfinfrastructure.tools

import com.spbpu.bbfinfrastructure.project.Project

interface AnalysisTool {

    fun test(dir: String): Map<String, Set<CWE>>
//    fun test(project: Project):List<CWE>
}

data class CWE(val num: Int)

enum class CHECKING_RESULT {
    CANNOT_FIND, BOTH_FOUND, DIFF
}