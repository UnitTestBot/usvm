import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.attribute.FileTime

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
    implementation(Libs.clikt)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
    testImplementation(Libs.kotlinx_serialization_core)

    testFixturesImplementation(Libs.kotlin_logging)
    testFixturesImplementation(Libs.junit_jupiter_api)
}

tasks.withType<Test> {
    maxHeapSize = "4G"
}

val generateTestResources by tasks.registering {
    group = "build"
    description = "Generates JSON resources from TypeScript files using ArkAnalyzer."
    doLast {
        val envVarName = "ARKANALYZER_DIR"
        val defaultArkAnalyzerDir = "../arkanalyzer"

        val arkAnalyzerDir = rootDir.resolve(System.getenv(envVarName) ?: run {
            println("Please, set $envVarName environment variable. Using default value: '$defaultArkAnalyzerDir'")
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
        println("Generating test resources in '${outputDir.relativeTo(projectDir)}'...")

        inputDir.walkTopDown().filter { it.isFile }.forEach { input ->
            val output = outputDir
                .resolve(input.relativeTo(inputDir))
                .resolveSibling(input.name + ".json")
            val inputFileTime = Files.getLastModifiedTime(input.toPath())
            val outputFileTime = if (output.exists()) {
                Files.getLastModifiedTime(output.toPath())
            } else {
                FileTime.fromMillis(0)
            }

            if (!output.exists() || inputFileTime > outputFileTime) {
                println("Regenerating JSON for '${output.relativeTo(outputDir)}'...")

                val cmd: List<String> = listOf(
                    "node",
                    script.absolutePath,
                    input.relativeTo(resources).path,
                    output.relativeTo(resources).path,
                )
                println("Running: '${cmd.joinToString(" ")}'")
                val process = ProcessBuilder(cmd).directory(resources).start()
                val ok = process.waitFor(10, TimeUnit.MINUTES)

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
            } else {
                println("Skipping '${output.relativeTo(outputDir)}'")
            }
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
    }
}
