import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.attribute.FileTime

plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    api(project(":usvm-dataflow"))
    api(project(":usvm-util"))

    api(Libs.jacodb_api_common)
    api(Libs.jacodb_ets)
    implementation(Libs.jacodb_taint_configuration)
    implementation(Libs.kotlinx_collections)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(testFixtures(Libs.jacodb_ets))
    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
    testImplementation(Libs.kotlinx_serialization_core)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
        allWarningsAsErrors = false
    }
}

tasks.named<Test>("test") {
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
                val process = ProcessBuilder(cmd).directory(resources).start();
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
