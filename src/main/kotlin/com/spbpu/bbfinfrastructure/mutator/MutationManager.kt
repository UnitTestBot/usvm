package com.spbpu.bbfinfrastructure.mutator

interface MutationManager {

    fun run(
        pathToBenchmark: String,
        pathToBenchmarkToFuzz: String,
        pathScriptToStartFuzzBenchmark: String,
        pathToVulnomicon: String,
        numOfFilesToCheck: Int,
        isLocal: Boolean
    )

}