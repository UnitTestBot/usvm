import usvmpython.USVM_PYTHON_ANNOTATIONS_MODULE
import usvmpython.USVM_PYTHON_COMMONS_MODULE
import usvmpython.getGeneratedHeadersPath
import usvmpython.tasks.generateJNIForCPythonAdapterTask

plugins {
    id("usvm.kotlin-conventions")
}

val headerPath = getGeneratedHeadersPath()

tasks.compileJava {
    // to suppress "No processor claimed any of these annotations: org.jetbrains.annotations.Nullable,org.jetbrains.annotations.NotNull"
    options.compilerArgs.add("-Xlint:-processing")
    options.compilerArgs.add("-AheaderPath=${headerPath.canonicalPath}")
    outputs.dirs(headerPath)
}

tasks.build {
    doLast {
        generateJNIForCPythonAdapterTask()
    }
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(mapOf("path" to ":$USVM_PYTHON_ANNOTATIONS_MODULE")))
    implementation(project(mapOf("path" to ":$USVM_PYTHON_COMMONS_MODULE")))
    annotationProcessor(project(":$USVM_PYTHON_ANNOTATIONS_MODULE"))

    implementation(Libs.python_types_api)

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}
