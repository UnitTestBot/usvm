package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.eq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class DeleteProperty : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/operators/DeleteProperty.ts")

    @Test
    fun `number property reads as undefined after delete`() {
        val method = getMethod("readAfterDelete")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsUndefined>(
            method = method,
            { _, result -> result == TsTestValue.TsUndefined },
            invariants = arrayOf({ _, result -> result == TsTestValue.TsUndefined }),
        )
    }

    @Test
    fun `delete example exhausts paths without interpreter failure`() {
        val method = getMethod("readAfterDelete")
        val options = UMachineOptions(
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            throwExceptionOnStepFailure = true,
        )

        val result = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
        assertTrue(result.states.isNotEmpty())
    }

    @Test
    fun `boolean property reads as undefined after delete`() {
        val method = getMethod("deleteBoolean")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `typed number property reads as undefined after delete`() {
        val method = getMethod("deleteTypedNumber")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `reference property reads as undefined after delete`() {
        val method = getMethod("deleteReference")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `alias observes delete`() {
        val method = getMethod("aliasSeesDelete")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `reassignment restores deleted property`() {
        val method = getMethod("restoreAfterDelete")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `deleting missing property succeeds`() {
        val method = getMethod("deleteMissing")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `conditional delete preserves both read outcomes`() {
        val method = getMethod("readAfterConditionalDelete")
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val analysis = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }
        val tests = analysis.states.map { state -> TsTestResolver().resolve(method, state) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.unsupportedPaths.isEmpty(), "${analysis.unsupportedPaths}")
        assertEquals(setOf(false, true), tests.map { test ->
            assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
        }.toSet())
        tests.forEach { test ->
            val shouldDelete = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number.toInt()
            assertEquals(if (shouldDelete) 1 else 2, result, "$test")
        }
    }

    @Test
    fun `deleting a property of an input object reports unsupported outcome`() {
        val method = getMethod("deleteInput")
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val analysis = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.states.isEmpty())
        assertTrue(analysis.unsupportedPaths.any { "Deleting a property of an input object" in it })
    }

    @Test
    fun `deleting an own property with a prototype fallback reports unsupported outcome`() {
        val method = getMethod("deleteOwnToString")
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val analysis = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.states.isEmpty())
        assertTrue(analysis.unsupportedPaths.any { "Object.prototype lookup" in it })
    }
}
