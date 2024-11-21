import usvmpython.*
import usvmpython.tasks.javaArgumentsForPython
import usvmpython.tasks.pythonEnvironmentVariables
import usvmpython.tasks.registerBuildSamplesTask

plugins {
    id("usvm.kotlin-conventions")
    distribution
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":$USVM_PYTHON_MAIN_MODULE"))
    implementation(project(":$USVM_PYTHON_COMMONS_MODULE"))
    implementation(project(":$USVM_PYTHON_ANNOTATIONS_MODULE"))
    implementation(Libs.python_types_api)
    implementation(Libs.logback)
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
    val buildSamples = registerBuildSamplesTask()

    tasks.register<JavaExec>(MANUAL_TEST_DEBUG_TASK) {
        dependsOn(buildSamples)
        dependsOn(":$CPYTHON_ADAPTER_MODULE:linkDebug")
        classpath = sourceSets.test.get().runtimeClasspath
        group = MANUAL_RUN_GROUP_NAME
        maxHeapSize = "2G"
        jvmArgs = javaArgumentsForPython(debugLog = true)
        environment(pythonEnvironmentVariables())
        mainClass.set(MANUAL_TEST_ENTRY)
    }

    tasks.register<JavaExec>(MANUAL_TEST_DEBUG_NO_LOGS_TASK) {
        dependsOn(buildSamples)
        dependsOn(":$CPYTHON_ADAPTER_MODULE:linkDebug")
        classpath = sourceSets.test.get().runtimeClasspath
        group = MANUAL_RUN_GROUP_NAME
        maxHeapSize = "2G"
        jvmArgs = javaArgumentsForPython(debugLog = false)
        environment(pythonEnvironmentVariables())
        mainClass.set(MANUAL_TEST_ENTRY)
    }

    tasks.test {
        maxHeapSize = "2G"
        jvmArgs = javaArgumentsForPython(debugLog = false)
        dependsOn(buildSamples)
        dependsOn(":$CPYTHON_ADAPTER_MODULE:linkDebug")
        environment(pythonEnvironmentVariables())
    }

    distributions {
        main {
            contents {
                into("lib") {
                    from(getCPythonAdapterBuildPath())
                    from(fileTree(getApproximationsDir()).exclude("**/__pycache__/**").exclude("**/*.iml"))
                }
                into("cpython") {
                    from(fileTree(getCPythonBuildPath()).exclude("**/__pycache__/**"))
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
