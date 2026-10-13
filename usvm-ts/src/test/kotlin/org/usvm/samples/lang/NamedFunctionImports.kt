package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NamedFunctionImports : TsMethodTestRunner() {
    override val scene: EtsScene = loadEtsProjectAutoConvert(
        getResourcePath(res = "/samples/lang/named-function-imports"),
        useArkAnalyzerTypeInference = null,
    )

    @Test
    fun `same named functions in different files retain their owners`() {
        val method = getMethod(methodName = "distinctOwners")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 12 },
            invariants = arrayOf({ result -> result eq 12 }),
        )
    }

    @Test
    fun `namespace import reads the current function while alias retains its original target`() {
        val method = getMethod(methodName = "namespaceLiveBinding")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 13 },
            invariants = arrayOf({ result -> result eq 13 }),
        )
    }

    @Test
    fun `namespace direct and optional calls explicitly report their unrepresented receiver`() {
        listOf("namespaceDirectCall", "namespaceOptionalCall").forEach { methodName ->
            val method = getMethod(methodName = methodName)
            val outcome = TsMachine(
                scene = scene,
                options = UMachineOptions(solverType = SolverType.YICES),
                tsOptions = TsOptions(),
            ).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.states.isEmpty(), methodName)
            assertTrue(outcome.unsupportedPaths.any { "namespace.receiverIsUndefined" in it }, "$methodName: $outcome")
        }
    }
}
