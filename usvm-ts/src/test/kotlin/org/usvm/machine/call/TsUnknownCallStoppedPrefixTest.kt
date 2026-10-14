package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UBoolExpr
import org.usvm.UMachineOptions
import org.usvm.isTrue
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.UUnsatResult
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsUnknownCallStoppedPrefixTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/baseline/CallFallbackBaseline.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val fullScene = EtsScene(listOf(sourceFile))

    @Test
    fun `stop fallback reports a feasible live prefix before pruning`() {
        val observer = RecordingStoppedPathObserver()

        val states = analyzeAllStates(
            methodName = "declaredMethodWithoutBodyContinues",
            fallback = TsResidualCallPolicy.STOP_PATH,
            models = noModels,
            observer = observer,
        )

        assertTrue(states.isEmpty())
        val prefix = observer.paths.single()
        assertTrue(prefix.wasFeasible)
        assertFalse(prefix.state.pathConstraints.isFalse)
        assertTrue(prefix.state.callStack.isNotEmpty())
        val event = observer.events.single()
        val pendingCall = assertIs<TsConcreteMethodCallStmt>(prefix.state.currentStatement)
        assertEquals(expected = event.callSite, actual = pendingCall.returnSite)
        assertEquals(expected = event.callSite.location, actual = pendingCall.location)
        assertEquals(expected = event.callee, actual = pendingCall.callee.signature)
        assertTrue(prefix.reason.contains(TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE.name))
        assertEquals(expected = listOf("stopped-prefix", "PATH_STOPPED"), actual = observer.notifications)
    }

    @Test
    fun `partial stop callback preserves the feasible residual guard before model forking`() {
        val observer = RecordingStoppedPathObserver()
        var supportedGuard: UBoolExpr? = null
        val model = object : TestModel(id = "residual-prefix-model", methodName = "convert") {
            override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
                val condition = requireNotNull(call.arguments.single().resolved).asExpr(state.ctx.boolSort)
                supportedGuard = condition
                val successor = TsUnknownCallModelSuccessor(
                    guard = condition,
                    completion = TsUnknownCallModelCompletion.Normal {
                        requireNotNull(call.arguments.single().resolved)
                    },
                )
                return TsUnknownCallModelExecution(
                    successors = listOf(successor),
                    residualGuard = state.ctx.mkNot(condition),
                )
            }
        }
        val method = method(fullScene, "modeledUnknownCallForks")

        TsMachine(
            scene = fullScene,
            options = allStatesMachineOptions,
            tsOptions = TsOptions(unknownCallFallback = TsResidualCallPolicy.STOP_PATH),
            observer = observer,
            unknownCallModels = catalog(model),
        ).use { machine ->
            val states = machine.analyze(methods = listOf(method))

            assertEquals(expected = 1, actual = states.size)
            val prefix = observer.paths.single()
            assertTrue(prefix.wasFeasible)
            assertFalse(prefix.state.pathConstraints.isFalse)
            assertTrue(prefix.state.callStack.isNotEmpty())
            val residual = prefix.state.ctx.mkNot(requireNotNull(supportedGuard))
            assertTrue(prefix.state.models.isNotEmpty())
            assertTrue(prefix.state.models.all { it.eval(residual).isTrue })
            val feasibility = prefix.state.ctx.solver<EtsType>().check(prefix.state.pathConstraints)
            assertIs<USatResult<*>>(feasibility)

            val overlappingConstraints = prefix.state.pathConstraints.clone()
            overlappingConstraints += requireNotNull(supportedGuard)
            val overlap = prefix.state.ctx.solver<EtsType>().check(overlappingConstraints)
            assertIs<UUnsatResult<*>>(overlap)
            assertEquals(
                expected = listOf("stopped-prefix", "MODEL_APPLIED", "PATH_STOPPED"),
                actual = observer.notifications,
            )
        }
    }

    @Test
    fun `residual prefix discards cached models outside its guard`() {
        val observer = RecordingStoppedPathObserver()
        var residualGuard: UBoolExpr? = null
        var cachedModelOrder = emptyList<Boolean>()
        val model = object : TestModel(id = "cached-residual-prefix-model", methodName = "convert") {
            override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
                val value = requireNotNull(call.arguments.single().resolved)
                val condition = value.asExpr(state.ctx.boolSort)
                val residual = state.ctx.mkNot(condition)
                residualGuard = residual

                // Seed two feasible cached models deliberately in the wrong order for residual extraction.
                fun modelFor(guard: UBoolExpr): UModelBase<EtsType> {
                    val constraints = state.pathConstraints.clone()
                    constraints += guard
                    return when (val result = state.ctx.solver<EtsType>().check(constraints)) {
                        is USatResult -> result.model
                        else -> error("Both Boolean domains must be feasible in this dispatcher fixture")
                    }
                }
                val supportedModel = modelFor(guard = condition)
                val residualModel = modelFor(guard = residual)
                state.models = listOf(supportedModel, residualModel)
                cachedModelOrder = state.models.map { it.eval(condition).isTrue }

                return TsUnknownCallModelExecution(
                    successors = listOf(
                        TsUnknownCallModelSuccessor(
                            guard = condition,
                            completion = TsUnknownCallModelCompletion.Normal { value },
                        ),
                    ),
                    residualGuard = residual,
                )
            }
        }

        val states = analyzeAllStates(
            methodName = "modeledUnknownCallForks",
            fallback = TsResidualCallPolicy.STOP_PATH,
            models = catalog(model),
            observer = observer,
        )

        assertEquals(expected = listOf(true, false), actual = cachedModelOrder)
        assertEquals(expected = 1, actual = states.size)
        val prefix = observer.paths.single().state
        val guard = requireNotNull(residualGuard)
        assertTrue(prefix.models.isNotEmpty())
        assertTrue(prefix.models.first().eval(guard).isTrue)
        assertTrue(prefix.models.all { it.eval(guard).isTrue })
        assertEquals(expected = 1, actual = prefix.models.size)
    }

    @Test
    fun `unsatisfiable model residual never reports a stopped prefix`() {
        val observer = RecordingStoppedPathObserver()
        val model = object : TestModel(id = "unsat-residual-model", methodName = "convert") {
            override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
                val execution = ForkingModel.apply(state = state, call = call)
                return TsUnknownCallModelExecution(
                    successors = execution.successors,
                    residualGuard = state.ctx.falseExpr,
                )
            }
        }

        val states = analyzeAllStates(
            methodName = "modeledUnknownCallForks",
            fallback = TsResidualCallPolicy.STOP_PATH,
            models = catalog(model),
            observer = observer,
        )

        assertEquals(expected = 2, actual = states.size)
        assertTrue(observer.paths.isEmpty())
        assertEquals(expected = listOf("MODEL_APPLIED"), actual = observer.notifications)
    }

    @Test
    fun `throwing stopped path observer does not change fallback or partial exploration`() {
        val cases = listOf(
            ObservationFailureCase(
                fallback = TsResidualCallPolicy.STOP_PATH,
                models = noModels,
                expectedFinalStateCount = 0,
            ),
            ObservationFailureCase(
                fallback = TsResidualCallPolicy.STOP_PATH,
                models = catalog(SupportedTrueResidualFalseModel),
                expectedFinalStateCount = 1,
                methodName = "modeledUnknownCallForks",
            ),
        )
        for (case in cases) {
            var stoppedCount = 0
            val events = mutableListOf<TsUnknownCallEvent>()
            val observer = object : TsInterpreterObserver {
                override fun onPathStopped(state: TsState, reason: String) {
                    stoppedCount++
                    error("stopped-prefix observer failure")
                }

                override fun onUnknownCall(event: TsUnknownCallEvent) {
                    events += event
                }
            }

            val states = analyzeAllStates(
                methodName = case.methodName,
                fallback = case.fallback,
                models = case.models,
                observer = observer,
            )

            assertEquals(expected = case.expectedFinalStateCount, actual = states.size)
            assertEquals(expected = 1, actual = stoppedCount)
            assertEquals(expected = TsUnknownCallOutcome.PATH_STOPPED, actual = events.last().outcome)
        }
    }

    private fun method(
        scene: EtsScene,
        methodName: String,
        className: String = "CallFallbackBaseline",
    ): EtsMethod = scene.projectClasses
        .single { it.name == className }
        .methods
        .single { it.name == methodName }

    private fun analyzeAllStates(
        methodName: String,
        fallback: TsResidualCallPolicy = TsResidualCallPolicy.STOP_PATH,
        models: TsUnknownCallModelCatalog = noModels,
        observer: TsInterpreterObserver? = null,
    ): List<TsState> {
        val method = method(fullScene, methodName)
        return TsMachine(
            scene = fullScene,
            options = allStatesMachineOptions,
            tsOptions = TsOptions(unknownCallFallback = fallback),
            observer = observer,
            unknownCallModels = models,
        ).use { machine ->
            machine.analyze(listOf(method))
        }
    }

    private object ForkingModel : TestModel(id = "forking-model", methodName = "convert") {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
            val result = requireNotNull(call.arguments.single().resolved)
            val condition = result.asExpr(state.ctx.boolSort)
            val completion = TsUnknownCallModelCompletion.Normal { result }

            return TsUnknownCallModelExecution(
                successors = listOf(
                    TsUnknownCallModelSuccessor(
                        guard = condition,
                        completion = completion,
                    ),
                    TsUnknownCallModelSuccessor(
                        guard = state.ctx.mkNot(condition),
                        completion = completion,
                    ),
                ),
            )
        }
    }

    private object SupportedTrueResidualFalseModel : TestModel(id = "partial-model", methodName = "convert") {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
            val result = requireNotNull(call.arguments.single().resolved)
            val condition = result.asExpr(state.ctx.boolSort)
            val successor = TsUnknownCallModelSuccessor(
                guard = condition,
                completion = TsUnknownCallModelCompletion.Normal { result },
            )

            return TsUnknownCallModelExecution(
                successors = listOf(successor),
                residualGuard = state.ctx.mkNot(condition),
            )
        }
    }

    private abstract class TestModel(
        override val id: String,
        methodName: String,
        enclosingClassName: String? = null,
    ) : TsUnknownCallModel {
        override val target = TsUnknownCallTarget(methodName = methodName, enclosingClassName = enclosingClassName)
    }

    private class RecordingStoppedPathObserver : TsInterpreterObserver {
        val paths = mutableListOf<StoppedPathSnapshot>()
        val events = mutableListOf<TsUnknownCallEvent>()
        val notifications = mutableListOf<String>()

        override fun onPathStopped(state: TsState, reason: String) {
            val wasFeasible = !state.pathConstraints.isFalse
            paths += StoppedPathSnapshot(state = state.clone(), reason = reason, wasFeasible = wasFeasible)
            notifications += "stopped-prefix"
        }

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
            notifications += event.outcome.name
        }
    }

    private data class StoppedPathSnapshot(
        val state: TsState,
        val reason: String,
        val wasFeasible: Boolean,
    )

    private data class ObservationFailureCase(
        val fallback: TsResidualCallPolicy,
        val models: TsUnknownCallModelCatalog,
        val expectedFinalStateCount: Int,
        val methodName: String = "declaredMethodWithoutBodyContinues",
    )

    private companion object {
        val noModels = TsUnknownCallModelCatalog(emptyList())

        fun catalog(vararg models: TsUnknownCallModel): TsUnknownCallModelCatalog =
            TsUnknownCallModelCatalog(models.toList())

        val allStatesMachineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            exceptionsPropagation = true,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            stopOnTargetsReached = false,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
