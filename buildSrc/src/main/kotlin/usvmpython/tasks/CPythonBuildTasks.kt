package usvmpython.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import usvmpython.*
import java.io.File


private fun Project.registerCPythonDebugConfiguration(): TaskProvider<Exec>? {
    if (isWindows) {
        return null
    }
    val cpythonSourcePath = getCPythonSourcePath()
    val cpythonBuildPath = getCPythonBuildPath()
    return tasks.register<Exec>(CPYTHON_BUILD_DEBUG_CONFIGURATION) {
        group = CPYTHON_GROUP_NAME
        workingDir = cpythonSourcePath
        val includePipFile = getIncludePipFile()
        inputs.file(includePipFile)
        outputs.file("$cpythonSourcePath/Makefile")
        val pipLine = includePipInBuildLine()
        doFirst {
            println("Pip line: $pipLine")
        }

        val openssl = if (project.hasProperty(PROPERTY_FOR_CPYTHON_SSL_PATH)) {
            "--with-openssl=${project.property(PROPERTY_FOR_CPYTHON_SSL_PATH)}"
        } else {
            ""
        }

        commandLine(
            "$cpythonSourcePath/configure",
            "--enable-shared",
            "--without-static-libpython",
            pipLine,
            "--prefix=$cpythonBuildPath",
            "--disable-test-modules",
            "--with-assertions",
            openssl
        )

        /*
        * Release configuration:

            commandLine(
                "$cpythonPath/configure",
                "--enable-shared",
                "--without-static-libpython",
                "--with-ensurepip=yes",
                "--prefix=$cpythonBuildPath",
                "--disable-test-modules",
                "--enable-optimizations"
            )

        */
    }
}

fun Project.registerCPythonDebugBuild(): TaskProvider<Exec> {
    val configCPythonDebug = registerCPythonDebugConfiguration()
    val cpythonSourcePath = getCPythonSourcePath()
    val cpythonBuildPath = getCPythonBuildPath()
    val windowsBuildScript = getWidowsBuildScriptPath()

    return tasks.register<Exec>(CPYTHON_BUILD_DEBUG) {
        configCPythonDebug?.let { dependsOn(it) }
        group = CPYTHON_GROUP_NAME
        inputs.dir(File(cpythonSourcePath, "Objects"))
        inputs.dir(File(cpythonSourcePath, "Python"))
        inputs.dir(File(cpythonSourcePath, "Include"))
        workingDir = cpythonSourcePath

        if (!isWindows) {
            outputs.dirs("$cpythonBuildPath/lib", "$cpythonBuildPath/include", "$cpythonBuildPath/bin")
            commandLine("make")
            commandLine("make", "install")

        } else {
            outputs.dirs(cpythonBuildPath)
            commandLine(
                windowsBuildScript.canonicalPath,
                "-c",
                "Debug",
                "-t",
                "Build",
                "--generate-layout",
                cpythonBuildPath
            )
        }
    }
}

fun Project.registerCPythonClean(): TaskProvider<Exec> {
    val cpythonSourcePath = getCPythonSourcePath()
    val windowsBuildScript = getWidowsBuildScriptPath()
    return tasks.register<Exec>("CPythonClean") {
        group = CPYTHON_GROUP_NAME
        workingDir = cpythonSourcePath
        if (!isWindows) {
            if (File(cpythonSourcePath, "Makefile").exists()) {
                commandLine("make", "clean")
            } else {
                commandLine("echo", "CPython Configuration is already clean")
            }
        } else {
            commandLine(windowsBuildScript.canonicalPath, "-t", "Clean")
        }
    }
}

fun Project.registerCPythonDistClean(): TaskProvider<Exec> {
    val cpythonSourcePath = getCPythonSourcePath()
    val windowsBuildScript = getWidowsBuildScriptPath()
    return tasks.register<Exec>("CPythonDistclean") {
        group = CPYTHON_GROUP_NAME
        workingDir = cpythonSourcePath
        if (!isWindows) {
            if (File(cpythonSourcePath, "Makefile").exists()) {
                commandLine("make", "distclean")
            } else {
                commandLine("echo", "CPython Configuration is already clean")
            }
        } else {
            commandLine(windowsBuildScript.canonicalPath, "-t", "CleanAll")
        }
    }
}
