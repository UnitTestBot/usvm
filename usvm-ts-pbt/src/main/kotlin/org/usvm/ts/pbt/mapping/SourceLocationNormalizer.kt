package org.usvm.ts.pbt.mapping

import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.SourcePosition
import org.usvm.ts.pbt.backend.SourceRange
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class SourceLocationNormalizer(sourceRoots: List<Path>) {
    private val paths = SourcePathResolver(sourceRoots)
    private val sourceLineMaps = hashMapOf<Path, TypeScriptLineMap>()

    val normalizedSourceRoots: List<Path> = paths.normalizedSourceRoots
    val sourceRootDiagnostics: List<EtsMappingDiagnostic> = paths.sourceRootDiagnostics

    fun normalizeRange(sourcePath: String, range: SourceRange): NormalizedSourceRange {
        val path = paths.resolveSourceFile(sourcePath)
        val sourceLineMap = sourceLineMaps.getOrPut(path) { readSourceLineMap(path) }
        val start = sourceLineMap.normalize(range.start)
        val end = sourceLineMap.normalize(range.end)

        return NormalizedSourceRange(
            path = path.toString(),
            start = start,
            end = end,
        )
    }

    fun sourcePathCandidates(value: String): Set<Path> = paths.sourcePathCandidates(value)

    fun modulePathCandidates(path: Path): Set<Path> = paths.modulePathCandidates(path)

    private fun readSourceLineMap(path: Path): TypeScriptLineMap {
        val source = Files.readString(path)

        return TypeScriptLineMap(source)
    }
}

private class SourcePathResolver(sourceRoots: List<Path>) {
    private val sourceRootResolutions = sourceRoots.mapIndexed { index, root ->
        normalizeSourceRoot(index, root)
    }

    val normalizedSourceRoots: List<Path> = sourceRootResolutions.map { resolution -> resolution.path }
    val sourceRootDiagnostics: List<EtsMappingDiagnostic> = sourceRootResolutions.mapNotNull { resolution ->
        resolution.diagnostic
    }

    fun sourcePathCandidates(value: String): Set<Path> {
        val path = Path.of(value)
        val candidates = if (path.isAbsolute) {
            listOf(path)
        } else {
            normalizedSourceRoots.map { root -> root.resolve(path) }
        }

        return candidates.mapTo(linkedSetOf()) { candidate -> candidate.canonicalizeIfExisting() }
    }

    fun resolveSourceFile(sourcePath: String): Path {
        val candidates = sourcePathCandidates(sourcePath)
        val existingFiles = candidates.filter(Files::isRegularFile)

        return when (existingFiles.size) {
            0 -> {
                val unresolvedCandidate = candidates.singleOrNull()

                unresolvedCandidate ?: throw UnsupportedSourceLocationException(
                    "Source path $sourcePath does not resolve uniquely below the configured source roots",
                )
            }

            1 -> existingFiles.single()

            else -> throw UnsupportedSourceLocationException(
                "Source path $sourcePath resolves to several files: ${existingFiles.sorted().joinToString()}",
            )
        }
    }

    fun modulePathCandidates(path: Path): Set<Path> {
        val candidates = mutableListOf(path)
        if (!path.hasTypeScriptModuleSuffix()) {
            val moduleName = path.fileName?.toString().orEmpty()

            for (suffix in TYPESCRIPT_MODULE_SUFFIXES) {
                candidates.add(path.resolveSibling("$moduleName$suffix"))
            }
            for (suffix in TYPESCRIPT_MODULE_SUFFIXES) {
                candidates.add(path.resolve("index$suffix"))
            }
        }

        return candidates.mapTo(linkedSetOf()) { candidate -> candidate.canonicalizeIfExisting() }
    }

    private fun normalizeSourceRoot(index: Int, root: Path): SourceRootResolution {
        val normalizedRoot = root.toAbsolutePath().normalize()

        return try {
            val realRoot = normalizedRoot.toRealPath()
            if (Files.isDirectory(realRoot)) {
                SourceRootResolution(path = realRoot)
            } else {
                unsupportedSourceRoot(index, normalizedRoot, "the path is not a directory")
            }
        } catch (error: IOException) {
            unsupportedSourceRoot(index, normalizedRoot, error.message ?: "the path cannot be resolved")
        }
    }

    private fun unsupportedSourceRoot(index: Int, path: Path, reason: String): SourceRootResolution {
        val diagnostic = EtsMappingDiagnostic(
            code = PbtDiagnosticCode.MAPPING_SOURCE_ROOT_UNSUPPORTED,
            message = "Cannot resolve TypeScript source root $index ($path): $reason",
            sourcePath = path.toString(),
        )

        return SourceRootResolution(
            path = path,
            diagnostic = diagnostic,
        )
    }

    private fun Path.canonicalizeIfExisting(): Path {
        val absolutePath = if (isAbsolute) this else toAbsolutePath()

        return try {
            absolutePath.toRealPath()
        } catch (_: IOException) {
            absolutePath.normalize()
        }
    }
}

private class TypeScriptLineMap(source: String) {
    private val lines = source.indexTypeScriptLines()

    fun normalize(position: SourcePosition): NormalizedSourcePosition {
        val zeroBasedLine = position.line - ISTANBUL_LINE_BASE
        val sourceLine = lines.getOrNull(zeroBasedLine)
            ?: throw UnsupportedSourceLocationException("Source line ${position.line} is outside the file")
        val offset = sourceLine.startOffset + position.column
        if (offset > sourceLine.endOffset) {
            throw UnsupportedSourceLocationException(
                "Source column ${position.column} is outside line ${position.line}",
            )
        }

        return NormalizedSourcePosition(
            line = zeroBasedLine,
            column = position.column,
            offset = offset,
        )
    }
}

private fun String.indexTypeScriptLines(): List<SourceLine> {
    val lines = mutableListOf<SourceLine>()
    var lineStart = 0
    for (lineBreak in TYPESCRIPT_LINE_BREAK.findAll(this)) {
        lines += SourceLine(startOffset = lineStart, endOffset = lineBreak.range.first)
        lineStart = lineBreak.range.last + 1
    }

    lines += SourceLine(startOffset = lineStart, endOffset = length)

    return lines
}

private fun Path.hasTypeScriptModuleSuffix(): Boolean {
    val name = fileName?.toString().orEmpty()

    return TYPESCRIPT_MODULE_SUFFIXES.any(name::endsWith)
}

private data class SourceLine(
    val startOffset: Int,
    val endOffset: Int,
)

private data class SourceRootResolution(
    val path: Path,
    val diagnostic: EtsMappingDiagnostic? = null,
)

internal class UnsupportedSourceLocationException(message: String) : IllegalArgumentException(message)

// TypeScript also treats the Unicode LINE SEPARATOR and PARAGRAPH SEPARATOR characters as line breaks.
private const val UNICODE_LINE_SEPARATOR = '\u2028'
private const val UNICODE_PARAGRAPH_SEPARATOR = '\u2029'
private const val ISTANBUL_LINE_BASE = 1
private val TYPESCRIPT_LINE_BREAK = Regex(
    pattern = "\r\n|[\n\r$UNICODE_LINE_SEPARATOR$UNICODE_PARAGRAPH_SEPARATOR]",
)
private val TYPESCRIPT_MODULE_SUFFIXES = listOf(".ts", ".ets", ".d.ts")
