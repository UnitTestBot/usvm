package usvmpython.tasks

import gradle.kotlin.dsl.accessors._466a692754d3da37fc853e1c7ad8ae1e.main
import gradle.kotlin.dsl.accessors._466a692754d3da37fc853e1c7ad8ae1e.sourceSets
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
