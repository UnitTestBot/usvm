package org.usvm.ts.pbt.coverage

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawV8SourceMapInspectorTest {
    @Test
    fun `empty raw coverage directory is a typed missing report failure`() {
        withRawDirectory { rawDirectory ->
            val error = assertFailsWith<CoverageArtifactException> {
                inspectRawV8SourceMapDiagnostics(
                    rawDirectory = rawDirectory,
                    sourceRoots = listOf(rawDirectory.toString()),
                )
            }

            assertEquals("coverage.report.missing", error.diagnostic.code)
            assertEquals(rawDirectory.toString(), error.diagnostic.path)
        }
    }

    @Test
    fun `raw report count is bounded before source-map caches are decoded`() {
        withRawDirectory { rawDirectory ->
            rawDirectory.resolve("first.json").writeText("{not-json")
            rawDirectory.resolve("second.json").writeText("{not-json")

            val error = assertFailsWith<CoverageArtifactException> {
                inspectRawV8SourceMapDiagnostics(
                    rawDirectory = rawDirectory,
                    sourceRoots = listOf(rawDirectory.toString()),
                    maxReportFiles = 1,
                    maxReportBytes = 1_024,
                )
            }

            assertEquals("coverage.report.invalid", error.diagnostic.code)
            assertEquals(rawDirectory.toString(), error.diagnostic.path)
        }
    }

    @Test
    fun `raw report bytes are bounded before the file is parsed`() {
        withRawDirectory { rawDirectory ->
            val rawReport = rawDirectory.resolve("coverage.json")
            rawReport.writeText("{not-json-but-over-the-test-limit")

            val error = assertFailsWith<CoverageArtifactException> {
                inspectRawV8SourceMapDiagnostics(
                    rawDirectory = rawDirectory,
                    sourceRoots = listOf(rawDirectory.toString()),
                    maxReportFiles = 1,
                    maxReportBytes = 8,
                )
            }

            assertEquals("coverage.report.invalid", error.diagnostic.code)
            assertEquals(rawReport.toString(), error.diagnostic.path)
        }
    }

    @Test
    fun `malformed raw source-map-cache schema is a typed coverage failure`() {
        withRawDirectory { rawDirectory ->
            val rawReport = rawDirectory.resolve("coverage.json")
            rawReport.writeText("""{"source-map-cache": []}""")

            val error = assertFailsWith<CoverageArtifactException> {
                inspectRawV8SourceMapDiagnostics(
                    rawDirectory = rawDirectory,
                    sourceRoots = listOf(rawDirectory.toString()),
                )
            }

            assertEquals("coverage.report.invalid", error.diagnostic.code)
            assertEquals("$rawReport.source-map-cache", error.diagnostic.path)
        }
    }

    @Test
    fun `raw diagnostics are deterministic across file order and duplicate cache entries`() {
        withRawDirectory { rawDirectory ->
            val sourceRoot = rawDirectory.resolve("source").createDirectory()
            val firstScript = sourceRoot.resolve("first.js")
            val secondScript = sourceRoot.resolve("second.js")
            firstScript.writeText("export const first = 1")
            secondScript.writeText("export const second = 2")
            rawDirectory.resolve("z-last.json").writeText(rawReport(firstScript, secondScript))
            rawDirectory.resolve("a-first.json").writeText(rawReport(secondScript, firstScript))

            val diagnostics = inspectRawV8SourceMapDiagnostics(
                rawDirectory = rawDirectory,
                sourceRoots = listOf(sourceRoot.toString()),
            )

            assertEquals(
                listOf(
                    firstScript.toString() to "coverage.source-map.missing",
                    secondScript.toString() to "coverage.source-map.missing",
                ),
                diagnostics.map { diagnostic -> diagnostic.path to diagnostic.code },
            )
        }
    }

    @Test
    fun `present referenced map with a URL query is classified as invalid`() {
        withRawDirectory { rawDirectory ->
            val sourceRoot = rawDirectory.resolve("source").createDirectory()
            val script = sourceRoot.resolve("generated.js")
            script.writeText("export const generated = 1")
            sourceRoot.resolve("generated.js.map").writeText("{not-json")
            rawDirectory.resolve("coverage.json").writeText(
                """
                {
                  "source-map-cache": {
                    "${script.toUri()}": {
                      "lineLengths": [1],
                      "data": null,
                      "url": "generated.js.map?cache=1"
                    }
                  }
                }
                """.trimIndent(),
            )

            val diagnostic = inspectRawV8SourceMapDiagnostics(
                rawDirectory = rawDirectory,
                sourceRoots = listOf(sourceRoot.toString()),
            ).single()

            assertEquals("coverage.source-map.invalid", diagnostic.code)
            assertEquals(script.toString(), diagnostic.path)
        }
    }

    @Test
    fun `raw source-map diagnostics override final-report guesses and merge without duplicates`() {
        val finalDiagnostics = listOf(
            CoverageDiagnostic(
                code = "coverage.source-map.missing",
                message = "final missing",
                path = "/workspace/second.js",
            ),
        )
        val rawDiagnostics = listOf(
            CoverageDiagnostic(
                code = "coverage.source-map.invalid",
                message = "raw invalid",
                path = "/workspace/second.js",
            ),
            CoverageDiagnostic(
                code = "coverage.source-map.missing",
                message = "raw missing",
                path = "/workspace/first.js",
            ),
            CoverageDiagnostic(
                code = "coverage.source-map.missing",
                message = "raw missing",
                path = "/workspace/first.js",
            ),
        )

        val merged = mergeCoverageDiagnostics(
            finalDiagnostics = finalDiagnostics,
            rawDiagnostics = rawDiagnostics,
        )

        assertEquals(
            listOf(
                "/workspace/first.js" to "coverage.source-map.missing",
                "/workspace/second.js" to "coverage.source-map.invalid",
            ),
            merged.map { diagnostic -> diagnostic.path to diagnostic.code },
        )
    }

    private fun rawReport(firstScript: Path, secondScript: Path): String =
        """
        {
          "source-map-cache": {
            "${firstScript.toUri()}": {
              "lineLengths": [1],
              "data": null,
              "url": "${firstScript.fileName}.map"
            },
            "${secondScript.toUri()}": {
              "lineLengths": [1],
              "data": null,
              "url": "${secondScript.fileName}.map"
            }
          }
        }
        """.trimIndent()

    private fun withRawDirectory(block: (Path) -> Unit) {
        val rawDirectory = createTempDirectory(prefix = "raw-v8-source-maps-")

        try {
            block(rawDirectory)
        } finally {
            rawDirectory.toFile().deleteRecursively()
        }
    }
}
