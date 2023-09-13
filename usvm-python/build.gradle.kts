import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("usvm.kotlin-conventions")
}

// from GRADLE_USER_HOME/gradle.properties
val githubUser: String by project
val githubToken: String by project  // with permission to read packages
val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/tochilinak/UTBotJava")
        credentials {
            username = githubUser
            password = githubToken
        }
    }
}


dependencies {
    implementation(project(":usvm-core"))
    implementation("org.utbot:utbot-python-types:2023.09-SNAPSHOT")

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-bitwuzla:${Versions.ksmt}")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}

val samplesSourceDir = File(projectDir, "src/test/resources/samples")
val samplesBuildDir = File(project.buildDir, "samples_build")
val commonJVMArgs = listOf(
    "-Dsamples.build.path=${samplesBuildDir.canonicalPath}",
    "-Dsamples.sources.path=${samplesSourceDir.canonicalPath}",
    "-Xss50m"
)


val cpythonPath: String = File(childProjects["cpythonadapter"]!!.projectDir, "cpython").path
val cpythonBuildPath: String = File(childProjects["cpythonadapter"]!!.buildDir, "cpython_build").path
val cpythonAdapterBuildPath: String =
    File(childProjects["cpythonadapter"]!!.buildDir, "/lib/main/debug").path  // TODO: and release?
val pythonBinaryPath: String =
    if (!isWindows) {
        "$cpythonBuildPath/bin/python3"
    } else {
        File(cpythonBuildPath, "python_d.exe").canonicalPath
    }
val pythonDllsPath: String = File(cpythonBuildPath, "DLLs").path  // for Windows

val installMypyRunner = tasks.register<Exec>("installUtbotMypyRunner") {
    group = "samples"
    dependsOn(":usvm-python:cpythonadapter:linkDebug")
    inputs.dir(cpythonPath)
    if (!isWindows) {
        environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    }
    environment("PYTHONHOME" to cpythonBuildPath)
    commandLine(pythonBinaryPath, "-m", "ensurepip")
    commandLine(pythonBinaryPath, "-m", "pip", "install", "utbot-mypy-runner==0.2.15")
}

val buildSamples = tasks.register<JavaExec>("buildSamples") {
    dependsOn(installMypyRunner)
    inputs.dir(samplesSourceDir)
    outputs.dir(samplesBuildDir)
    group = "samples"
    classpath = sourceSets.test.get().runtimeClasspath
    args = listOf(samplesSourceDir.canonicalPath, samplesBuildDir.canonicalPath, pythonBinaryPath)
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("PYTHONHOME" to cpythonBuildPath)
    mainClass.set("org.usvm.runner.BuildSamplesKt")
}

fun registerCpython(task: JavaExec, debug: Boolean) = task.apply {
    if (debug)
        dependsOn(":usvm-python:cpythonadapter:linkDebug")
    else
        dependsOn(":usvm-python:cpythonadapter:linkRelease")
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
    environment("PYTHONHOME" to cpythonBuildPath)
}

tasks.register<JavaExec>("manualTestDebug") {
    group = "run"
    dependsOn(buildSamples)
    if (!isWindows) {
        registerCpython(this, debug = true)
        jvmArgs = commonJVMArgs + listOf("-Dlogback.configurationFile=logging/logback-debug.xml") //, "-Xcheck:jni")
    } else {
        environment("PYTHONHOME" to cpythonBuildPath)
        jvmArgs = commonJVMArgs + listOf("-Dlogback.configurationFile=logging/logback-debug.xml", "-Djava.library.path=$cpythonAdapterBuildPath")
        //val initialPath = environment["PATH"]!!
        environment("PATH", "$cpythonBuildPath;$pythonDllsPath")
    }
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.register<JavaExec>("manualTestDebugNoLogs") {
    group = "run"
    registerCpython(this, debug = true)
    dependsOn(buildSamples)
    jvmArgs = commonJVMArgs + "-Dlogback.configurationFile=logging/logback-info.xml"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.register<JavaExec>("manualTestRelease") {
    group = "run"
    registerCpython(this, debug = false)
    dependsOn(buildSamples)
    jvmArgs = commonJVMArgs + "-Dlogback.configurationFile=logging/logback-info.xml"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ManualTestKt")
}

tasks.test {
    val args = (commonJVMArgs + "-Dlogback.configurationFile=logging/logback-info.xml").toMutableList()
    // val args = (commonJVMArgs + "-Dlogback.configurationFile=logging/logback-debug.xml").toMutableList()
    if (!isWindows) {
        environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
        environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
    } else {
        args += "-Djava.library.path=$cpythonAdapterBuildPath"
        environment("PATH", "$cpythonBuildPath;$pythonDllsPath")
    }
    jvmArgs = args
    dependsOn(":usvm-python:cpythonadapter:linkDebug")
    dependsOn(buildSamples)
    environment("PYTHONHOME" to cpythonBuildPath)
}