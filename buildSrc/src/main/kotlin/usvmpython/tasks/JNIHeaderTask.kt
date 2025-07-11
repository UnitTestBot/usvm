package usvmpython.tasks

import gradle.kotlin.dsl.accessors._ed4eee7f6ae7e6c7746c5b956eb7e0be.main
import gradle.kotlin.dsl.accessors._ed4eee7f6ae7e6c7746c5b956eb7e0be.sourceSets
import org.glavo.javah.JavahTask
import org.gradle.api.Project
import usvmpython.getGeneratedHeadersPath
import usvmpython.CPYTHON_ADAPTER_CLASS

fun Project.generateJNIForCPythonAdapterTask() {
    val task = JavahTask()
    task.outputDir = getGeneratedHeadersPath().toPath()
    val classpath = sourceSets.main.get().runtimeClasspath
    classpath.files.forEach {
        task.addClassPath(it.toPath())
    }
    task.addClass(CPYTHON_ADAPTER_CLASS)
    task.run()
}
