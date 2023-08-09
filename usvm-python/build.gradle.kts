plugins {
    id("usvm.kotlin-conventions")
}


dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-python:utbot-python-types"))

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-bitwuzla:${Versions.ksmt}")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}

val cpythonBuildPath = "${childProjects["cpythonadapter"]!!.buildDir}/cpython_build"
val cpythonAdapterBuildPath = "${childProjects["cpythonadapter"]!!.buildDir}/lib/main/debug"  // TODO: and release?

fun registerCpython(task: JavaExec, debug: Boolean) = task.apply {
    if (debug)
        dependsOn(":usvm-python:cpythonadapter:linkDebug")
    else
        dependsOn(":usvm-python:cpythonadapter:linkRelease")
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
}

tasks.register<JavaExec>("manualTestDebug") {
    group = "run"
    registerCpython(this, debug = true)
    jvmArgs = listOf("-Dlogback.configurationFile=logging/logback-debug.xml") //, "-Xcheck:jni")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.register<JavaExec>("manualTestDebugNoLogs") {
    group = "run"
    registerCpython(this, debug = true)
    jvmArgs = listOf("-Dlogback.configurationFile=logging/logback-info.xml")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.register<JavaExec>("manualTestRelease") {
    group = "run"
    registerCpython(this, debug = false)
    jvmArgs = listOf("-Dlogback.configurationFile=logging/logback-info.xml")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

val samplesSourceDir = File(projectDir, "src/test/resources/samples")
val samplesBuildDir = File(project.buildDir, "samples_build")

// temporary
val installMypyRunner = tasks.register<Exec>("installUtbotMypyRunner") {
    group = "samples"
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("PYTHONHOME" to cpythonBuildPath)
    commandLine("$cpythonBuildPath/bin/python3", "-m", "ensurepip")
    commandLine("$cpythonBuildPath/bin/python3", "-m", "pip", "install", "utbot-mypy-runner==0.2.11")
}

tasks.register<JavaExec>("buildSamples") {
    dependsOn(installMypyRunner)
    group = "samples"
    classpath = sourceSets.test.get().runtimeClasspath
    args = listOf(samplesSourceDir.canonicalPath, samplesBuildDir.canonicalPath, "$cpythonBuildPath/bin/python3")
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("PYTHONHOME" to cpythonBuildPath)
    mainClass.set("BuildSamplesKt")
}

tasks.test {
    jvmArgs = listOf(
        "-Dlogback.configurationFile=logging/logback-info.xml",
        "-Dsamples.build.path="
    )
    dependsOn(":usvm-python:cpythonadapter:linkDebug")
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
}