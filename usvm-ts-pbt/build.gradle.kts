plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(Libs.jacodb_ets)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(Libs.logback)
}

val fastCheckAdapterDir = layout.projectDirectory.dir("fast-check-adapter")
val npmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val installFastCheckAdapter = tasks.register<Exec>("installFastCheckAdapter") {
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "ci", "--ignore-scripts")
    inputs.files(
        fastCheckAdapterDir.file("package.json"),
        fastCheckAdapterDir.file("package-lock.json"),
    )
    outputs.dir(fastCheckAdapterDir.dir("node_modules"))
}

val testFastCheckAdapter = tasks.register<Exec>("testFastCheckAdapter") {
    dependsOn(installFastCheckAdapter)
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "test")
    inputs.dir(fastCheckAdapterDir.dir("src"))
    inputs.dir(fastCheckAdapterDir.dir("test"))
}

tasks.test {
    dependsOn(installFastCheckAdapter)
}

tasks.check {
    dependsOn(testFastCheckAdapter)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
