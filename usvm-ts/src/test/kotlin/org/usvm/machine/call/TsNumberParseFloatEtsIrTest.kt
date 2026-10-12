package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.assertTrue

class TsNumberParseFloatEtsIrTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/NumberParseFloatEtsIr.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            )
        ),
    )
    override val tsOptions = TsOptions(
        unknownCallFallback = TsResidualCallPolicy.STOP_PATH,
        unknownCallModelSelection = TsUnknownCallModelSelection.Only(setOf("ts.number.parseFloat")),
    )

    // This suite exercises the production model catalog instead of the compatibility-only runner.
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
            machine.analyze(methods = listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    @Test
    fun `decimal prefix model preserves numeric results and ignored argument evaluation`() {
        val expected = mapOf(
            "decimalPrefix" to -1.25,
            "exponentPrefix" to 50.0,
            "incompleteExponent" to 12.0,
            "hexadecimalPrefix" to 0.0,
            "secondDecimalPoint" to 1.2,
            "unicodeWhitespace" to Double.POSITIVE_INFINITY,
            "negativeZero" to Double.NEGATIVE_INFINITY,
            "notANumber" to Double.NaN,
            "decimalRounding" to 0.100000000000001,
            "ignoredArgument" to 13.0,
        )

        for ((methodName, expectedValue) in expected) {
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(methodName = methodName, className = "NumberParseFloatEtsIr"),
                { result -> result.number.toBits() == expectedValue.toBits() },
                invariants = arrayOf({ result -> result.number.toBits() == expectedValue.toBits() }),
            )
        }
    }

    @Test
    fun `decimal outside the exact model domain stays residual`() {
        val events = mutableListOf<TsUnknownCallEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) {
                events += event
            }
        }
        val method = getMethod(methodName = "unsupportedMantissa", className = "NumberParseFloatEtsIr")

        val states = TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer).use { machine ->
            machine.analyze(methods = listOf(method))
        }

        assertTrue(states.isEmpty())
        val modelApplied = TsUnknownCallDecision.ModelApplied(modelId = "ts.number.parseFloat")
        assertTrue(events.any { it.decision == modelApplied })
        assertTrue(events.any { it.outcome == TsUnknownCallOutcome.PATH_STOPPED })
    }
}
