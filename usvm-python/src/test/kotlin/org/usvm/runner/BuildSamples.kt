package org.usvm.runner

import org.utbot.python.newtyping.mypy.MypyBuildDirectory
import org.utbot.python.newtyping.mypy.buildMypyInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

fun main(args: Array<String>) {
    val inputPath = args[0]
    val requiredPath = args[1]
    val pythonPath = args[2]
    val root = File(requiredPath)
    root.mkdirs()
    val mypyBuildDir = MypyBuildDirectory(root, setOf(inputPath))
    val files = Files.find(
        Paths.get(inputPath),
        Integer.MAX_VALUE,
        { _, fileAttr -> fileAttr.isRegularFile }
    ).map { it.toFile() }.filter { it.name.endsWith(".py") }.asSequence().toList()
    val inputRoot = File(inputPath)
    val modules = files.map {
        inputRoot.toURI().relativize(it.toURI()).path
            .removeSuffix(".py")
            .replace("/", ".")
            .replace("\\", ",")
    }
    buildMypyInfo(pythonPath, files.map { it.canonicalPath }, modules, mypyBuildDir)
}