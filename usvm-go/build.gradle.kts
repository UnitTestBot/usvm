import io.gitlab.arturbosch.detekt.Detekt
import java.time.Duration

plugins {
    kotlin("jvm")
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(Libs.jacodb_go)
    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_collections)
    implementation(Libs.ksmt_yices)
    testImplementation(Libs.logback)
}

val generatedGo = layout.buildDirectory.dir("generated/go")
val goSources = fileTree("src/main/go") { include("**/*.go", "go.mod", "go.sum") }

fun registerGoExport(taskName: String, packageName: String, directory: String) = tasks.register<Exec>(taskName) {
    group = "verification"
    description = "Exports $packageName as SSA JSON."
    workingDir(layout.projectDirectory.dir("src/main/go"))
    environment("GOTOOLCHAIN", "go1.22.3")
    inputs.files(goSources)
    outputs.dir(generatedGo.map { it.dir(directory) })
    commandLine("go", "run", ".", "-packageName", packageName, "-dump-ssa=false",
        "-output-dir", generatedGo.get().dir(directory).asFile.absolutePath)
}

val generateGoIr = registerGoExport(taskName = "generateGoIr", packageName = "usvm/examples", directory = "examples")
val generateGoRegressions = registerGoExport(taskName = "generateGoRegressions", packageName = "usvm/regressions", directory = "regressions")
val generateGoImports = registerGoExport(taskName = "generateGoImports", packageName = "usvm/examples/imports", directory = "imports")

val generateGoOracle by tasks.registering(Exec::class) {
    group = "verification"
    description = "Executes the regression samples with native Go."
    workingDir(layout.projectDirectory.dir("src/main/go"))
    environment("GOTOOLCHAIN", "go1.22.3")
    environment("USVM_GO_ORACLE_FILE", generatedGo.get().file("native-oracle.json").asFile.absolutePath)
    inputs.files(goSources)
    outputs.file(generatedGo.map { it.file("native-oracle.json") })
    doFirst { generatedGo.get().asFile.mkdirs() }
    commandLine("go", "test", "./regressions", "-run", "TestNativeOracle", "-count=1")
}

val compileGoReplay by tasks.registering(Exec::class) {
    group = "verification"
    description = "Compiles the native witness replay runner."
    workingDir(layout.projectDirectory.dir("src/main/go"))
    environment("GOTOOLCHAIN", "go1.22.3")
    inputs.files(goSources)
    outputs.file(generatedGo.map { it.file("native-replay.test") })
    commandLine("go", "test", "-c", "-o", generatedGo.get().file("native-replay.test").asFile.absolutePath, "./regressions")
}

tasks.withType<Test>().configureEach {
    systemProperty("usvm.go.generatedDir", generatedGo.get().asFile.absolutePath)
    timeout.set(Duration.ofMinutes(15))
}

tasks.test {
    dependsOn(generateGoIr, generateGoRegressions, generateGoOracle, compileGoReplay)
}

tasks.named<Test>("manualTest") {
    dependsOn(generateGoIr)
}

tasks.withType<Detekt>().configureEach {
    ignoreFailures = false
    setExcludes(listOf("**/resources/**", "**/build/**", "**/generated/**"))
}
