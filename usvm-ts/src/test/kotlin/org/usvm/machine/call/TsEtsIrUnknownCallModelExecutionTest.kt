package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsEtsIrUnknownCallModelExecutionTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/EtsIrSemanticModelCalls.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))
    private val baseArtifact = loadEtsIrUnknownCallModelArtifact(
        sourcePath = getResourcePath("/models/EtsIrSemanticModels.ts"),
        entryPointClassName = "EtsIrSemanticModels",
        entryPointMethodName = "absolute",
    )
    private val modelClass = baseArtifact.file.allClasses.single { it.name == "EtsIrSemanticModels" }
    private val models = TsUnknownCallModelCatalog(
        models = listOf(
            model(
                id = "test.ets-ir.absolute",
                targetName = "absolute",
                entryPointName = "absolute",
            ),
            model(
                id = "test.ets-ir.increment",
                targetName = "modeledIncrement",
                entryPointName = "increment",
            ),
            model(
                id = "test.ets-ir.fail",
                targetName = "fail",
                entryPointName = "fail",
            ),
            model(
                id = "test.ets-ir.positive-identity",
                targetName = "positiveIdentity",
                entryPointName = "positiveIdentity",
                domainGuard = positiveInputGuard,
            ),
            model(
                id = "test.ets-ir.arity-mismatch",
                targetName = "arityMismatch",
                entryPointName = "positiveIdentity",
            ),
            model(
                id = "test.ets-ir.unresolved-argument",
                targetName = "unresolvedInput",
                entryPointName = "positiveIdentity",
            ),
            model(
                id = "test.ets-ir.outer",
                targetName = "outer",
                entryPointName = "outer",
            ),
            model(
                id = "test.ets-ir.double",
                targetName = "double",
                entryPointName = "double",
            ),
            model(
                id = "test.ets-ir.recursive",
                targetName = "recursive",
                entryPointName = "recurse",
            ),
        ),
    )

    @Test
    fun `pure EtsIR body maps argument and return value`() {
        val result = analyze(methodName = "pureArgumentAndReturn")

        assertEquals(42.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf("test.ets-ir.absolute"), result.modelIds)
    }

    @Test
    fun `stateful EtsIR body maps receiver argument state and return alias`() {
        val result = analyze(methodName = "receiverStateArgumentAndAlias")

        assertEquals(42.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf("test.ets-ir.increment"), result.modelIds)
    }

    @Test
    fun `exception from EtsIR body propagates through original call`() {
        val result = analyze(methodName = "exception")

        assertTrue(result.values.single() is TsTestValue.TsException)
        assertIs<TsMethodResult.TsException>(result.states.single().methodResult)
        assertEquals(1, result.states.single().localToSortStack.size)
        assertEquals(listOf("test.ets-ir.fail"), result.modelIds)
    }

    @Test
    fun `unsupported input uses configured residual fallback`() {
        val result = analyze(methodName = "unsupportedInput")

        assertTrue(result.states.isEmpty())
        assertEquals(listOf(TsUnknownCallOutcome.PATH_STOPPED), result.events.map { it.outcome })
        assertIs<TsUnknownCallDecision.ResidualFallback>(result.events.single().decision)
    }

    @Test
    fun `unresolved or arity mismatched inputs use configured residual fallback`() {
        val unsupportedMethods = listOf(
            "arityMismatch",
            "unresolvedArgument",
        )

        unsupportedMethods.forEach { methodName ->
            val result = analyze(methodName = methodName)

            assertEquals(
                listOf(TsUnknownCallOutcome.PATH_STOPPED),
                result.events.map { it.outcome },
                methodName,
            )
            assertIs<TsUnknownCallDecision.ResidualFallback>(result.events.single().decision, methodName)
        }
    }

    @Test
    fun `unknown call inside EtsIR body uses the same dispatcher`() {
        val result = analyze(methodName = "nestedUnknownCall")

        assertEquals(42.0, assertIs<TsTestValue.TsNumber>(result.values.single()).number)
        assertEquals(listOf("test.ets-ir.outer", "test.ets-ir.double"), result.modelIds)
    }

    @Test
    fun `recursive model redirection uses residual fallback instead of looping`() {
        val result = analyze(methodName = "recursiveRedirection")

        assertTrue(result.states.isEmpty())
        assertEquals(
            listOf(TsUnknownCallOutcome.MODEL_APPLIED, TsUnknownCallOutcome.PATH_STOPPED),
            result.events.map { it.outcome },
        )
        assertIs<TsUnknownCallDecision.ResidualFallback>(result.events.last().decision)
    }

    private fun model(
        id: String,
        targetName: String,
        entryPointName: String,
        domainGuard: TsEtsIrUnknownCallModelDomainGuard = TsEtsIrUnknownCallModelDomainGuard.ALWAYS,
    ): TsUnknownCallModel {
        val artifact = baseArtifact.copy(
            entryPoint = modelClass.methods.single { it.name == entryPointName },
        )

        return TsEtsIrUnknownCallModel(
            id = id,
            target = TsUnknownCallTarget(methodName = targetName),
            artifact = artifact,
            domainGuard = domainGuard,
        )
    }

    private fun analyze(methodName: String): AnalysisResult {
        val method = method(methodName)
        val observer = RecordingUnknownCallObserver()

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
            unknownCallModels = models,
        ).use { machine ->
            val states = machine.analyze(listOf(method))
            val values = states.map { state -> TsTestResolver().resolve(method, state).returnValue }

            AnalysisResult(
                states = states,
                values = values,
                events = observer.events.toList(),
            )
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "EtsIrSemanticModelCalls" }
        .methods
        .single { it.name == name }

    private class RecordingUnknownCallObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }
    }

    private data class AnalysisResult(
        val states: List<org.usvm.machine.state.TsState>,
        val values: List<TsTestValue>,
        val events: List<TsUnknownCallEvent>,
    ) {
        val modelIds: List<String>
            get() = events.mapNotNull { event ->
                (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId
            }
    }

    private companion object {
        val positiveInputGuard = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
            val zero = state.ctx.mkFp(0.0, state.ctx.fp64Sort)
            val value = inputs.single().asExpr(state.ctx.fp64Sort)
            state.ctx.mkFpLessExpr(zero, value)
        }

        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
