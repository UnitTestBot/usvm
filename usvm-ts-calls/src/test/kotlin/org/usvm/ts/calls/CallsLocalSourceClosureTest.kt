package org.usvm.ts.calls

import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CallsLocalSourceClosureTest : CurrentTsCallsSymbolicEngineTestSupport() {
    @Test
    fun `source closure is reloaded after checkout revision changes`() {
        val fixture = importedFixture(
            exportName = "revisionSensitive",
            dependency = "export function helper(value: string): boolean { return true; }",
        )
        val first = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, first.status, first.toString())

        Files.writeString(
            fixture.sourceRoot.resolve("helper.ts"),
            "export function replacement(value: string): boolean { return true; }",
        )
        runGit(fixture.sourceRoot, "add", "helper.ts")
        runGit(fixture.sourceRoot, "commit", "-m", "replace helper")
        val nextRevision = runGit(fixture.sourceRoot, "rev-parse", "HEAD").trim()

        val second = fixture.preflight(project = fixture.project.copy(revision = nextRevision))

        assertEquals(
            CallsSymbolicPreflightReasonCode.IMPORTED_CALLEES_UNSUPPORTED,
            second.reasonCode,
            second.toString(),
        )
    }

    @Test
    fun `loads local dependency closure and replays a nested source target`() {
        val fixture = fixture(
            source = """
                import { magnitude } from '../helpers';
                export function positiveMagnitude(value: number): boolean {
                  if (magnitude(value) === 42) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "positiveMagnitude",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "true",
            sourceFileName = "entry/Fixture.ts",
            dependencies = mapOf(
                "helpers/index.ts" to """
                    import { absolute } from './Fixture.js';
                    export function magnitude(value: number): number { return absolute(value); }
                """.trimIndent(),
                "helpers/Fixture.ts" to "export function absolute(value: number): number { return Math.abs(value); }",
                "unrelated.ts" to "throw new Error('This module must not be loaded');",
            ),
        )
        val events = mutableListOf<TsUnknownCallEvent>()

        val preflight = fixture.preflight()
        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        val result = fixture.search(modelIds = setOf("ts.math.abs"), unknownCallEventSink = events::add)

        val inputs = assertNotNull(result.inputs, "$result; events=$events")
        assertEquals(42.0, kotlin.math.abs(assertIs<JsConcreteValue.Number>(inputs.single()).toDouble()))
        assertTrue(events.any { (it.decision as? TsUnknownCallDecision.ModelApplied)?.modelId == "ts.math.abs" })
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `preflight checks regex in a reachable imported helper using its own source`() {
        val fixture = importedFixture(
            exportName = "importedRegex",
            dependency = "export function helper(value: string): boolean { return /a/.test(value); }",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightReasonCode.REGEX_LITERAL_UNSUPPORTED, result.reasonCode, result.toString())
    }

    @Test
    fun `preflight does not inspect an unreachable imported helper body`() {
        val fixture = importedFixture(
            exportName = "unreachableRegex",
            dependency = """
                export function helper(value: string): boolean { return value.length > 0; }
                export function unused(value: string): boolean { return /a/.test(value); }
            """.trimIndent(),
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, result.status, result.toString())
    }

    @Test
    fun `preflight rejects transitive external imports`() {
        val fixture = importedFixture(
            exportName = "externalImport",
            dependency = """
                import { existsSync } from 'node:fs';
                export function helper(value: string): boolean { return existsSync(value); }
            """.trimIndent(),
        )

        val result = fixture.preflight()

        assertEquals(
            CallsSymbolicPreflightReasonCode.IMPORTED_CALLEES_UNSUPPORTED,
            result.reasonCode,
            result.toString(),
        )
        assertTrue(result.diagnostic.orEmpty().contains("node:fs"))
    }

    @Test
    fun `preflight rejects dependency module initialization effects`() {
        val fixture = importedFixture(
            exportName = "moduleEffects",
            dependency = """
                let threshold = 0;
                threshold = 1;
                export function helper(value: string): boolean { return value.length > threshold; }
            """.trimIndent(),
        )

        val result = fixture.preflight()

        assertEquals(
            CallsSymbolicPreflightReasonCode.TOP_LEVEL_INITIALIZATION_UNSUPPORTED,
            result.reasonCode,
            result.toString(),
        )
    }

    @Test
    fun `preflight rejects missing imports and does not treat them as unknown calls`() {
        val fixture = importedFixture(
            exportName = "missingImport",
            dependency = """
                import { absent } from './missing';
                export function helper(value: string): boolean { return absent(value); }
            """.trimIndent(),
        )

        val result = fixture.preflight()

        assertEquals(
            CallsSymbolicPreflightReasonCode.IMPORTED_CALLEES_UNSUPPORTED,
            result.reasonCode,
            result.toString(),
        )
        assertTrue(result.diagnostic.orEmpty().contains("./missing"))
    }

    @Test
    fun `preflight ignores explicitly type-only external imports`() {
        val fixture = importedFixture(
            exportName = "typeOnlyImport",
            dependency = """
                import type { ExternalType } from 'missing-type-package';
                export function helper(value: string): boolean { return value.length > 0; }
            """.trimIndent(),
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, result.status, result.toString())
    }

    @Test
    fun `preflight checks a regex initializer in an imported module`() {
        val fixture = importedFixture(
            exportName = "importedInitializer",
            dependency = """
                const pattern = /a/;
                export function helper(value: string): boolean { return pattern.test(value); }
            """.trimIndent(),
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightReasonCode.REGEX_LITERAL_UNSUPPORTED, result.reasonCode, result.toString())
    }

    private fun importedFixture(exportName: String, dependency: String): SymbolicFixture = fixture(
        source = """
            import { helper } from './helper';
            export function $exportName(value: string): boolean { return helper(value); }
        """.trimIndent(),
        exportName = exportName,
        inputs = listOf(PropertyInput(name = "value", domain = StringDomain(maxLength = 3))),
        targetStatement = "return helper(value);",
        targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
        returnExpression = "helper(value)",
        dependencies = mapOf("helper.ts" to dependency),
    )
}
