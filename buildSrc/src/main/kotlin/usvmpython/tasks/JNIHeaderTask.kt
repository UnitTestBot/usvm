package usvmpython.tasks

import gradle.kotlin.dsl.accessors._f82704f7081d2131104652c38785c0ac.main
import gradle.kotlin.dsl.accessors._f82704f7081d2131104652c38785c0ac.sourceSets
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
