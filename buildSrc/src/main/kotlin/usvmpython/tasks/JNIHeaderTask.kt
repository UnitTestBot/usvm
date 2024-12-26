package usvmpython.tasks

import org.glavo.javah.JavahTask
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.get
import usvmpython.CPYTHON_ADAPTER_CLASS
import usvmpython.getGeneratedHeadersPath

fun Project.generateJNIForCPythonAdapterTask() {
    val task = JavahTask()
    task.outputDir = getGeneratedHeadersPath().toPath()
    val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
    val classpath = sourceSets["main"].runtimeClasspath
    classpath.files.forEach {
        task.addClassPath(it.toPath())
    }
    task.addClass(CPYTHON_ADAPTER_CLASS)
    task.run()
}
