package org.usvm.runner

import org.usvm.utils.getModulesFromFiles
import org.usvm.utils.getPythonFilesFromRoot
import org.utbot.python.newtyping.mypy.MypyBuildDirectory
import org.utbot.python.newtyping.mypy.buildMypyInfo
import java.io.File

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