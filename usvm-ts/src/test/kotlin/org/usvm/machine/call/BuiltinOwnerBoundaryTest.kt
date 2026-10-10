package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath

class BuiltinOwnerBoundaryTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/BuiltinOwnerBoundary.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    @Test
    fun `local objects named Math and Number retain their own methods`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "shadowedMath", className = "BuiltinOwnerBoundary"),
            { result -> result.number == 77.0 },
            invariants = arrayOf({ result -> result.number == 77.0 }),
        )
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "shadowedNumber", className = "BuiltinOwnerBoundary"),
            { result -> !result.value },
            invariants = arrayOf({ result -> !result.value }),
        )
    }

    @Test
    fun `aliases retain genuine numeric builtin semantics`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "aliasedMath", className = "BuiltinOwnerBoundary"),
            { result -> result.number == 2.0 },
            invariants = arrayOf({ result -> result.number == 2.0 }),
        )
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "aliasedNumber", className = "BuiltinOwnerBoundary"),
            { result -> !result.value },
            invariants = arrayOf({ result -> !result.value }),
        )
    }
}
