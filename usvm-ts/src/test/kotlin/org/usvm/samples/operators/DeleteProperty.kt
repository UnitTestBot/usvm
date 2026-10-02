package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
