package org.usvm.ts.pbt.campaign

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.WitnessReductionStatus
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.fastcheck.BackendErrorKind
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertyCampaignRunnerTest {
    private val root = testResourcesRoot().resolve("properties/campaign")
    private val runner = PropertyCampaignRunner(sourceRoot = root)
    private val configuration = PropertyRunConfiguration(seed = 354, numRuns = 1, timeoutMillis = 60_000)

    @Test
    fun `PBT only preserves the original admission and named check without symbolic work`() {
        val property = property().copy(
            predicate = property().predicate.copy(checkResultExportName = "namedResult"),
            assertions = listOf(
                PropertyAssertion(
                    id = "not-forty-two",
                    source = PropertySourcePoint(module = MODULE, line = 5, column = 1),
                ),
            ),
        )

        val result = runner.run(
            property = property,
            mode = PropertyCampaignMode.PBT_ONLY,
            configuration = configuration.copy(examples = listOf(listOf(JsConcreteValue.number(42.0)))),
        )

        assertTrue(result.confirmedViolation)
        assertEquals("not-forty-two", result.concrete?.failure?.checkId)
        assertEquals(PropertyFailureKind.PROPERTY, result.concrete?.failure?.kind)
        assertEquals(property.id, result.propertyId)
        assertNull(result.symbolic)
        assertNull(result.confirmation)
    }

    @Test
    fun `symbolic only searches the original predicate and confirms and reduces the exact candidate`() {
        val result = runner.run(
            property = property(),
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
        )

        assertNull(result.concrete)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.symbolic?.status)
        assertEquals(listOf(JsConcreteValue.number(42.0)), result.symbolic?.inputs)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.confirmation?.status)
        assertEquals(result.symbolic?.inputs, result.confirmation?.candidate?.inputs)
        assertEquals(listOf(JsConcreteValue.number(42.0)), result.confirmation?.reduction?.inputs)
        assertEquals(WitnessReductionStatus.EXHAUSTED, result.confirmation?.reduction?.status)
        assertTrue(result.confirmedViolation)
    }

    @Test
    fun `sequential runs symbolic search after a concrete run with no finding`() {
        val result = runner.run(
            property = property(),
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration.copy(examples = listOf(listOf(JsConcreteValue.number(20.0)))),
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.concrete?.status)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.symbolic?.status)
        assertTrue(result.confirmedViolation)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.confirmation?.status)
    }

    @Test
    fun `sequential retains a concrete violation without demanding another engine`() {
        val result = runner.run(
            property = property(),
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration.copy(examples = listOf(listOf(JsConcreteValue.number(42.0)))),
        )

        assertTrue(result.confirmedViolation)
        assertEquals(listOf(JsConcreteValue.number(42.0)), result.concrete?.counterexample)
        assertNull(result.symbolic)
        assertNull(result.confirmation)
        assertEquals(PropertyCampaignStop.COMPLETED, result.stop)
    }

    @Test
    fun `direct concrete control retains its actual requested source coverage`() {
        val result = runner.run(
            property = property(),
            mode = PropertyCampaignMode.PBT_ONLY,
            configuration = configuration.copy(
                coverageRequest = PropertyCoverageRequest(scopes = setOf(CoverageScope.PROPERTY_ENTRY_POINTS)),
            ),
        )

        val coverage = assertNotNull(result.concrete?.coverage)
        assertTrue(coverage.files.isNotEmpty())
        assertEquals(PropertyCampaignStop.COMPLETED, result.stop)
        assertNull(result.symbolic)
    }

    @Test
    fun `unsupported symbolic work retains the real successful concrete run`() {
        val asynchronous = property().copy(
            predicate = TypeScriptEntryPoint(
                module = MODULE,
                exportName = "asyncHolds",
                executionKind = ExecutionKind.ASYNC,
            ),
        )

        val result = runner.run(
            property = asynchronous,
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration,
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.concrete?.status)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, result.symbolic?.status)
        assertFalse(result.confirmedViolation)
        assertNull(result.confirmation)
    }

    @Test
    fun `named checks without an executable binding retain the unconfirmed symbolic candidate`() {
        val named = property().copy(
            assertions = listOf(
                PropertyAssertion(
                    id = "not-forty-two",
                    source = PropertySourcePoint(module = MODULE, line = 5, column = 1),
                ),
            ),
        )

        val result = runner.run(
            property = named,
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
        )

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.symbolic?.status)
        assertNotNull(result.symbolic?.inputs)
        assertEquals(WitnessReplayStatus.UNSUPPORTED_REPLAY, result.confirmation?.status)
        assertFalse(result.confirmedViolation)
    }

    @Test
    fun `an exhausted search allocation retains the completed concrete result`() {
        val result = runner.run(
            property = property(),
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration,
            validationReserveMillis = configuration.timeoutMillis,
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.concrete?.status)
        assertEquals(PropertyCampaignStop.DEADLINE, result.stop)
        assertEquals(PropertyCampaignPhase.SYMBOLIC, result.stoppedAt)
        assertNull(result.symbolic)
        assertFalse(result.confirmedViolation)
    }

    @Test
    fun `an expired total budget does not become a no finding symbolic result`() {
        val result = runner.run(
            property = property(),
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration.copy(timeoutMillis = 1),
            validationReserveMillis = 1,
        )

        assertEquals(PropertyCampaignStop.DEADLINE, result.stop)
        assertNull(result.symbolic)
        assertNull(result.confirmation)
        assertFalse(result.confirmedViolation)
    }

    @Test
    fun `a native process timeout consumes the shared budget and prevents symbolic fallback`() {
        val hanging = property().copy(predicate = TypeScriptEntryPoint(module = MODULE, exportName = "hangs"))

        val result = runner.run(
            property = hanging,
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration.copy(timeoutMillis = 100),
            validationReserveMillis = 1,
        )

        assertEquals(PropertyCampaignStop.DEADLINE, result.stop)
        assertEquals(PropertyCampaignPhase.PBT, result.stoppedAt)
        assertNull(result.symbolic)
        assertTrue(result.elapsedMillis < 5_000, "A 100 ms campaign acquired a fresh backend timeout")
    }

    @Test
    fun `the backend does not reset an already consumed caller deadline`() {
        val deadline = WitnessReplayDeadline(timeoutMillis = 1)
        while (deadline.remainingMillis() > 0) Thread.onSpinWait()
        val backend = FastCheckBackend(sourceRoots = listOf(root))

        val error = assertFailsWith<PbtBackendException> {
            backend.run(property = property(), configuration = configuration, deadline = deadline)
        }

        assertEquals(BackendErrorKind.TIMEOUT, error.kind)
    }

    @Test
    fun `confirmation timeout preserves the exact unconfirmed symbolic candidate`() {
        val directory = createTempDirectory(prefix = "campaign-confirmation-timeout-")
        val executable = directory.resolve("waiting-node")
        executable.writeText("#!/bin/sh\nexec node -e 'setInterval(() => {}, 1000)'\n")
        check(executable.toFile().setExecutable(true))
        val waitingRunner = PropertyCampaignRunner(sourceRoot = root, nodeExecutable = executable.toString())

        try {
            val result = waitingRunner.run(
                property = property(),
                mode = PropertyCampaignMode.SYMBOLIC_ONLY,
                configuration = configuration.copy(timeoutMillis = 10_000),
                validationReserveMillis = 1_000,
            )

            assertEquals(listOf(JsConcreteValue.number(42.0)), result.symbolic?.inputs)
            assertEquals(result.symbolic?.inputs, result.confirmation?.candidate?.inputs)
            assertEquals(WitnessReplayStatus.TIMEOUT, result.confirmation?.status)
            assertEquals(PropertyCampaignStop.DEADLINE, result.stop)
            assertEquals(PropertyCampaignPhase.CONFIRMATION, result.stoppedAt)
            assertFalse(result.confirmedViolation)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `failed symbolic preparation retains the previously completed concrete result`() {
        val directory = createTempDirectory(prefix = "campaign-source-change-")
        val module = directory.resolve(MODULE)
        module.writeText(
            """
            import { unlinkSync } from 'node:fs';
            export function removesSource(value: number): boolean {
                unlinkSync(${Json.encodeToUtf8SafeString(module.toString())});
                return value === value;
            }
            """.trimIndent(),
        )
        val changingProperty = property().copy(
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = "removesSource"),
            precondition = null,
        )

        try {
            val result = PropertyCampaignRunner(sourceRoot = directory).run(
                property = changingProperty,
                mode = PropertyCampaignMode.SEQUENTIAL,
                configuration = configuration,
            )

            assertFalse(Files.exists(module))
            assertEquals(PropertyRunStatus.SUCCESS, result.concrete?.status)
            assertEquals(PropertyCampaignStop.EXECUTION_FAILURE, result.stop)
            assertEquals(PropertyCampaignPhase.SYMBOLIC, result.stoppedAt)
            assertNotNull(result.diagnostic)
            assertNull(result.symbolic)
            assertFalse(result.confirmedViolation)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun property() = PropertyDefinition(
        id = PropertyId(value = "campaign.isolated"),
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 0, max = 100))),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = "isolatedFailure"),
        precondition = TypeScriptEntryPoint(module = MODULE, exportName = "admits"),
    )

    private companion object {
        const val MODULE = "DirectControls.ts"
    }
}
