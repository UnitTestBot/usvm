import java.io.FileNotFoundException

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-ts-dataflow"))

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_ets)

    implementation(Libs.ksmt_yices)
    implementation(Libs.ksmt_cvc5)
    implementation(Libs.ksmt_symfpu)
    implementation(Libs.ksmt_runner)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)

    // https://mvnrepository.com/artifact/org.burningwave/core
    // Use it to export all modules to all
    testImplementation("org.burningwave:core:12.62.7")
}

val generateSdkIR by tasks.registering {
    group = "build"
    description = "Generates SDK IR using ArkAnalyzer."
    doLast {
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
        val prefix = "sdk/ohos/5.0.1.111/"
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
        println("Running: '${cmd.joinToString(" ")}'")
        val process = ProcessBuilder(cmd).directory(resources).start()
        val ok = process.waitFor(5, TimeUnit.SECONDS)

        val stdout = process.inputStream.bufferedReader().readText().trim()
        if (stdout.isNotBlank()) {
            println("[STDOUT]:\n--------\n$stdout\n--------")
        }
        val stderr = process.errorStream.bufferedReader().readText().trim()
        if (stderr.isNotBlank()) {
            println("[STDERR]:\n--------\n$stderr\n--------")
        }

        if (!ok) {
            println("Timeout!")
            process.destroy()
        } else {
            println("Done running!")
        }
    }
}
