import org.gradle.internal.jvm.Jvm
import usvmpython.*
import usvmpython.tasks.registerCPythonClean
import usvmpython.tasks.registerCPythonDebugBuild
import usvmpython.tasks.registerCPythonDistClean

// Example project: https://github.com/vladsoroka/GradleJniSample

plugins {
    `cpp-library`
}

if (cpythonIsActivated()) {
    val cpythonBuildPath = getCPythonBuildPath()
    val adapterHeaderPath = getGeneratedHeadersPath()

    val cpythonBuildDebugTask = registerCPythonDebugBuild()
    val cpythonCleanTask = registerCPythonClean()
    registerCPythonDistClean()

    tasks.clean {
        dependsOn(cpythonCleanTask)
    }

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

            compileTask.dependsOn(":$USVM_PYTHON_MAIN_MODULE:build")
            if (!compileTask.isOptimized) {
                compileTask.dependsOn(cpythonBuildDebugTask)
            } else {
                compileTask.dependsOn(cpythonBuildDebugTask)  // TODO
            }
        }

        if (isMacos) {
            binaries.whenElementFinalized {
                val linkTask = tasks.getByName("linkDebug") as LinkSharedLibrary
                val pythonLibPath = File(cpythonBuildPath, "lib/libpython3.11.dylib")
                linkTask.libs.from(pythonLibPath.path)
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

    /**
     * Task for debugging CPython patch without JNI-calls.
     * */
    if (!isWindows) {
        tasks.register<Exec>("cpython_check_compile") {
            dependsOn(cpythonBuildDebugTask)
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