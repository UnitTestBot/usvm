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

val samplesSourceDir = File(projectDir, "src/test/resources/samples")
val samplesBuildDir = File(project.buildDir, "samples_build")
val samplesJVMArgs = listOf(
    "-Dsamples.build.path=${samplesBuildDir.canonicalPath}",
    "-Dsamples.sources.path=${samplesSourceDir.canonicalPath}"
)

// temporary
val installMypyRunner = tasks.register<Exec>("installUtbotMypyRunner") {
    group = "samples"
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("PYTHONHOME" to cpythonBuildPath)
    commandLine("$cpythonBuildPath/bin/python3", "-m", "ensurepip")
    commandLine("$cpythonBuildPath/bin/python3", "-m", "pip", "install", "utbot-mypy-runner==0.2.11")
}

val buildSamples = tasks.register<JavaExec>("buildSamples") {
    dependsOn(installMypyRunner)
    group = "samples"
    classpath = sourceSets.test.get().runtimeClasspath
    args = listOf(samplesSourceDir.canonicalPath, samplesBuildDir.canonicalPath, "$cpythonBuildPath/bin/python3")
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("PYTHONHOME" to cpythonBuildPath)
    mainClass.set("org.usvm.runner.BuildSamplesKt")
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
    dependsOn(buildSamples)
    jvmArgs = samplesJVMArgs + listOf("-Dlogback.configurationFile=logging/logback-debug.xml") //, "-Xcheck:jni")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.register<JavaExec>("manualTestDebugNoLogs") {
    group = "run"
    registerCpython(this, debug = true)
    dependsOn(buildSamples)
    jvmArgs = samplesJVMArgs + "-Dlogback.configurationFile=logging/logback-info.xml"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.register<JavaExec>("manualTestRelease") {
    group = "run"
    registerCpython(this, debug = false)
    dependsOn(buildSamples)
    jvmArgs = samplesJVMArgs + "-Dlogback.configurationFile=logging/logback-info.xml"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.test {
    jvmArgs = samplesJVMArgs + "-Dlogback.configurationFile=logging/logback-info.xml"
    dependsOn(":usvm-python:cpythonadapter:linkDebug")
    dependsOn(buildSamples)
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
}