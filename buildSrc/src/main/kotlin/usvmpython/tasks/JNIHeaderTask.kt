package usvmpython.tasks

import org.glavo.javah.JavahTask
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import usvmpython.getGeneratedHeadersPath
import usvmpython.CPYTHON_ADAPTER_CLASS


fun Project.generateJNIForCPythonAdapterTask() {
    val task = JavahTask()
    task.outputDir = getGeneratedHeadersPath().toPath()
    val classpath = extensions.getByType<SourceSetContainer>().getByName("main").runtimeClasspath
    classpath.files.forEach {
        task.addClassPath(it.toPath())
    }
    task.addClass(CPYTHON_ADAPTER_CLASS)
    task.run()
}
