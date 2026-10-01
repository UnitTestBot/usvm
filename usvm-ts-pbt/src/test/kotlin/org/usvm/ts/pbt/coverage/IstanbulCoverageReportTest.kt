package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.CoverageCollectorIdentity
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.model.PropertyId
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IstanbulCoverageReportTest {
    @Test
    fun `decodes literal TypeScript statement function and branch hits`() {
        val artifact = decodeIstanbulCoverageReport(
            reportPath = goldenReport(),
            context = context(),
        )

        val file = artifact.files.single()
        val branch = file.branches.single()
        val firstArm = branch.arms.first()

        assertEquals("/workspace/src/math.ts", file.path)
        assertEquals(listOf(2L, 2L), file.statements.map { statement -> statement.hits })
        assertEquals("classify", file.functions.single().name)
        assertEquals(2L, file.functions.single().hits)
        assertEquals("if", branch.type)
        assertEquals(listOf(1L, 1L), branch.arms.map { arm -> arm.hits })
        assertEquals(2, branch.location.start.line)
        assertEquals(2, firstArm.location.start.line)
    }

    @Test
    fun `classifies entry points wrappers and dependencies independently`() {
        val artifact = decodeIstanbulCoverageReport(
            reportPath = goldenReport(),
            context = context(
                request = PropertyCoverageRequest(scopes = CoverageScope.entries.toHashSet()),
            ),
        )

        assertEquals(
            listOf(
                "/adapter/dist/src/execution-cli.js",
                "/adapter/node_modules/example/index.js",
                "/workspace/src/math.ts",
                "/workspace/src/property.ts",
            ),
            artifact.files.map { file -> file.path },
        )
    }

    @Test
    fun `include and exclude globs filter remapped paths with exclude precedence`() {
        val artifact = decodeIstanbulCoverageReport(
            reportPath = goldenReport(),
            context = context(
                request = PropertyCoverageRequest(
                    scopes = CoverageScope.entries.toHashSet(),
                    includePatterns = listOf("**/*.ts"),
                    excludePatterns = listOf("**/property.?s"),
                ),
            ),
        )

        assertEquals(listOf("/workspace/src/math.ts"), artifact.files.map { file -> file.path })
    }

    @Test
    fun `generated JavaScript below source roots reports a missing source map`() {
        val artifact = decodeIstanbulCoverageReport(
            reportPath = goldenReport(),
            context = context(),
        )

        val diagnostic = artifact.diagnostics.single()
        assertEquals("coverage.source-map.missing", diagnostic.code)
        assertEquals("/workspace/src/generated-without-map.js", diagnostic.path)
    }

    @Test
    fun `missing and malformed reports produce stable diagnostics`() {
        val missing = assertFailsWith<CoverageArtifactException> {
            decodeIstanbulCoverageReport(
                reportPath = Path.of("/definitely/missing/coverage-final.json"),
                context = context(),
            )
        }
        assertEquals("coverage.report.missing", missing.diagnostic.code)

        val malformedReport = createTempFile(suffix = ".json")
        try {
            malformedReport.writeText("{not-json")
            val malformed = assertFailsWith<CoverageArtifactException> {
                decodeIstanbulCoverageReport(
                    reportPath = malformedReport,
                    context = context(),
                )
            }
            assertEquals("coverage.report.invalid", malformed.diagnostic.code)
        } finally {
            malformedReport.deleteIfExists()
        }
    }

    @Test
    fun `report larger than the decoder limit is rejected before parsing`() {
        val oversizedReport = createTempFile(suffix = ".json")
        try {
            Files.newByteChannel(oversizedReport, StandardOpenOption.WRITE).use { report ->
                report.position(TEST_COVERAGE_REPORT_LIMIT_BYTES)
                report.write(ByteBuffer.wrap(byteArrayOf(0)))
            }

            val oversized = assertFailsWith<CoverageArtifactException> {
                decodeIstanbulCoverageReport(
                    reportPath = oversizedReport,
                    context = context(),
                )
            }

            assertEquals("coverage.report.invalid", oversized.diagnostic.code)
            assertEquals(
                "Istanbul coverage report exceeds $TEST_COVERAGE_REPORT_LIMIT_BYTES bytes",
                oversized.diagnostic.message,
            )
        } finally {
            oversizedReport.deleteIfExists()
        }
    }

    @Test
    fun `dense legal report near the strict limit observes an expired deadline`() {
        val report = createTempFile(suffix = ".json")
        val statements = 30_000

        try {
            val statementMap = (0 until statements).joinToString(separator = ",") { id ->
                "\"$id\":{\"start\":{\"line\":1,\"column\":0},\"end\":{\"line\":1,\"column\":1}}"
            }
            val hits = (0 until statements).joinToString(separator = ",") { id -> "\"$id\":1" }
            val prefix = "{\"/workspace/src/math.ts\":{\"path\":\"/workspace/src/math.ts\"," +
                "\"statementMap\":{$statementMap},\"fnMap\":{},\"branchMap\":{}," +
                "\"s\":{$hits},\"f\":{},\"b\":{}}}"
            val padding = (STRICT_REPORT_LIMIT_BYTES - prefix.toByteArray().size - 1).toInt()
            assertTrue(padding > 0)
            report.writeText(prefix + " ".repeat(padding))
            assertEquals(STRICT_REPORT_LIMIT_BYTES - 1, Files.size(report))

            val startedAt = System.nanoTime()
            var deadline = 0L
            var checkpoints = 0
            assertFailsWith<TestDeadlineExpired> {
                decodeIstanbulCoverageReport(
                    reportPath = report,
                    context = context(),
                    maxReportBytes = STRICT_REPORT_LIMIT_BYTES,
                    checkBudget = {
                        if (deadline == 0L) deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5)
                        checkpoints++
                        if (System.nanoTime() >= deadline) throw TestDeadlineExpired()
                    },
                )
            }
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            assertTrue(checkpoints >= 2)
            assertTrue(elapsedMillis >= 5)
        } finally {
            report.deleteIfExists()
        }
    }

    @Test
    fun `Istanbul entry iteration checks the deadline within one retained file`() {
        val report = IstanbulCoverageReportReader.read(goldenReport())
        val file = report.getValue("/workspace/src/math.ts").jsonObject
        var checkpoints = 0
        val decoder = IstanbulSourceFileDecoder(
            file = file,
            path = "/workspace/src/math.ts",
            reportKey = "/workspace/src/math.ts",
            checkBudget = {
                checkpoints++
                if (checkpoints == 2) throw TestDeadlineExpired()
            },
        )

        assertFailsWith<TestDeadlineExpired> { decoder.decode() }
        assertEquals(2, checkpoints)
    }

    @Test
    fun `generated JavaScript with an unusable map reports an invalid source map`() {
        val directory = createTempDirectory("invalid-source-map-")
        try {
            val generated = directory.resolve("generated.js")
            generated.writeText("export const value = 1;\n//# sourceMappingURL=generated.js.map")
            directory.resolve("generated.js.map").writeText("{not-json")
            val report = directory.resolve("coverage-final.json")
            report.writeText(
                """
                {
                  "$generated": {
                    "path": "$generated",
                    "statementMap": {
                      "0": { "start": { "line": 1, "column": 0 }, "end": { "line": 1, "column": 23 } }
                    },
                    "fnMap": {},
                    "branchMap": {},
                    "s": { "0": 1 },
                    "f": {},
                    "b": {}
                  }
                }
                """.trimIndent(),
            )

            val artifact = decodeIstanbulCoverageReport(
                reportPath = report,
                context = context().copy(sourceRoots = listOf(directory.toString())),
            )

            assertEquals("coverage.source-map.invalid", artifact.diagnostics.single().code)
            assertEquals(generated.toString(), artifact.diagnostics.single().path)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun context(
        request: PropertyCoverageRequest = PropertyCoverageRequest(),
    ) = IstanbulCoverageContext(
        backendId = "fast-check",
        backendVersion = "4.9.0",
        propertyId = PropertyId("coverage.property"),
        sourceRoots = listOf("/workspace/src"),
        propertyEntryPointPaths = setOf("/workspace/src/property.ts"),
        adapterRoot = "/adapter",
        runtimeVersion = "v22.14.0",
        collector = CoverageCollectorIdentity(id = "c8", version = "10.1.3"),
        request = request,
    )

    private fun goldenReport(): Path = locateFile(
        "src/test/resources/coverage/istanbul/statement-branch.json",
        "usvm-ts-pbt/src/test/resources/coverage/istanbul/statement-branch.json",
    )

    private fun locateFile(vararg candidates: String): Path = candidates
        .map { candidate -> Path.of(candidate).absolute() }
        .singleOrNull(Files::isRegularFile)
        ?: error("Cannot locate file; checked ${candidates.toList()}")

    private companion object {
        const val TEST_COVERAGE_REPORT_LIMIT_BYTES = 64L * 1024 * 1024
        const val STRICT_REPORT_LIMIT_BYTES = 4L * 1024 * 1024
    }

    private class TestDeadlineExpired : RuntimeException()
}
