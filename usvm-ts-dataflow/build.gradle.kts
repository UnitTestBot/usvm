import java.io.FileNotFoundException
import kotlin.time.Duration.Companion.minutes

plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
    application
    id(Plugins.Shadow)
    `java-test-fixtures`
}

dependencies {
    api(project(":usvm-dataflow"))
    implementation(project(":usvm-util"))

    api(Libs.jacodb_api_common)
    api(Libs.jacodb_ets)
    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_taint_configuration)
    implementation(Libs.kotlinx_collections)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_serialization_protobuf)
    implementation(Libs.clikt)
    runtimeOnly(Libs.logback)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)

    testFixturesImplementation(Libs.kotlin_logging)
    testFixturesImplementation(Libs.junit_jupiter_api)
}

tasks.withType<Test> {
    maxHeapSize = "4G"
}

val generateTestResources by tasks.registering {
    group = "build"
    description = "Generates test resources from TypeScript files using ArkAnalyzer."
    doLast {
        logger.lifecycle("Generating test resources using ArkAnalyzer...")
        val startTime = System.currentTimeMillis()

        val envVarName = "ARKANALYZER_DIR"
        val defaultArkAnalyzerDir = "../arkanalyzer"

        val arkAnalyzerDir = rootDir.resolve(System.getenv(envVarName) ?: run {
            logger.lifecycle("Please, set $envVarName environment variable. Using default value: '$defaultArkAnalyzerDir'")
            defaultArkAnalyzerDir
        })
        if (!arkAnalyzerDir.exists()) {
            throw FileNotFoundException("ArkAnalyzer directory does not exist: '$arkAnalyzerDir'. Did you forget to set the '$envVarName' environment variable?")
        }

        val scriptSubPath = "src/save/serializeArkIR"
        val script = arkAnalyzerDir.resolve("out").resolve("$scriptSubPath.js")
        if (!script.exists()) {
            throw FileNotFoundException("Script file not found: '$script'. Did you forget to execute 'npm run build' in the arkanalyzer project?")
        }

        val resources = projectDir.resolve("src/test/resources")
        val inputDir = resources.resolve("ts")
        val outputDir = resources.resolve("ir")
        logger.lifecycle("Generating test resources in '${outputDir.relativeTo(projectDir)}'...")

        inputDir.walkTopDown().filter { it.isFile }.forEach { input ->
            val output = outputDir
                .resolve(input.relativeTo(inputDir))
                .resolveSibling(input.name + ".json")
            logger.lifecycle("Regenerating JSON for '${output.relativeTo(outputDir)}'...")

            val cmd: List<String> = listOf(
                "node",
                script.absolutePath,
                input.relativeTo(resources).path,
                output.relativeTo(resources).path,
            )
            logger.lifecycle("Running: ${cmd.joinToString(" ")}")
            val result = ProcessUtil.run(cmd, timeout = 1.minutes) {
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
                "Done generating test resources in %.1fs"
                    .format((System.currentTimeMillis() - startTime) / 1000.0)
            )
        }
    }
}

// tasks.test {
//     dependsOn(generateTestResources)
// }

application {
    mainClass = "org.usvm.dataflow.ts.infer.cli.InferTypesKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
}

tasks.startScripts {
    applicationName = "usvm-type-inference"
}

tasks.shadowJar {
    minimize {
        // Note: keep 'mordant' dependency inside shadowJar, or else the following error occurs:
        // ```
        // Exception in thread "main" java.util.ServiceConfigurationError:
        // com.github.ajalt.mordant.terminal.TerminalInterfaceProvider:
        // Provider com.github.ajalt.mordant.terminal.terminalinterface.jna.TerminalInterfaceProviderJna not found
        // ```
        exclude(dependency("com.github.ajalt.mordant:.*:.*"))

        // Keep the logback in shadow jar:
        exclude(dependency("ch.qos.logback:logback-classic:.*"))
    }
}
