import usvmpython.*
import usvmpython.tasks.registerBuildSamplesTask

plugins {
    id("usvm.kotlin-conventions")
    distribution
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":$USVM_PYTHON_MAIN_MODULE"))
    implementation(project(":$USVM_PYTHON_COMMONS_MODULE"))
    implementation("com.github.UnitTestBot:PythonTypesAPI:${Versions.pythonTypesAPIHash}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
}

tasks.test {
    onlyIf { cpythonIsActivated() }
}

tasks.jar {
    dependsOn(":usvm-util:jar")
    dependsOn(":usvm-core:jar")
    dependsOn(":$USVM_PYTHON_MAIN_MODULE:jar")
    dependsOn(":$USVM_PYTHON_COMMONS_MODULE:jar")
}

if (cpythonIsActivated()) {
    val samplesSourceDir = getSamplesSourceDir()
    val approximationsDir = getApproximationsDir()
    val samplesBuildDir = getSamplesBuildDir()
    val commonJVMArgs = listOf(
        "-Dsamples.build.path=${samplesBuildDir.canonicalPath}",
        "-Dsamples.sources.path=${samplesSourceDir.canonicalPath}",
        "-Dapproximations.path=${approximationsDir.canonicalPath}",
        "-Xss50m"
    )

    val cpythonBuildPath: String = getCPythonBuildPath().canonicalPath
    val cpythonAdapterBuildPath: String = getCPythonAdapterBuildPath().path
    val pythonDllsPath: String = File(cpythonBuildPath, "DLLs").path  // for Windows
    val buildSamples = registerBuildSamplesTask()

    fun registerCpython(task: JavaExec, debug: Boolean) = task.apply {
        if (debug)
            dependsOn(":usvm-python:cpythonadapter:linkDebug")
        else
            dependsOn(":usvm-python:cpythonadapter:linkRelease")
        dependsOn(":usvm-python:cpythonadapter:CPythonBuildDebug")
        environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
        environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
        environment("PYTHONHOME" to cpythonBuildPath)
    }

    tasks.register<JavaExec>("manualTestDebug") {
        group = "run"
        dependsOn(buildSamples)
        maxHeapSize = "2G"
        if (!isWindows) {
            registerCpython(this, debug = true)
            jvmArgs = commonJVMArgs + listOf("-Dlogback.configurationFile=logging/logback-debug.xml") //, "-Xcheck:jni")
        } else {
            environment("PYTHONHOME" to cpythonBuildPath)
            jvmArgs = commonJVMArgs + listOf(
                "-Dlogback.configurationFile=logging/logback-debug.xml",
                "-Djava.library.path=$cpythonAdapterBuildPath"
            )
            environment("PATH", "$cpythonBuildPath;$pythonDllsPath")
        }
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("ManualTestKt")
    }

    tasks.register<JavaExec>("manualTestDebugNoLogs") {
        group = "run"
        registerCpython(this, debug = true)
        dependsOn(buildSamples)
        maxHeapSize = "2G"
        if (!isWindows) {
            registerCpython(this, debug = true)
            jvmArgs = commonJVMArgs + listOf("-Dlogback.configurationFile=logging/logback-info.xml") //, "-Xcheck:jni")
        } else {
            environment("PYTHONHOME" to cpythonBuildPath)
            jvmArgs = commonJVMArgs + listOf(
                "-Dlogback.configurationFile=logging/logback-info.xml",
                "-Djava.library.path=$cpythonAdapterBuildPath"
            )
            environment("PATH", "$cpythonBuildPath;$pythonDllsPath")
        }
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("ManualTestKt")
    }

    tasks.test {
        maxHeapSize = "2G"
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
        dependsOn(":usvm-python:cpythonadapter:CPythonBuildDebug")
        dependsOn(buildSamples)
        environment("PYTHONHOME" to cpythonBuildPath)
    }

    distributions {
        main {
            contents {
                into("lib") {
                    from(cpythonAdapterBuildPath)
                    from(fileTree(approximationsDir).exclude("**/__pycache__/**").exclude("**/*.iml"))
                }
                into("cpython") {
                    from(fileTree(cpythonBuildPath).exclude("**/__pycache__/**"))
                }
                into("jar") {
                    from(tasks.jar)
                }
            }
        }
    }

    tasks.jar {
        dependsOn(":$USVM_PYTHON_MAIN_MODULE:jar")
        manifest {
            attributes(
                "Main-Class" to RUNNER_ENTRY_POINT,
            )
        }
        val dependencies = configurations
            .runtimeClasspath
            .get()
            .map(::zipTree)
        from(dependencies)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.distTar.get().dependsOn(":$CPYTHON_ADAPTER_MODULE:CPythonBuildDebug")
    tasks.distZip.get().dependsOn(":$CPYTHON_ADAPTER_MODULE:CPythonBuildDebug")
    tasks.distTar.get().dependsOn(":$CPYTHON_ADAPTER_MODULE:linkDebug")
    tasks.distZip.get().dependsOn(":$CPYTHON_ADAPTER_MODULE:linkDebug")
}
