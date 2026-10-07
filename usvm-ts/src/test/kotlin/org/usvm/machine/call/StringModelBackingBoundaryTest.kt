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

class StringModelBackingBoundaryTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/StringModelBackingBoundary.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        TsMachine(scene = scene, options = options, tsOptions = TsOptions(maxArraySize = 2)).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    @Test
    fun `derived strings compare their full contents beyond the input bound`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsBoolean>(
            method = getMethod(methodName = "equalDerived", className = "StringModelBackingBoundary"),
            { _, result -> result.value },
            invariants = arrayOf({ _, result -> result.value }),
        )
    }

    @Test
    fun `derived string length uses the output bound`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = getMethod(methodName = "derivedLength", className = "StringModelBackingBoundary"),
            { input, result -> result.number == input.value.length + 10.0 },
            invariants = arrayOf({ input, result -> result.number == input.value.length + 10.0 }),
        )
    }

    @Test
    fun `derived string witnesses keep their full bound`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = getMethod(methodName = "returnedDerived", className = "StringModelBackingBoundary"),
            { input, result -> result.value == input.value + "0123456789" },
            invariants = arrayOf({ input, result -> result.value == input.value + "0123456789" }),
        )
    }
}
