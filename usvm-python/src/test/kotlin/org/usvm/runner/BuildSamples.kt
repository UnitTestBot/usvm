package org.usvm.runner

import org.usvm.utils.getModulesFromFiles
import org.usvm.utils.getPythonFilesFromRoot
import org.utpython.types.mypy.MypyBuildDirectory
import org.utpython.types.mypy.buildMypyInfo
import java.io.File

/**
 * This is supposed to be called only from Gradle task `buildSamples`.
 * Not designed for human usage.
 * */

fun main(args: Array<String>) {
    val inputPath = args[0]
    val requiredPath = args[1]
    val pythonPath = args[2]
    val root = File(requiredPath)
    root.mkdirs()
    val mypyBuildDir = MypyBuildDirectory(root, setOf(inputPath))
    val files = getPythonFilesFromRoot(inputPath)
    val modules = getModulesFromFiles(inputPath, files)
    buildMypyInfo(pythonPath, files.map { it.canonicalPath }, modules, mypyBuildDir)
}
