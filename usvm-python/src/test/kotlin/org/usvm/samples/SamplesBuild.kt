package org.usvm.samples

import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.StructuredPythonProgram
import org.usvm.language.types.pythonInt
import org.utbot.python.newtyping.mypy.MypyBuildDirectory
import org.utbot.python.newtyping.mypy.readMypyInfoBuild
import java.io.File

object SamplesBuild {
    private val mypyBuildRoot = System.getProperty("samples.build.path")!!
    private val sourcesRoot = System.getProperty("samples.sources.path")!!
    private val mypyDirectory = MypyBuildDirectory(File(mypyBuildRoot), setOf(sourcesRoot))
    val mypyBuild = readMypyInfoBuild(mypyDirectory)
    val program = StructuredPythonProgram(setOf(File(sourcesRoot)))

    init {
        val callable = PythonUnpinnedCallable.constructCallableFromName(
            listOf(pythonInt),
            "many_branches",
            "SimpleExample"
        )
        println("PINNED CALLABLE:")
        kotlin.runCatching { println(program.pinCallable(callable)) }.onFailure { println(it) }
    }
}