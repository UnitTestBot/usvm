import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.Reader
import kotlin.time.Duration

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
    testImplementation(testFixtures(project(":usvm-ts-dataflow")))

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
        println("Running: '${cmd.joinToString(" ")}'")
        val result = ProcessUtil.run(cmd) {
            directory(resources)
        }
        if (result.stdout.isNotBlank()) {
            println("[STDOUT]:\n--------\n${result.stdout}\n--------")
        }
        if (result.stderr.isNotBlank()) {
            println("[STDERR]:\n--------\n${result.stderr}\n--------")
        }
        if (result.isTimeout) {
            println("Timeout!")
        }
        if (result.exitCode != 0) {
            println("Exit code: ${result.exitCode}")
        }
    }
}

tasks.register<Test>("testIr") {
    description = "Run tests using precomputed ArkIR JSON (no ArkAnalyzer)."
    group = "verification"
    useJUnitPlatform()

    systemProperty("ART_MODE", "ir")
    systemProperty(
        "ART_PATH",
        file("src/test/resources/article/examples.json").absolutePath
    )

    systemProperty("ART_NAME", "examples")
    filter {
        includeTestsMatching("org.usvm.article.ArticleExample*")
    }

    dependsOn("processTestResources")
}

tasks.register<Test>("genIrAndTest") {
    description = "Run tests without usage of precomputed IR."
    group = "verification"
    useJUnitPlatform()

    systemProperty("ART_MODE", "ts")
    systemProperty(
        "ART_PATH",
        file("src/test/resources/article/examples.ts").absolutePath
    )
    environment("ARKANALYZER_DIR", "${project.rootDir}/third_party/arkanalyzer")

    systemProperty("ART_NAME", "examples")
    filter {
        includeTestsMatching("org.usvm.article.ArticleExample*")
    }

    dependsOn("processTestResources")
}

object ProcessUtil {
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val isTimeout: Boolean, // true if the process was terminated due to timeout
    )

    fun run(
        command: List<String>,
        input: String? = null,
        timeout: Duration? = null,
        builder: ProcessBuilder.() -> Unit = {},
    ): Result {
        val reader = input?.reader() ?: "".reader()
        return run(command, reader, timeout, builder)
    }

    fun run(
        command: List<String>,
        input: Reader,
        timeout: Duration? = null,
        builder: ProcessBuilder.() -> Unit = {},
    ): Result {
        val process = ProcessBuilder(command).apply(builder).start()
        return communicate(process, input, timeout)
    }

    private fun communicate(
        process: Process,
        input: Reader,
        timeout: Duration? = null,
    ): Result {
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val scope = CoroutineScope(Dispatchers.IO)

        // Handle process input
        val stdinJob = scope.launch {
            process.outputStream.bufferedWriter().use { writer ->
                input.copyTo(writer)
            }
        }

        // Launch output capture coroutines
        val stdoutJob = scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdout.appendLine(it) }
            }
        }
        val stderrJob = scope.launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderr.appendLine(it) }
            }
        }

        // Wait for completion
        val isTimeout = if (timeout != null) {
            !process.waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)
        } else {
            process.waitFor()
            false
        }
        runBlocking {
            stdinJob.join()
            stdoutJob.join()
            stderrJob.join()
        }

        return Result(
            exitCode = process.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            isTimeout = isTimeout,
        )
    }
}
