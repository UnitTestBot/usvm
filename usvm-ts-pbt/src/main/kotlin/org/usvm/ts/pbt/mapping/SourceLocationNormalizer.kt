package org.usvm.ts.pbt.mapping

import org.usvm.ts.pbt.backend.SourcePosition
import org.usvm.ts.pbt.backend.SourceRange
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class SourceLocationNormalizer(sourceRoots: List<Path>) {
    private val sourceRootResolutions = sourceRoots.mapIndexed { index, root ->
        normalizeSourceRoot(index, root)
    }

    val normalizedSourceRoots: List<Path> = sourceRootResolutions.map { resolution -> resolution.path }
    val sourceRootDiagnostics: List<EtsMappingDiagnostic> = sourceRootResolutions.mapNotNull { resolution ->
        resolution.diagnostic
    }

    fun normalizeRange(sourcePath: String, range: SourceRange): NormalizedSourceRange {
        val path = normalizePath(sourcePath).single()
        val source = Files.readString(path)
        val lines = source.sourceLines()
        val start = range.start.normalize(lines)
        val end = range.end.normalize(lines)
        if (end.offset < start.offset) {
            throw UnsupportedSourceLocationException("Source range end precedes its start")
        }

        return NormalizedSourceRange(
            path = path.toString(),
            start = start,
            end = end,
        )
    }

    fun normalizePath(value: String): Set<Path> {
        val path = Path.of(value)
        val candidates = if (path.isAbsolute) {
            listOf(path)
        } else {
            normalizedSourceRoots.map { root -> root.resolve(path) }
        }

        return candidates.mapTo(linkedSetOf()) { candidate -> candidate.canonicalizeIfExisting() }
    }

    fun modulePathCandidates(path: Path): Set<Path> {
        val candidates = buildList {
            add(path)
            val name = path.fileName?.toString().orEmpty()
            if (name.endsWith(".ts") || name.endsWith(".ets")) return@buildList

            add(path.resolveSibling("$name.ts"))
            add(path.resolveSibling("$name.ets"))
            add(path.resolveSibling("$name.d.ts"))
            add(path.resolve("index.ts"))
            add(path.resolve("index.ets"))
            add(path.resolve("index.d.ts"))
        }

        return candidates.mapTo(linkedSetOf()) { candidate -> candidate.canonicalizeIfExisting() }
    }

    private fun SourcePosition.normalize(lines: List<SourceLine>): NormalizedSourcePosition {
        val zeroBasedLine = line - ISTANBUL_LINE_BASE
        val sourceLine = lines.getOrNull(zeroBasedLine)
            ?: throw UnsupportedSourceLocationException("Source line $line is outside the file")
        val offset = sourceLine.startOffset + column
        if (offset > sourceLine.endOffset) {
            throw UnsupportedSourceLocationException(
                "Source column $column is outside line $line",
            )
        }

        return NormalizedSourcePosition(
            line = zeroBasedLine,
            column = column,
            offset = offset,
        )
    }

    private fun String.sourceLines(): List<SourceLine> = buildList {
        var lineStart = 0
        var index = 0
        while (index < length) {
            val terminatorLength = when (this@sourceLines[index]) {
                '\r' -> if (this@sourceLines.getOrNull(index + 1) == '\n') 2 else 1
                '\n', '\u2028', '\u2029' -> 1
                else -> 0
            }
            if (terminatorLength == 0) {
                index++
                continue
            }

            add(SourceLine(startOffset = lineStart, endOffset = index))
            index += terminatorLength
            lineStart = index
        }

        add(SourceLine(startOffset = lineStart, endOffset = length))
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

    private fun unsupportedSourceRoot(index: Int, path: Path, reason: String): SourceRootResolution =
        SourceRootResolution(
            path = path,
            diagnostic = EtsMappingDiagnostic(
                code = "mapping.source-root.unsupported",
                message = "Cannot resolve TypeScript source root $index ($path): $reason",
                sourcePath = path.toString(),
            ),
        )

    private fun Path.canonicalizeIfExisting(): Path {
        val absolutePath = if (isAbsolute) this else toAbsolutePath()

        return try {
            absolutePath.toRealPath()
        } catch (_: IOException) {
            absolutePath.normalize()
        }
    }

    private companion object {
        const val ISTANBUL_LINE_BASE = 1
    }
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
