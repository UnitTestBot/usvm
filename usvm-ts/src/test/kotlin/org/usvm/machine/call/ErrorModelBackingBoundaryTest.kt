package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.expr.ERROR_MESSAGE_STORAGE_FIELD
import org.usvm.machine.expr.ERROR_NAME_STORAGE_FIELD
import org.usvm.machine.expr.ErrorStorageField
import org.usvm.machine.expr.mkErrorModelStorageLValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import org.usvm.util.mkFieldLValue
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ErrorModelBackingBoundaryTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/ErrorModelBackingBoundary.ts"),
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
    fun `modeled Error retains supported public name and message semantics`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "publicFields", className = "ErrorModelBackingBoundary"),
            { result -> result.value == "CustomError|after" },
            invariants = arrayOf({ result -> result.value == "CustomError|after" }),
        )
    }

    @Test
    fun `ordinary property named like Error message storage stays separate in memory`() {
        checkStorageIsolation(key = ErrorStorageField.MESSAGE, sourceName = ERROR_MESSAGE_STORAGE_FIELD)
    }

    @Test
    fun `ordinary property named like Error name storage stays separate in memory`() {
        checkStorageIsolation(key = ErrorStorageField.NAME, sourceName = ERROR_NAME_STORAGE_FIELD)
    }

    // This deliberately tests field-region isolation after source-created Error initialization.
    // It does not claim support for adding arbitrary own properties through the TypeScript interpreter.
    private fun checkStorageIsolation(key: ErrorStorageField, sourceName: String) {
        val method = getMethod(methodName = "allocatedError", className = "ErrorModelBackingBoundary")
        TsMachine(scene = scene, options = UMachineOptions(), tsOptions = TsOptions()).use { machine ->
            val state = machine.analyze(listOf(method)).single()
            val result = assertIs<TsMethodResult.Success>(state.methodResult)
            val reference = result.value.asExpr(state.ctx.addressSort)
            val modelSlot = mkErrorModelStorageLValue(state.ctx.addressSort, reference, key)
            val ordinarySlot = mkFieldLValue(state.ctx.addressSort, reference, sourceName)
            val original = state.memory.read(modelSlot)
            val userValue = state.memory.allocConcrete(EtsStringType)

            state.memory.write(ordinarySlot, userValue, guard = state.ctx.trueExpr)

            assertEquals(original, state.memory.read(modelSlot))
            assertEquals(userValue, state.memory.read(ordinarySlot))
        }
    }
}
