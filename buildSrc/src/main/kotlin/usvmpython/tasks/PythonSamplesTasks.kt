package usvmpython.tasks

import gradle.kotlin.dsl.accessors._466a692754d3da37fc853e1c7ad8ae1e.sourceSets
import gradle.kotlin.dsl.accessors._466a692754d3da37fc853e1c7ad8ae1e.test
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.environment
import org.gradle.kotlin.dsl.register
import usvmpython.*
import java.io.File

private fun Project.registerInstallMypyRunnerTask(): TaskProvider<Exec> {
    val pythonBinaryPath = getPythonBinaryPath()
    val cpythonSourcePath = getCPythonSourcePath()
    val cpythonBuildPath = getCPythonBuildPath()
    val cpythonAdapterBuildPath = getCPythonAdapterBuildPath()
    return tasks.register<Exec>(INSTALL_MYPY_RUNNER_TASK) {
        group = SAMPLE_GROUP_NAME
        dependsOn(":$CPYTHON_ADAPTER_MODULE:linkDebug")
        dependsOn(":$CPYTHON_ADAPTER_MODULE:CPythonBuildDebug")
        inputs.dir(cpythonSourcePath)
        if (isWindows) {
            outputs.dir(File(cpythonBuildPath, "Lib/site-packages/utbot_mypy_runner"))
        }
        if (!isWindows) {
            environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
        }
        environment("PYTHONHOME" to cpythonBuildPath)
        commandLine(pythonBinaryPath, "-m", "ensurepip")
        commandLine(pythonBinaryPath, "-m", "pip", "install", "utbot-mypy-runner==${Versions.utbotMypyRunner}")
    }
}

fun Project.registerBuildSamplesTask(): TaskProvider<JavaExec> {
    val installMypyRunner = registerInstallMypyRunnerTask()
    val samplesSourceDir = getSamplesSourceDir()
    val samplesBuildDir = getSamplesBuildDir()
    val cpythonBuildPath = getCPythonBuildPath()
    val pythonBinaryPath = getPythonBinaryPath().canonicalPath
    val cpythonAdapterBuildPath = getCPythonAdapterBuildPath()
    return tasks.register<JavaExec>(BUILD_SAMPLES_TASK) {
        group = SAMPLE_GROUP_NAME
        dependsOn(installMypyRunner)
        inputs.files(fileTree(samplesSourceDir).exclude("**/__pycache__/**"))
        outputs.dir(samplesBuildDir)
        classpath = sourceSets.test.get().runtimeClasspath
        args = listOf(samplesSourceDir.canonicalPath, samplesBuildDir.canonicalPath, pythonBinaryPath)
        environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
        environment("PYTHONHOME" to cpythonBuildPath)
        mainClass.set(BUILD_SAMPLES_ENTRY_POINT)
    }
}
