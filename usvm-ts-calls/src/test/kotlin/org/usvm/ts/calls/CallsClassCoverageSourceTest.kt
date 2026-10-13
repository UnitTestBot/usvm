package org.usvm.ts.calls

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyInput
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class CallsClassCoverageSourceTest {
    @Test
    fun `class universe freezes exact statements and checks native IDs`() {
        val sourceRoot = Files.createTempDirectory("calls-class-universe-")
        try {
            Files.writeString(
                sourceRoot.resolve("Target.ts"),
                "export class Target { run(x: boolean) { if (x) return 1; return 2; } }",
            )

            val instrumented = CallsClassSourceInstrumentation.create(
                sourceRoot = sourceRoot,
                sourceFile = "Target.ts",
                className = "Target",
            )
            val universe = instrumented.universe
            val surface = CallsClassSurface(
                sourceFile = "Target.ts",
                exportName = "Target",
                constructorInputs = emptyList(),
                members = listOf(
                    CallsClassMember(
                        id = "run",
                        name = "run",
                        kind = CallsClassMemberKind.METHOD,
                        inputs = listOf(PropertyInput(name = "x", domain = BooleanDomain)),
                        resultDomain = IntegerDomain(min = 1, max = 2),
                    ),
                ),
                stateFields = emptyMap(),
            )
            instrumented.validateSurface(surface = surface)
            assertFailsWith<IllegalArgumentException> {
                instrumented.validateSurface(surface = surface.copy(members = emptyList()))
            }
            assertFailsWith<IllegalArgumentException> {
                instrumented.validateSurface(surface = surface.copy(stateFields = mapOf("foreign" to BooleanDomain)))
            }
            universe.verifyUnchanged(sourceRoot = sourceRoot)

            assertEquals(expected = 3, actual = universe.statements.size)
            assertEquals(expected = 3, actual = universe.statementIds.size)
            assertTrue(universe.statements.all { statement -> statement.startLine == 1 })
            assertNotEquals(illegal = universe.sourceSha256, actual = instrumented.instrumentedSha256)
            val firstId = universe.statements.first().id
            assertEquals(expected = setOf(firstId), actual = universe.checkedCoveredIds(listOf(firstId, firstId)))
            assertFailsWith<IllegalArgumentException> { universe.checkedCoveredIds(listOf("foreign-id")) }

            val repeated = CallsClassSourceInstrumentation.create(
                sourceRoot = sourceRoot,
                sourceFile = "Target.ts",
                className = "Target",
            )
            assertEquals(expected = instrumented, actual = repeated)
        } finally {
            Files.walk(sourceRoot).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun `class source drift and unsupported reachability remain explicit failures`() {
        val sourceRoot = Files.createTempDirectory("calls-class-source-drift-")
        try {
            val source = sourceRoot.resolve("Target.ts")
            Files.writeString(source, "export class Target { run() { return 1; } }")
            val universe = CallsClassSourceInstrumentation.create(
                sourceRoot = sourceRoot,
                sourceFile = "Target.ts",
                className = "Target",
            ).universe

            Files.writeString(source, "export class Target { run() { return 2; } }")
            assertFailsWith<IllegalArgumentException> { universe.verifyUnchanged(sourceRoot = sourceRoot) }

            Files.writeString(source, "export class Target { run(key: string) { return this[key]; } }")
            val failure = assertFailsWith<IllegalArgumentException> {
                CallsClassSourceInstrumentation.create(
                    sourceRoot = sourceRoot,
                    sourceFile = "Target.ts",
                    className = "Target",
                )
            }
            assertTrue(failure.message.orEmpty().contains("Computed receiver"))
        } finally {
            Files.walk(sourceRoot).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
