package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UConcreteHeapRef
import org.usvm.UMachineOptions
import org.usvm.api.mockMethodCall
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.newStmt
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsUnknownCallDispatcherTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/baseline/CallFallbackBaseline.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val fullScene = EtsScene(listOf(sourceFile))

    @Test
    fun `every profile decision is reported through the interpreter observer`() {
        val cases = listOf(
            ObservationCase(
                profile = TsUnknownCallProfiles.MODELS_THEN_STOP,
                modelProvider = TsNoUnknownCallModels,
                outcome = TsUnknownCallOutcome.PATH_STOPPED,
                decision = TsUnknownCallDecision.ResidualFallback(
                    policy = TsResidualCallPolicy.STOP_PATH,
                    reason = TsUnknownCallResidualReason.MODEL_NOT_APPLICABLE,
                ),
                finalStateCount = 0,
            ),
            ObservationCase(
                profile = TsUnknownCallProfiles.FRESH_SYMBOLIC_FOR_ALL,
                modelProvider = TsNoUnknownCallModels,
                outcome = TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN,
                decision = TsUnknownCallDecision.ResidualFallback(
                    policy = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
                    reason = TsUnknownCallResidualReason.MODEL_LOOKUP_DISABLED,
                ),
                finalStateCount = 1,
            ),
            ObservationCase(
                profile = TsUnknownCallProfiles.MODELS_THEN_FRESH_SYMBOLIC,
                modelProvider = ApplyingModelProvider,
                outcome = TsUnknownCallOutcome.MODEL_APPLIED,
                decision = TsUnknownCallDecision.ModelApplied(modelId = "applying-model"),
                finalStateCount = 1,
            ),
        )

        cases.forEach { case ->
            val observer = RecordingUnknownCallObserver()
            val states = analyzeAllStates(
                methodName = "declaredMethodWithoutBodyContinues",
                profile = case.profile,
                modelProvider = case.modelProvider,
                observer = observer,
            )

            assertEquals(case.finalStateCount, states.size, case.profile.toString())
            val event = observer.events.single()
            assertEquals("declaredMethodWithoutBodyContinues", event.callSite.location.method.name)
            assertEquals("external", event.callee.name)
            assertEquals(TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE, event.failureReason)
            assertEquals(case.profile, event.profile)
            assertEquals(case.outcome, event.outcome)
            assertEquals(case.decision, event.decision)
        }
    }

    @Test
    fun `model decision is reported once when the model forks`() {
        val observer = RecordingUnknownCallObserver()
        val states = analyzeAllStates(
            methodName = "modeledUnknownCallForks",
            profile = TsUnknownCallProfiles.MODELS_THEN_STOP,
            modelProvider = ForkingModelProvider,
            observer = observer,
        )

        assertEquals(2, states.size)
        val event = observer.events.single()
        assertEquals(TsUnknownCallOutcome.MODEL_APPLIED, event.outcome)
    }

    @Test
    fun `throwing observer cannot change fresh or modeled exploration`() {
        val cases = listOf(
            ObservationFailureCase(
                profile = TsUnknownCallProfiles.FRESH_SYMBOLIC_FOR_ALL,
                modelProvider = TsNoUnknownCallModels,
                expectedFinalStateCount = 1,
            ),
            ObservationFailureCase(
                profile = TsUnknownCallProfiles.MODELS_THEN_STOP,
                modelProvider = ForkingModelProvider,
                expectedFinalStateCount = 2,
                methodName = "modeledUnknownCallForks",
            ),
        )

        cases.forEach { case ->
            val states = analyzeAllStates(
                methodName = case.methodName,
                profile = case.profile,
                modelProvider = case.modelProvider,
                observer = ThrowingUnknownCallObserver,
            )

            assertEquals(case.expectedFinalStateCount, states.size, case.profile.toString())
        }
    }

    @Test
    fun `applied model decisions require non blank identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelApplication.Applied(modelId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            TsUnknownCallDecision.ModelApplied(modelId = "")
        }
    }

    @Test
    fun `profiles select model lookup independently from residual fallback`() {
        val cases = listOf(
            ProfileCase(
                profile = TsUnknownCallProfiles.STOP_ALL,
                withoutModel = ProfileResult(
                    reachesReturn = false,
                    outcome = TsUnknownCallOutcome.PATH_STOPPED,
                ),
                withModel = ProfileResult(
                    reachesReturn = false,
                    outcome = TsUnknownCallOutcome.PATH_STOPPED,
                ),
            ),
            ProfileCase(
                profile = TsUnknownCallProfiles.FRESH_SYMBOLIC_FOR_ALL,
                withoutModel = ProfileResult(
                    reachesReturn = true,
                    outcome = TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN,
                ),
                withModel = ProfileResult(
                    reachesReturn = true,
                    outcome = TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN,
                ),
            ),
            ProfileCase(
                profile = TsUnknownCallProfiles.MODELS_THEN_STOP,
                withoutModel = ProfileResult(
                    reachesReturn = false,
                    outcome = TsUnknownCallOutcome.PATH_STOPPED,
                ),
                withModel = ProfileResult(
                    reachesReturn = true,
                    outcome = TsUnknownCallOutcome.MODEL_APPLIED,
                ),
            ),
            ProfileCase(
                profile = TsUnknownCallProfiles.MODELS_THEN_FRESH_SYMBOLIC,
                withoutModel = ProfileResult(
                    reachesReturn = true,
                    outcome = TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN,
                ),
                withModel = ProfileResult(
                    reachesReturn = true,
                    outcome = TsUnknownCallOutcome.MODEL_APPLIED,
                ),
            ),
        )

        cases.forEach { case ->
            assertEquals(case.withoutModel, runProfile(case.profile, TsNoUnknownCallModels), case.profile.toString())
            assertEquals(case.withModel, runProfile(case.profile, ApplyingModelProvider), case.profile.toString())
        }
    }

    @Test
    fun `TsOptions profile configures the machine dispatcher`() {
        assertEquals(TsUnknownCallProfiles.MODELS_THEN_STOP, TsOptions().unknownCallProfile)
        assertTrue(TsOptions().unknownCallProfile.residualOverrides.isEmpty())

        assertFalse(reachesReturn("declaredMethodWithoutBodyContinues"))
        assertTrue(
            reachesReturn(
                "declaredMethodWithoutBodyContinues",
                tsOptions = TsOptions(unknownCallProfile = TsUnknownCallProfiles.FRESH_SYMBOLIC_FOR_ALL),
            )
        )
    }

    @Test
    fun `explicit family override replaces the profile residual fallback`() {
        val family = method(fullScene, "declaredMethodWithoutBodyContinues")
            .cfg
            .stmts
            .mapNotNull { it.callExpr }
            .single { it.callee.name == "external" }
            .callee
            .enclosingClass
        val profile = TsUnknownCallProfiles.STOP_ALL.copy(
            residualOverrides = mapOf(
                family to TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
            )
        )

        assertTrue(
            reachesReturn(
                "declaredMethodWithoutBodyContinues",
                tsOptions = TsOptions(unknownCallProfile = profile),
            )
        )
    }

    @Test
    fun `fresh symbolic return uses the source call result type`() {
        val dispatcher = RecordingResultSortDispatcher(
            TsProfileUnknownCallDispatcher(
                TsUnknownCallProfiles.FRESH_SYMBOLIC_FOR_ALL,
                TsNoUnknownCallModels,
            )
        )

        assertTrue(reachesReturn("overloadedDeclaredMethodWithoutBodyContinues", dispatcher = dispatcher))
        assertTrue(dispatcher.resultSortMatches.isNotEmpty())
        assertTrue(dispatcher.resultSortMatches.all { it })
    }

    @Test
    fun `inventoried unknown calls use normalized compatibility dispatch`() {
        val cases = listOf(
            Case(
                "declaredMethodWithoutBodyContinues",
                TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
                reachesReturn = true,
            ),
            Case(
                "allocatedReceiverWithoutMethodContinues",
                TsUnknownCallFailureReason.NO_SUITABLE_VIRTUAL_TARGET,
                reachesReturn = true,
            ),
            Case(
                "unresolvedStaticCallPrunes",
                TsUnknownCallFailureReason.STATIC_METHOD_NOT_FOUND,
                reachesReturn = false,
                sceneWithout = "ExternalStatic",
            ),
            Case(
                "unresolvedVirtualCallPrunes",
                TsUnknownCallFailureReason.VIRTUAL_METHOD_NOT_FOUND,
                reachesReturn = false,
                sceneWithout = "ExternalReceiver",
            ),
            Case(
                "unresolvedAllocatedReceiverCallPrunes",
                listOf(
                    TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
                    TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
                ),
                reachesReturn = false,
                sceneWithout = "ExternalReceiver",
            ),
            Case(
                "nonReferenceInstanceCallPrunes",
                TsUnknownCallFailureReason.NON_REFERENCE_RECEIVER,
                reachesReturn = false,
            ),
            Case(
                "unresolvedConstructorContinues",
                TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
                reachesReturn = true,
                sceneWithout = "ExternalReceiver",
            ),
            Case(
                "unresolvedAnyPointerCallPrunes",
                TsUnknownCallFailureReason.POINTER_TARGET_NOT_FOUND,
                reachesReturn = false,
            ),
            Case(
                "nonReferencePointerCallContinues",
                TsUnknownCallFailureReason.NON_REFERENCE_POINTER,
                reachesReturn = true,
            ),
            Case(
                "intraproceduralAssignmentCallContinues",
                TsUnknownCallFailureReason.INTERPROCEDURAL_ANALYSIS_DISABLED,
                reachesReturn = true,
                tsOptions = TsOptions(interproceduralAnalysis = false),
            ),
            Case(
                "intraproceduralCallStatementContinues",
                TsUnknownCallFailureReason.INTERPROCEDURAL_ANALYSIS_DISABLED,
                reachesReturn = true,
                tsOptions = TsOptions(interproceduralAnalysis = false),
            ),
            Case(
                "logCallSkipsBody",
                TsUnknownCallFailureReason.LOGGING_CALL,
                reachesReturn = true,
            ),
            Case(
                "booleanConverterPrunes",
                TsUnknownCallFailureReason.POINTER_TARGET_NOT_FOUND,
                reachesReturn = false,
            ),
        )

        cases.forEach { case ->
            val scene = case.sceneWithout?.let(::sceneWithout) ?: fullScene
            val dispatcher = RecordingUnknownCallDispatcher()

            assertEquals(
                case.reachesReturn,
                reachesReturn(case.methodName, scene, case.tsOptions, dispatcher),
                case.methodName,
            )
            assertEquals(
                case.reasons,
                dispatcher.calls.map { it.failureReason },
                case.methodName,
            )
        }
    }

    @Test
    fun `descriptor keeps typed call data without eagerly resolving arguments`() {
        val dispatcher = RecordingUnknownCallDispatcher()
        val scene = sceneWithout("ExternalStatic")

        assertFalse(reachesReturn("unresolvedStaticCallPrunes", scene, dispatcher = dispatcher))

        val call = dispatcher.calls.single()
        assertEquals("external", call.callee.name)
        assertNull(call.receiver)
        assertTrue(call.arguments.isEmpty())
        assertIs<EtsVoidType>(call.resultType)
        assertEquals("unresolvedStaticCallPrunes", call.callSite.location.method.name)
    }

    @Test
    fun `descriptor preserves source and resolved values available at dispatch`() {
        val dispatcher = RecordingUnknownCallDispatcher()

        assertFalse(reachesReturn("nonReferenceInstanceCallPrunes", dispatcher = dispatcher))

        val call = dispatcher.calls.single()
        val receiver = assertNotNull(call.receiver)
        assertEquals("receiver", assertIs<EtsLocal>(receiver.source).name)
        assertNotNull(receiver.resolved)
        assertTrue(call.arguments.isEmpty())
    }

    @Test
    fun `normally executable and compatibility-approximated calls bypass unknown dispatch`() {
        val methods = listOf(
            // The native frontend gives this call a concrete executable target despite the legacy baseline name.
            "anyReceiverWithKnownMethodContinues",
            "loggerCallSkipsBody",
            "toStringUsesPlaceholder",
            "valueOfReturnsReceiver",
            "mathFloorRoundsTowardNegativeInfinity",
            "resourceLookupSkipsBody",
        )

        methods.forEach { methodName ->
            val dispatcher = RecordingUnknownCallDispatcher()

            assertTrue(reachesReturn(methodName, dispatcher = dispatcher), methodName)
            assertTrue(dispatcher.calls.isEmpty(), methodName)
        }
    }

    @Test
    fun `pre-call allocation failures are documented dispatcher exclusions`() {
        val dispatcher = RecordingUnknownCallDispatcher()

        assertFalse(reachesReturn("booleanConstructorUsesTruthiness", dispatcher = dispatcher))
        assertTrue(dispatcher.calls.isEmpty())
    }

    @Test
    fun `pointer descriptor pairs its source with the resolved function pointer`() {
        val dispatcher = RecordingUnknownCallDispatcher()
        val pointerCall = method(fullScene, "associatedLoggingPointerContinues", className = "Log")
            .cfg
            .stmts
            .mapNotNull { it.callExpr }
            .filterIsInstance<EtsPtrCallExpr>()
            .single()

        assertTrue(
            reachesReturn(
                "associatedLoggingPointerContinues",
                dispatcher = dispatcher,
                className = "Log",
            )
        )

        val call = dispatcher.calls.single { it.callSite.location.method.name == "associatedLoggingPointerContinues" }
        assertEquals(TsUnknownCallFailureReason.LOGGING_CALL, call.failureReason)
        assertEquals(pointerCall.ptr, assertNotNull(call.receiver).source)
        assertEquals(true, dispatcher.receiverIsAssociatedFunction.single { it != null })
    }

    @Test
    fun `descriptor result type comes from the source overload`() {
        val dispatcher = RecordingUnknownCallDispatcher()

        assertTrue(reachesReturn("overloadedDeclaredMethodWithoutBodyContinues", dispatcher = dispatcher))

        val calls = dispatcher.calls.filter {
            it.callSite.location.method.name == "overloadedDeclaredMethodWithoutBodyContinues"
        }
        assertTrue(calls.isNotEmpty())
        assertTrue(calls.all { it.resultType == EtsNumberType })
        assertTrue(calls.any { it.callee.returnType != it.resultType })
    }

    private fun reachesReturn(
        methodName: String,
        scene: EtsScene = fullScene,
        tsOptions: TsOptions = TsOptions(),
        dispatcher: TsUnknownCallDispatcher? = null,
        modelProvider: TsUnknownCallModelProvider = TsNoUnknownCallModels,
        className: String = "CallFallbackBaseline",
    ): Boolean = returnStatement(scene, methodName, className) in
        reachedStatements(methodName, scene, tsOptions, dispatcher, modelProvider, className)

    private fun reachedStatements(
        methodName: String,
        scene: EtsScene,
        tsOptions: TsOptions,
        dispatcher: TsUnknownCallDispatcher?,
        modelProvider: TsUnknownCallModelProvider,
        className: String,
    ): Set<EtsStmt> {
        val method = method(scene, methodName, className)
        val returnStatement = returnStatement(scene, methodName, className)
        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        initialTarget.addChild(TsReachabilityTarget.FinalPoint(returnStatement))

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            machineObserver = ReachabilityObserver(),
            unknownCallDispatcher = dispatcher,
            unknownCallModelProvider = modelProvider,
        ).use { machine ->
            machine.analyze(listOf(method), listOf(initialTarget))
                .flatMapTo(mutableSetOf()) { state -> state.pathNode.allStatements }
        }
    }

    private fun returnStatement(scene: EtsScene, methodName: String, className: String): EtsReturnStmt =
        method(scene, methodName, className).cfg.stmts.filterIsInstance<EtsReturnStmt>().single()

    private fun method(
        scene: EtsScene,
        methodName: String,
        className: String = "CallFallbackBaseline",
    ): EtsMethod = scene.projectClasses
        .single { it.name == className }
        .methods
        .single { it.name == methodName }

    private fun sceneWithout(className: String): EtsScene {
        val filteredFile = EtsFile(
            signature = sourceFile.signature,
            classes = sourceFile.classes.filterNot { it.name == className },
            namespaces = sourceFile.namespaces,
            importInfos = sourceFile.importInfos,
            exportInfos = sourceFile.exportInfos,
        )
        return EtsScene(listOf(filteredFile))
    }

    private fun analyzeAllStates(
        methodName: String,
        profile: TsUnknownCallProfile,
        modelProvider: TsUnknownCallModelProvider = TsNoUnknownCallModels,
        observer: TsInterpreterObserver? = null,
    ): List<TsState> {
        val method = method(fullScene, methodName)
        return TsMachine(
            scene = fullScene,
            options = allStatesMachineOptions,
            tsOptions = TsOptions(unknownCallProfile = profile),
            observer = observer,
            unknownCallModelProvider = modelProvider,
        ).use { machine ->
            machine.analyze(listOf(method))
        }
    }

    private class RecordingUnknownCallDispatcher : TsUnknownCallDispatcher {
        val calls = mutableListOf<TsUnknownCall>()
        val receiverIsAssociatedFunction = mutableListOf<Boolean?>()

        override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
            calls += call
            val receiver = call.receiver?.resolved as? UConcreteHeapRef
            receiverIsAssociatedFunction += receiver?.let { resolved ->
                scope.calcOnState { associatedFunction[resolved] != null }
            }
            return TsCompatibilityUnknownCallDispatcher.dispatch(scope, call)
        }
    }

    private fun runProfile(
        profile: TsUnknownCallProfile,
        modelProvider: TsUnknownCallModelProvider,
    ): ProfileResult {
        val dispatcher = RecordingOutcomeDispatcher(TsProfileUnknownCallDispatcher(profile, modelProvider))
        val reachesReturn = reachesReturn(
            "declaredMethodWithoutBodyContinues",
            dispatcher = dispatcher,
        )
        return ProfileResult(reachesReturn, dispatcher.outcomes.single())
    }

    private class RecordingOutcomeDispatcher(
        private val delegate: TsUnknownCallDispatcher,
    ) : TsUnknownCallDispatcher {
        val outcomes = mutableListOf<TsUnknownCallOutcome>()

        override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome =
            delegate.dispatch(scope, call).also(outcomes::add)
    }

    private class RecordingResultSortDispatcher(
        private val delegate: TsUnknownCallDispatcher,
    ) : TsUnknownCallDispatcher {
        val resultSortMatches = mutableListOf<Boolean>()

        override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
            val outcome = delegate.dispatch(scope, call)
            if (outcome == TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN) {
                resultSortMatches += scope.calcOnState {
                    val result = methodResult as TsMethodResult.Success.MockedCall
                    result.value.sort == ctx.typeToSort(call.resultType)
                }
            }
            return outcome
        }
    }

    private object ApplyingModelProvider : TsUnknownCallModelProvider {
        override fun apply(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallModelApplication {
            mockMethodCall(scope, call.callee, call.resultType)
            scope.doWithState { newStmt(call.callSite) }
            return TsUnknownCallModelApplication.Applied(modelId = "applying-model")
        }
    }

    private object ForkingModelProvider : TsUnknownCallModelProvider {
        override fun apply(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallModelApplication {
            val result = requireNotNull(call.arguments.single().resolved)
            val condition = scope.calcOnState { result.asExpr(ctx.boolSort) }
            val completeCall: TsState.() -> Unit = {
                methodResult = TsMethodResult.Success.MockedCall(result, call.callee)
                newStmt(call.callSite)
            }
            scope.fork(
                condition = condition,
                blockOnTrueState = completeCall,
                blockOnFalseState = completeCall,
            )
            return TsUnknownCallModelApplication.Applied(modelId = "forking-model")
        }
    }

    private class RecordingUnknownCallObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }
    }

    private object ThrowingUnknownCallObserver : TsInterpreterObserver {
        override fun onUnknownCall(event: TsUnknownCallEvent) {
            error("observer failure")
        }
    }

    private data class ProfileCase(
        val profile: TsUnknownCallProfile,
        val withoutModel: ProfileResult,
        val withModel: ProfileResult,
    )

    private data class ProfileResult(
        val reachesReturn: Boolean,
        val outcome: TsUnknownCallOutcome,
    )

    private data class ObservationCase(
        val profile: TsUnknownCallProfile,
        val modelProvider: TsUnknownCallModelProvider,
        val outcome: TsUnknownCallOutcome,
        val decision: TsUnknownCallDecision,
        val finalStateCount: Int,
    )

    private data class ObservationFailureCase(
        val profile: TsUnknownCallProfile,
        val modelProvider: TsUnknownCallModelProvider,
        val expectedFinalStateCount: Int,
        val methodName: String = "declaredMethodWithoutBodyContinues",
    )

    private data class Case(
        val methodName: String,
        val reasons: List<TsUnknownCallFailureReason>,
        val reachesReturn: Boolean,
        val sceneWithout: String? = null,
        val tsOptions: TsOptions = TsOptions(),
    ) {
        constructor(
            methodName: String,
            reason: TsUnknownCallFailureReason,
            reachesReturn: Boolean,
            sceneWithout: String? = null,
            tsOptions: TsOptions = TsOptions(),
        ) : this(methodName, listOf(reason), reachesReturn, sceneWithout, tsOptions)
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            exceptionsPropagation = true,
            stopOnTargetsReached = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        val allStatesMachineOptions = machineOptions.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            stopOnTargetsReached = false,
        )
    }
}
