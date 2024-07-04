package com.spbpu.bbfinfrastructure.project.suite

interface TestSuite {

    fun flushSuiteAndRun(
        pathToFuzzBenchmark: String,
        scriptToStartBenchmark: String,
        pathToVulnomicon: String,
        isLocal: Boolean
    )

}