package org.usvm.runner

import org.usvm.language.StructuredPyProgram
import org.utpython.types.mypy.MypyBuildDirectory
import org.utpython.types.mypy.readMypyInfoBuild
import java.io.File

object SamplesBuild {
    private val mypyBuildRoot = System.getProperty("samples.build.path")!!
    private val sourcesRoot = System.getProperty("samples.sources.path")!!
    private val mypyDirectory = MypyBuildDirectory(File(mypyBuildRoot), setOf(sourcesRoot))
    val mypyBuild = readMypyInfoBuild(mypyDirectory)
    val program = StructuredPyProgram(setOf(File(sourcesRoot)))
}
