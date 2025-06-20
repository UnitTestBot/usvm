import java.io.FileNotFoundException

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-ts-dataflow"))

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_ets)
    implementation(Libs.grpc_api)

    implementation(Libs.ksmt_yices)
    implementation(Libs.ksmt_cvc5)
    implementation(Libs.ksmt_symfpu)
    implementation(Libs.ksmt_runner)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
}

val generateSdkIR by tasks.registering {
    group = "build"
    description = "Generates SDK IR using ArkAnalyzer."
    doLast {
        logger.lifecycle("Generating SDK IR using ArkAnalyzer...")
        val startTime = System.currentTimeMillis()

        val envVarName = "ARKANALYZER_DIR"

        val arkAnalyzerPath = System.getenv(envVarName) ?: run {
            error("Please, set $envVarName environment variable.")
        }
        val arkAnalyzerDir = rootDir.resolve(arkAnalyzerPath)

        val scriptSubPath = "src/save/serializeArkIR"
        val script = arkAnalyzerDir.resolve("out").resolve("$scriptSubPath.js")
        if (!script.exists()) {
            throw FileNotFoundException("Script file not found: '$script'. Did you forget to execute 'npm run build' in the ArkAnalyzer folder?")
        }

        val resources = projectDir.resolve("src/test/resources")
        val prefix = "sdk/ohos/"
        val inputDir = resources.resolve("${prefix}ets")
        val outputDir = resources.resolve("${prefix}etsir")
        println("Generating SDK IR into '${outputDir.relativeTo(projectDir)}'...")

        // cd src/test/resources
        // cd sdk/ohos/5.0.1.111
        // npx ts-node .../serializeArkIR.ts -v -p ets etsir

        val cmd: List<String> = listOf(
            "node",
            script.absolutePath,
            "-v",
            "-p",
            inputDir.relativeTo(resources).path,
            outputDir.relativeTo(resources).path,
        )
        logger.lifecycle("Running command: ${cmd.joinToString(" ")}")
        val result = ProcessUtil.run(cmd) {
            directory(resources)
        }
        if (result.stdout.isNotBlank()) {
            logger.lifecycle("[STDOUT]:\n--------\n${result.stdout}--------")
        }
        if (result.stderr.isNotBlank()) {
            logger.lifecycle("[STDERR]:\n--------\n${result.stderr}--------")
        }
        if (result.isTimeout) {
            logger.warn("Timeout!")
        }
        if (result.exitCode != 0) {
            logger.warn("Exit code: ${result.exitCode}")
        }

        logger.lifecycle(
            "Done generating SDK IR in %.1fs"
                .format((System.currentTimeMillis() - startTime) / 1000.0)
        )
    }
}
