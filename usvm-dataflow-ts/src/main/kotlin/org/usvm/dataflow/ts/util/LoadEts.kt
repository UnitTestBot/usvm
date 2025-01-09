package org.usvm.dataflow.ts.util

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsFile
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.walk

/**
 * Load an [EtsFileDto] from a file.
 *
 * For example, `data/sample.json` can be loaded with:
 * ```
 * val dto: EtsFileDto = loadEtsFileDto(Path("data/sample.json"))
 * ```
 */
fun loadEtsFileDto(path: Path): EtsFileDto {
    require(path.extension == "json") { "File must have a '.json' extension: $path" }
    path.inputStream().use { stream ->
        return EtsFileDto.loadFromJson(stream)
    }
}

/**
 * Load an [EtsFile] from a file.
 *
 * For example, `data/sample.json` can be loaded with:
 * ```
 * val file: EtsFile = loadEtsFile(Path("data/sample.json"))
 * ```
 */
fun loadEtsFile(path: Path): EtsFile {
    val etsFileDto = loadEtsFileDto(path)
    return etsFileDto.toEtsFile()
}

/**
 * Load multiple [EtsFile]s from a directory.
 *
 * For example, all files in `data` can be loaded with:
 * ```
 * val files: Sequence<EtsFile> = loadMultipleEtsFilesFromDirectory(Path("data"))
 * ```
 */
fun loadMultipleEtsFilesFromDirectory(dirPath: Path): Sequence<EtsFile> {
    return dirPath.walk().filter { it.extension == "json" }.map { loadEtsFile(it) }
}
