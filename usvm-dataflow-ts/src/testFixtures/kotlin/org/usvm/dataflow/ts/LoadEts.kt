package org.usvm.dataflow.ts

import mu.KotlinLogging
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

private val logger = KotlinLogging.logger {}

/**
 * Load an [EtsFileDto] from a resource file.
 *
 * For example, `resources/ets/sample.json` can be loaded with:
 * ```
 * val dto: EtsFileDto = loadEtsFileDtoFromResource("/ets/sample.json")
 * ```
 */
fun loadEtsFileDtoFromResource(jsonPath: String): EtsFileDto {
    logger.debug { "Loading EtsIR from resource: '$jsonPath'" }
    require(jsonPath.endsWith(".json")) { "File must have a '.json' extension: '$jsonPath'" }
    getResourceStream(jsonPath).use { stream ->
        return EtsFileDto.loadFromJson(stream)
    }
}

/**
 * Load an [EtsFile] from a resource file.
 *
 * For example, `resources/ets/sample.json` can be loaded with:
 * ```
 * val file: EtsFile = loadEtsFileFromResource("/ets/sample.json")
 * ```
 */
fun loadEtsFileFromResource(jsonPath: String): EtsFile {
    val etsFileDto = loadEtsFileDtoFromResource(jsonPath)
    return etsFileDto.toEtsFile()
}

/**
 * Load multiple [EtsFile]s from a resource directory.
 *
 * For example, all files in `resources/project/` can be loaded with:
 * ```
 * val files: Sequence<EtsFile> = loadMultipleEtsFilesFromResourceDirectory("/project")
 * ```
 */
fun loadMultipleEtsFilesFromResourceDirectory(dirPath: String): Sequence<EtsFile> {
    val rootPath = getResourcePath(dirPath)
    return rootPath
        .walk(PathWalkOption.BREADTH_FIRST)
        .filter { it.extension == "json" }
        .map { it.relativeTo(rootPath) }
        .map { loadEtsFileFromResource("$dirPath/$it") }
}

fun loadMultipleEtsFilesFromMultipleResourceDirectories(
    dirPaths: List<String>,
): Sequence<EtsFile> {
    return dirPaths.asSequence().flatMap { loadMultipleEtsFilesFromResourceDirectory(it) }
}

fun loadEtsProjectFromResources(
    modules: List<String>,
    prefix: String,
): EtsScene {
    logger.info { "Loading Ets project with ${modules.size} modules $modules from '$prefix/<module>'" }
    val dirPaths = modules.map { "$prefix/$it" }
    val files = loadMultipleEtsFilesFromMultipleResourceDirectories(dirPaths).toList()
    logger.info { "Loaded ${files.size} files" }
    return EtsScene(files, sdkFiles = emptyList())
}

//-----------------------------------------------------------------------------

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
