package com.spbpu.bbfinfrastructure.project.suite

import com.spbpu.bbfinfrastructure.project.BBFFile
import com.spbpu.bbfinfrastructure.project.Project

class GoTestSuite: TestSuite() {
    override fun flushSuiteAndRun(
        pathToFuzzBenchmark: String,
        scriptToStartBenchmark: String,
        pathToVulnomicon: String,
        pathToReportsDir: String,
        isLocal: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun rename(project: Project, bbfFile: BBFFile, projectIndex: Int) {
        return
    }
}