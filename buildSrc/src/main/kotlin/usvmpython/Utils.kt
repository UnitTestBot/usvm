package usvmpython

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import java.io.File

fun Project.cpythonIsActivated(): Boolean {
    if (!hasProperty(PROPERTY_FOR_CPYTHON_ACTIVATION))
        return false
    val prop = property(PROPERTY_FOR_CPYTHON_ACTIVATION) as? String
    return prop?.lowercase() == "true"
}

fun Project.getCPythonModule() =
    rootProject.findProject(CPYTHON_ADAPTER_MODULE)!!

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