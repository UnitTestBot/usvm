package org.usvm.machine.call

import io.ksmt.utils.asExpr
import io.mockk.mockk
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.isTrue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.solver.USatResult
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
    fun `every model or fallback decision is reported through the interpreter observer`() {
        val cases = listOf(
            ObservationCase(
                fallback = TsResidualCallPolicy.STOP_PATH,
                models = noModels,
                outcome = TsUnknownCallOutcome.PATH_STOPPED,
                decision = TsUnknownCallDecision.ResidualFallback(TsResidualCallPolicy.STOP_PATH),
                finalStateCount = 0,
            ),
            ObservationCase(
                fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
                models = noModels,
                outcome = TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN,
                decision = TsUnknownCallDecision.ResidualFallback(TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
                finalStateCount = 1,
            ),
            ObservationCase(
                fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
                models = catalog(ApplyingModel),
                outcome = TsUnknownCallOutcome.MODEL_APPLIED,
                decision = TsUnknownCallDecision.ModelApplied(modelId = "applying-model"),
                finalStateCount = 1,
            ),
        )

        cases.forEach { case ->
            val observer = RecordingUnknownCallObserver()
            val states = analyzeAllStates(
                methodName = "declaredMethodWithoutBodyContinues",
                fallback = case.fallback,
                models = case.models,
                observer = observer,
            )

            assertEquals(case.finalStateCount, states.size, case.fallback.toString())
            val event = observer.events.single()
            assertEquals("declaredMethodWithoutBodyContinues", event.callSite.location.method.name)
            assertEquals("external", event.callee.name)
            assertEquals(TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE, event.failureReason)
            assertEquals(case.outcome, event.outcome)
            assertEquals(case.decision, event.decision)
        }
    }

    @Test
    fun `model decision is reported once when the model forks`() {
        val observer = RecordingUnknownCallObserver()
        val states = analyzeAllStates(
            methodName = "modeledUnknownCallForks",
            models = catalog(ForkingModel),
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
                fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
                models = noModels,
                expectedFinalStateCount = 1,
            ),
            ObservationFailureCase(
                fallback = TsResidualCallPolicy.STOP_PATH,
                models = catalog(ForkingModel),
                expectedFinalStateCount = 2,
                methodName = "modeledUnknownCallForks",
            ),
        )

        cases.forEach { case ->
            val states = analyzeAllStates(
                methodName = case.methodName,
                fallback = case.fallback,
                models = case.models,
                observer = ThrowingUnknownCallObserver,
            )

            assertEquals(case.expectedFinalStateCount, states.size, case.fallback.toString())
        }
    }

    @Test
    fun `applied model decisions require non blank identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelApplication.Applied(
                modelId = " ",
                execution = completeExecution(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TsUnknownCallDecision.ModelApplied(modelId = "")
        }
    }

    @Test
    fun `model execution plans require at least one successor`() {
        val error = assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelExecution(
                successors = emptyList(),
                residualGuard = mockk(),
            )
        }

        assertEquals("A semantic model must declare at least one guarded successor", error.message)
    }

    @Test
    fun `fresh fallback preserves all fake value representations`() {
        assertFreshResultPreservesAllFakeRepresentations(
            fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
        )
    }

    @Test
    fun `partial residual fallback preserves all fake value representations`() {
        assertFreshResultPreservesAllFakeRepresentations(
            fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
            models = catalog(UnsupportedPartialModel),
        )
    }

    @Test
    fun `partial model sends only residual domain to fresh fallback`() {
        val observer = RecordingUnknownCallObserver()
        val states = analyzeAllStates(
            methodName = "modeledUnknownCallForks",
            fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
            models = catalog(SupportedTrueResidualFalseModel),
            observer = observer,
        )

        assertEquals(2, states.size)
        assertEquals(
            listOf(TsUnknownCallOutcome.MODEL_APPLIED, TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN),
            observer.events.map { it.outcome },
        )
    }

    @Test
    fun `partial model sends residual domain to stop fallback`() {
        val observer = RecordingUnknownCallObserver()
        val states = analyzeAllStates(
            methodName = "modeledUnknownCallForks",
            models = catalog(SupportedTrueResidualFalseModel),
            observer = observer,
        )

        assertEquals(1, states.size)
        assertEquals(
            listOf(TsUnknownCallOutcome.MODEL_APPLIED, TsUnknownCallOutcome.PATH_STOPPED),
            observer.events.map { it.outcome },
        )
    }

    @Test
    fun `exceptional model successor preserves exception state`() {
        val states = analyzeAllStates(
            methodName = "modeledUnknownCallThrows",
            models = catalog(ExceptionalModel),
        )

        assertIs<TsMethodResult.TsException>(states.single().methodResult)
    }

    @Test
    fun `stateful model can return an existing reference alias`() {
        val states = analyzeAllStates(
            methodName = "modeledUnknownCallReturnsAlias",
            models = catalog(StatefulAliasModel),
        )
        val aliasReturn = method(fullScene, "modeledUnknownCallReturnsAlias")
            .cfg
            .stmts
            .filterIsInstance<EtsReturnStmt>()
            .first()

        val state = states.single()
        assertTrue(aliasReturn in state.pathNode.allStatements)
        assertTrue(STATE_CHANGE_MARKER in state.addedArtificialLocals)
    }

    @Test
    fun `TsOptions configures one fallback without profiles`() {
        assertEquals(TsResidualCallPolicy.STOP_PATH, TsOptions().unknownCallFallback)
        assertNull(TsOptions().enabledUnknownCallModelIds)

        assertFalse(reachesReturn("declaredMethodWithoutBodyContinues"))
        assertTrue(
            reachesReturn(
                "declaredMethodWithoutBodyContinues",
                tsOptions = TsOptions(unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
            )
        )
    }

    @Test
    fun `fresh symbolic return uses the source call result type`() {
        val dispatcher = RecordingResultSortDispatcher(
            TsModelUnknownCallDispatcher(
                models = noModels,
                fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
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
    fun `unknown call keeps typed data without eagerly resolving arguments`() {
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
    fun `unknown call preserves source and resolved values available at dispatch`() {
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
    fun `pointer call pairs its source with the resolved function pointer`() {
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
    fun `unknown call result type comes from the source overload`() {
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
        models: TsUnknownCallModelCatalog = noModels,
        className: String = "CallFallbackBaseline",
    ): Boolean = returnStatement(scene, methodName, className) in
        reachedStatements(methodName, scene, tsOptions, dispatcher, models, className)

    private fun reachedStatements(
        methodName: String,
        scene: EtsScene,
        tsOptions: TsOptions,
        dispatcher: TsUnknownCallDispatcher?,
        models: TsUnknownCallModelCatalog,
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
            unknownCallModels = models,
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

    private fun assertFreshResultPreservesAllFakeRepresentations(
        fallback: TsResidualCallPolicy,
        models: TsUnknownCallModelCatalog = noModels,
    ) {
        val method = method(fullScene, "freshUnknownCallResult")
        TsMachine(
            scene = fullScene,
            options = allStatesMachineOptions,
            tsOptions = TsOptions(unknownCallFallback = fallback),
            unknownCallModels = models,
        ).use { machine ->
            val state = machine.analyze(listOf(method)).single()
            val result = assertIs<TsMethodResult.Success>(state.methodResult).value
            val fakeValue = assertIs<UConcreteHeapRef>(result)
            val fakeType = with(state.ctx) {
                assertTrue(fakeValue.isFakeObject())
                fakeValue.getFakeType(state.memory)
            }
            val discriminators = mapOf(
                "boolean" to fakeType.boolTypeExpr,
                "number" to fakeType.fpTypeExpr,
                "reference" to fakeType.refTypeExpr,
            )

            discriminators.forEach { (kind, discriminator) ->
                val constraints = state.pathConstraints.clone()
                constraints += discriminator
                val solverResult = state.ctx.solver<EtsType>().check(constraints)

                assertIs<USatResult<*>>(solverResult, "Fresh fake result lost its $kind representation")
            }

            val exactlyOneType = fakeType.mkExactlyOneTypeConstraint(state.ctx)
            assertTrue(state.models.isNotEmpty())
            assertTrue(state.models.all { model -> model.eval(exactlyOneType).isTrue })
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

    private object ApplyingModel : TestModel(id = "applying-model", methodName = "external") {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
            val successor = TsUnknownCallModelSuccessor(
                guard = state.ctx.trueExpr,
                completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
            )

            return TsUnknownCallModelExecution(successors = listOf(successor))
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

    private object ExceptionalModel : TestModel(id = "exceptional-model", methodName = "fail") {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
            val successor = TsUnknownCallModelSuccessor(
                guard = state.ctx.trueExpr,
                completion = TsUnknownCallModelCompletion.Exceptional {
                    ctx.mkUndefinedValue() to EtsStringType
                },
            )

            return TsUnknownCallModelExecution(successors = listOf(successor))
        }
    }

    private object UnsupportedPartialModel : TestModel(id = "unsupported-partial-model", methodName = "value") {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
            val successor = TsUnknownCallModelSuccessor(
                guard = state.ctx.falseExpr,
                completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
            )

            return TsUnknownCallModelExecution(
                successors = listOf(successor),
                residualGuard = state.ctx.trueExpr,
            )
        }
    }

    private object StatefulAliasModel : TestModel(id = "stateful-alias-model", methodName = "identity") {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution {
            val argument = requireNotNull(call.arguments.single().resolved)
            val successor = TsUnknownCallModelSuccessor(
                guard = state.ctx.trueExpr,
                completion = TsUnknownCallModelCompletion.Normal { argument },
                applyStateChanges = { addedArtificialLocals += STATE_CHANGE_MARKER },
            )

            return TsUnknownCallModelExecution(successors = listOf(successor))
        }
    }

    private abstract class TestModel(
        override val id: String,
        methodName: String,
    ) : TsUnknownCallModel {
        override val target = TsUnknownCallTarget(methodName = methodName)
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

    private data class ObservationCase(
        val fallback: TsResidualCallPolicy,
        val models: TsUnknownCallModelCatalog,
        val outcome: TsUnknownCallOutcome,
        val decision: TsUnknownCallDecision,
        val finalStateCount: Int,
    )

    private data class ObservationFailureCase(
        val fallback: TsResidualCallPolicy,
        val models: TsUnknownCallModelCatalog,
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
        const val STATE_CHANGE_MARKER = "semantic-model-state-change"

        val noModels = TsUnknownCallModelCatalog(emptyList())

        fun catalog(vararg models: TsUnknownCallModel): TsUnknownCallModelCatalog =
            TsUnknownCallModelCatalog(models.toList())

        fun completeExecution(): TsUnknownCallModelExecution =
            TsUnknownCallModelExecution(
                successors = listOf(
                    TsUnknownCallModelSuccessor(
                        guard = mockk(),
                        completion = TsUnknownCallModelCompletion.Normal { mockk<UExpr<*>>() },
                    ),
                ),
            )

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
