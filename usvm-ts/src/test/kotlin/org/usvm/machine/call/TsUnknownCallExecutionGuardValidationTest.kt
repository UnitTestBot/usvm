package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.solver.UUnknownResult
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration

class TsUnknownCallExecutionGuardValidationTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/baseline/CallFallbackBaseline.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `overlapping model successor guards are rejected`() {
        assertInvalidModel(
            methodName = "declaredMethodWithoutBodyContinues",
            profile = TsUnknownCallProfiles.MODELS_THEN_STOP,
            modelProvider = OverlappingSuccessorsModelProvider,
            expectedMessage = "Semantic model overlapping-successors produced overlapping guards: " +
                "successor[0], successor[1]",
        )
    }

    @Test
    fun `overlapping model successor and residual guards are rejected`() {
        assertInvalidModel(
            methodName = "declaredMethodWithoutBodyContinues",
            profile = TsUnknownCallProfiles.MODELS_THEN_FRESH_SYMBOLIC,
            modelProvider = OverlappingResidualModelProvider,
            expectedMessage = "Semantic model overlapping-residual produced overlapping guards: successor[0], residual",
        )
    }

    @Test
    fun `exact model successor guards must cover the current call domain`() {
        assertInvalidModel(
            methodName = "modeledUnknownCallForks",
            profile = TsUnknownCallProfiles.MODELS_THEN_STOP,
            modelProvider = IncompleteExactModelProvider,
            expectedMessage = "Semantic model incomplete-exact guards do not cover the current call domain",
        )
    }

    @Test
    fun `partial model successor and residual guards must cover the current call domain`() {
        assertInvalidModel(
            methodName = "modeledUnknownCallForks",
            profile = TsUnknownCallProfiles.MODELS_THEN_FRESH_SYMBOLIC,
            modelProvider = IncompletePartialModelProvider,
            expectedMessage = "Semantic model incomplete-partial guards do not cover the current call domain",
        )
    }

    @Test
    fun `unknown solver result cannot validate execution guards`() {
        val exception = assertFailsWith<IllegalStateException> {
            UUnknownResult<Nothing>().requireConclusiveGuardValidation(modelId = "unknown-guards")
        }

        assertEquals(
            "Semantic model unknown-guards guards could not be validated: solver returned UNKNOWN",
            exception.message,
        )
    }

    private fun assertInvalidModel(
        methodName: String,
        profile: TsUnknownCallProfile,
        modelProvider: TsUnknownCallModelProvider,
        expectedMessage: String,
    ) {
        val exception = assertFailsWith<IllegalStateException> {
            analyzeAllStates(
                methodName = methodName,
                profile = profile,
                modelProvider = modelProvider,
            )
        }

        assertEquals(expectedMessage, exception.message)
    }

    private fun analyzeAllStates(
        methodName: String,
        profile: TsUnknownCallProfile,
        modelProvider: TsUnknownCallModelProvider,
    ): List<TsState> {
        val method = method(methodName)

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(unknownCallProfile = profile),
            unknownCallModelProvider = modelProvider,
        ).use { machine ->
            machine.analyze(listOf(method))
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "CallFallbackBaseline" }
        .methods
        .single { it.name == name }

    private object OverlappingSuccessorsModelProvider : TsUnknownCallModelProvider {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication {
            val completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() }

            return TsUnknownCallModelApplication.Applied(
                modelId = "overlapping-successors",
                precision = TsUnknownCallModelPrecision.EXACT,
                execution = TsUnknownCallModelExecution(
                    successors = listOf(
                        TsUnknownCallModelSuccessor(guard = state.ctx.trueExpr, completion = completion),
                        TsUnknownCallModelSuccessor(guard = state.ctx.trueExpr, completion = completion),
                    ),
                    residualGuard = null,
                ),
            )
        }
    }

    private object OverlappingResidualModelProvider : TsUnknownCallModelProvider {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication {
            val successor = TsUnknownCallModelSuccessor(
                guard = state.ctx.trueExpr,
                completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
            )

            return TsUnknownCallModelApplication.Applied(
                modelId = "overlapping-residual",
                precision = TsUnknownCallModelPrecision.PARTIAL,
                execution = TsUnknownCallModelExecution(
                    successors = listOf(successor),
                    residualGuard = state.ctx.trueExpr,
                ),
            )
        }
    }

    private object IncompleteExactModelProvider : TsUnknownCallModelProvider {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication {
            val condition = requireNotNull(call.arguments.single().resolved).asExpr(state.ctx.boolSort)
            val successor = TsUnknownCallModelSuccessor(
                guard = condition,
                completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
            )

            return TsUnknownCallModelApplication.Applied(
                modelId = "incomplete-exact",
                precision = TsUnknownCallModelPrecision.EXACT,
                execution = TsUnknownCallModelExecution(
                    successors = listOf(successor),
                    residualGuard = null,
                ),
            )
        }
    }

    private object IncompletePartialModelProvider : TsUnknownCallModelProvider {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication {
            val condition = requireNotNull(call.arguments.single().resolved).asExpr(state.ctx.boolSort)
            val successor = TsUnknownCallModelSuccessor(
                guard = condition,
                completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
            )

            return TsUnknownCallModelApplication.Applied(
                modelId = "incomplete-partial",
                precision = TsUnknownCallModelPrecision.PARTIAL,
                execution = TsUnknownCallModelExecution(
                    successors = listOf(successor),
                    residualGuard = state.ctx.falseExpr,
                ),
            )
        }
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            stopOnCoverage = 0,
            stopOnTargetsReached = false,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
            throwExceptionOnStepFailure = true,
        )
    }
}
