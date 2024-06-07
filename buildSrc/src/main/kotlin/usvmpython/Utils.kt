package usvmpython

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import java.io.File

fun Project.cpythonIsActivated(): Boolean {
    if (!project.hasProperty(PROPERTY_FOR_CPYTHON_ACTIVATION))
        return false
    val prop = project.property(PROPERTY_FOR_CPYTHON_ACTIVATION) as? String
    return prop?.lowercase() == "true"
}

fun Project.getCPythonModule() =
    rootProject.findProject(CPYTHON_ADAPTER_MODULE)!!

fun Project.getUsvmPythonModule() =
    rootProject.findProject(USVM_PYTHON_MODULE)!!

fun Project.getCPythonBuildPath(): File {
    val cpythonModule = getCPythonModule()
    return cpythonModule.layout.buildDirectory.file("cpython_build").get().asFile
}

fun Project.getCPythonSourcePath(): File {
    val cpythonModule = getCPythonModule()
    return File(cpythonModule.layout.projectDirectory.asFile, "cpython")
}

fun Project.getCPythonAdapterBuildPath(): File {
    val cpythonModule = getCPythonModule()
    return cpythonModule.layout.buildDirectory.file("lib/main/debug").get().asFile
}

val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
val isMacos = Os.isFamily(Os.FAMILY_MAC)

fun Project.getWidowsBuildScriptPath(): File =
    File(getCPythonSourcePath(), "PCBuild/build.bat")

fun Project.getIncludePipFile(): File {
    val cpythonModule = getCPythonModule()
    return File(cpythonModule.projectDir, "include_pip_in_build")
}

fun Project.includePipInBuildLine(): String {
    val includePipFile = getIncludePipFile()
    val includePip = includePipFile.readText().trim() != "false"
    return if (includePip) {
        "--with-ensurepip=yes"
    } else {
        "--with-ensurepip=no"
    }
}

fun Project.getGeneratedHeadersPath(): File =
    getCPythonModule().layout.buildDirectory.file("adapter_include").get().asFile

fun Project.getSamplesSourceDir(): File =
    File(getUsvmPythonModule().projectDir, "src/test/resources/samples")

fun Project.getSamplesBuildDir(): File =
    getUsvmPythonModule().layout.buildDirectory.file("samples_build").get().asFile

fun Project.getApproximationsDir(): File =
    File(getUsvmPythonModule().projectDir, "python_approximations")

fun Project.getPythonBinaryPath(): File {
    val cpythonBuildPath = getCPythonBuildPath()
    return if (isWindows) {
        File(cpythonBuildPath, "python_d.exe")
    } else {
        File(cpythonBuildPath, "bin/python3")
    }
}