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

class ErrorModelOwnerBoundaryTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/error-storage-owner/ErrorModels.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet())),
        ).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    @Test
    fun `source ErrorValue in ErrorModels keeps ordinary self field storage`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "ordinarySelfFields", className = "ErrorValue"),
            { result -> result.value == "user name|user message" },
            invariants = arrayOf({ result -> result.value == "user name|user message" }),
        )
    }

    @Test
    fun `source ErrorValue fields remain visible through any alias`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "ordinarySelfFieldsThroughAny", className = "ErrorValue"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }
}
