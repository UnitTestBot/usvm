package org.usvm.machine.expr

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

class ArrayStorageTypeBoundaryTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ArrayStorageTypeBoundary.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(projectFiles = listOf(sourceFile))

    @Test
    fun `unsupported indexed access and typed array length report limitations`() {
        val methods = mapOf(
            "readUnknown" to "unknown",
            "writeUnknown" to "unknown",
            "readUint16" to "Uint16Array",
            "lengthUint8" to "Uint8Array",
        )

        methods.forEach { (methodName, expectedType) ->
            val observer = RecordingObserver()
            val method = scene.projectClasses
                .single { it.name == "ArrayStorageTypeBoundary" }
                .methods
                .single { it.name == methodName }

            val states = TsMachine(
                scene = scene,
                options = machineOptions,
                tsOptions = TsOptions(),
                observer = observer,
            ).use { machine ->
                machine.analyze(methods = listOf(method))
            }

            assertTrue(states.none { it.methodResult is TsMethodResult.Success }, methodName)
            assertTrue(observer.limitations.isNotEmpty(), methodName)
            assertTrue(
                observer.limitations.all { it.reason == TsRuntimeFeatureLimitationReason.ARRAY_STORAGE_TYPE },
                "$methodName: ${observer.limitations.map { it.reason }}",
            )
            assertTrue(
                observer.limitations.any { expectedType in it.detail },
                "$methodName: ${observer.limitations.map { it.detail }}",
            )
        }
    }

    private class RecordingObserver : TsInterpreterObserver {
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()

        override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
            limitations += event
        }
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 20_000L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
