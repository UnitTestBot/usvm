package org.usvm.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

fun getPythonFilesFromRoot(path: String): List<File> {
    return Files.find(
        Paths.get(path),
        Integer.MAX_VALUE,
        { _, fileAttr -> fileAttr.isRegularFile }
    ).map { it.toFile() }.filter { it.name.endsWith(".py") }.asSequence().toList()
}

fun getModulesFromFiles(root: String, files: List<File>): List<String> {
    return files.map {
        File(root).toURI().relativize(it.toURI()).path
            .removeSuffix(".py")
            .replace("/", ".")
            .replace("\\", ",")
    }
}
