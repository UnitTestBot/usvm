import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.internal.jvm.Jvm

// Example project: https://github.com/vladsoroka/GradleJniSample

plugins {
    `cpp-library`
}

val cpythonActivated: String? by project
val cpythonActivatedFlag = cpythonActivated?.toLowerCase() == "true"

if (cpythonActivatedFlag) {
    val cpythonPath: String = File(projectDir, "cpython").canonicalPath
    val cpythonBuildPath: String = File(project.buildDir.path, "cpython_build").canonicalPath
    val cpythonTaskGroup = "cpython"
    val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    val windowsBuildScript = File(cpythonPath, "PCBuild/build.bat")

    val configCPythonDebug =
        if (!isWindows) {
            tasks.register<Exec>("CPythonBuildConfigurationDebug") {
                group = cpythonTaskGroup
                workingDir = File(cpythonPath)
                val includePipFile = File(projectDir, "include_pip_in_build")
                inputs.file(includePipFile)
                outputs.file("$cpythonPath/Makefile")
                val pipLine = if (includePipFile.readText().trim() == "false") {
                    "--with-ensurepip=no"
                } else {
                    "--with-ensurepip=yes"
                }
                doFirst {
                    println("Pip line: $pipLine")
                }
                commandLine(
                    "$cpythonPath/configure",
                    "--enable-shared",
                    "--without-static-libpython",
                    pipLine,
                    "--prefix=$cpythonBuildPath",
                    "--disable-test-modules",
                    "--with-assertions"
                )
            }
        } else {
            null
        }

    /*
    val configCPythonRelease =
        if (!isWindows) {
            tasks.register<Exec>("CPythonBuildConfigurationRelease") {
                group = cpythonTaskGroup
                workingDir = File(cpythonPath)
                outputs.file("$cpythonPath/Makefile")
                commandLine(
                    "$cpythonPath/configure",
                    "--enable-shared",
                    "--without-static-libpython",
                    "--with-ensurepip=yes",
                    "--prefix=$cpythonBuildPath",
                    "--disable-test-modules",
                    "--enable-optimizations"
                )
            }
        } else {
            null
        }
     */

    val cpythonBuildDebug = tasks.register<Exec>("CPythonBuildDebug") {
        group = cpythonTaskGroup
        inputs.dir(File(cpythonPath, "Objects"))
        inputs.dir(File(cpythonPath, "Python"))
        inputs.dir(File(cpythonPath, "Include"))
        workingDir = File(cpythonPath)
        if (!isWindows) {
            dependsOn(configCPythonDebug!!)
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

    val adapterHeaderPath = "${project.buildDir.path}/adapter_include"

    library {
        binaries.configureEach {
            val compileTask = compileTask.get()
            compileTask.includes.from("${Jvm.current().javaHome}/include")

            val osFamily = targetPlatform.targetMachine.operatingSystemFamily
            if (osFamily.isMacOs) {
                compileTask.includes.from("${Jvm.current().javaHome}/include/darwin")
            } else if (osFamily.isLinux) {
                compileTask.includes.from("${Jvm.current().javaHome}/include/linux")
            } else if (osFamily.isWindows) {
                compileTask.includes.from("${Jvm.current().javaHome}/include/win32")
            }

            compileTask.includes.from(adapterHeaderPath)
            compileTask.includes.from("src/main/c/include")
            compileTask.source.from(fileTree("src/main/c"))
            compileTask.source.from(fileTree(adapterHeaderPath).filter { it.name.endsWith(".c") })
            if (!isWindows) {
                compileTask.includes.from("$cpythonBuildPath/include/python3.11")
                compileTask.compilerArgs.addAll(
                    listOf(
                        "-x",
                        "c",
                        "-std=c11",
                        "-L$cpythonBuildPath/lib",
                        "-lpython3.11",
                        "-Werror",
                        "-Wall"
                    )
                )
            } else {
                compileTask.includes.from(File(cpythonBuildPath, "include").canonicalPath)
                compileTask.compilerArgs.addAll(listOf("/TC"))
            }

            compileTask.dependsOn(":usvm-python:usvm-python-main:compileJava")
            if (!compileTask.isOptimized) {
                compileTask.dependsOn(cpythonBuildDebug)
            } else {
                compileTask.dependsOn(cpythonBuildDebug)  // TODO
            }
        }

        if (isWindows) {
            binaries.whenElementFinalized {
                val linkTask = tasks.getByName("linkDebug") as LinkSharedLibrary
                val pythonLibPath = File(cpythonBuildPath, "libs/")
                linkTask.linkerArgs.addAll("/LIBPATH:${pythonLibPath.path}")
            }
        }
    }

    val cpythonClean = tasks.register<Exec>("CPythonClean") {
        group = cpythonTaskGroup
        workingDir = File(cpythonPath)
        if (!isWindows) {
            if (File(cpythonPath, "Makefile").exists()) {
                commandLine("make", "clean")
            } else {
                commandLine("echo", "CPython Configuration is already clean")
            }
        } else {
            commandLine(windowsBuildScript.canonicalPath, "-t", "Clean")
        }
    }

    tasks.register<Exec>("CPythonDistclean") {
        group = cpythonTaskGroup
        workingDir = File(cpythonPath)
        if (!isWindows) {
            if (File(cpythonPath, "Makefile").exists()) {
                commandLine("make", "distclean")
            } else {
                commandLine("echo", "CPython Configuration is already clean")
            }
        } else {
            commandLine(windowsBuildScript.canonicalPath, "-t", "CleanAll")
        }
    }

    tasks.clean {
        dependsOn(cpythonClean)
    }

    if (!isWindows) {
        tasks.register<Exec>("cpython_check_compile") {
            dependsOn(cpythonBuildDebug)
            workingDir = File("${projectDir.path}/cpython_check")
            commandLine(
                "gcc",
                "-std=c11",
                "-I$cpythonBuildPath/include/python3.11",
                "sample_handler.c",
                "-o",
                "check",
                "-L$cpythonBuildPath/lib",
                "-lpython3.11"
            )
        }
    }
}