package org.usvm.machine.call

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
import org.usvm.UConcreteHeapRef
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
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
    fun `inventoried unknown calls use normalized compatibility dispatch`() {
        val cases = listOf(
            Case("declaredMethodWithoutBodyContinues", TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE, true),
            Case(
                "allocatedReceiverWithoutMethodContinues",
                TsUnknownCallFailureReason.NO_SUITABLE_VIRTUAL_TARGET,
                true,
            ),
            Case(
                "unresolvedStaticCallPrunes",
                TsUnknownCallFailureReason.STATIC_METHOD_NOT_FOUND,
                false,
                sceneWithout = "ExternalStatic",
            ),
            Case(
                "unresolvedVirtualCallPrunes",
                TsUnknownCallFailureReason.VIRTUAL_METHOD_NOT_FOUND,
                false,
                sceneWithout = "ExternalReceiver",
            ),
            Case(
                "unresolvedAllocatedReceiverCallPrunes",
                listOf(
                    TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
                    TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
                ),
                false,
                sceneWithout = "ExternalReceiver",
            ),
            Case("nonReferenceInstanceCallPrunes", TsUnknownCallFailureReason.NON_REFERENCE_RECEIVER, false),
            Case(
                "unresolvedConstructorContinues",
                TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
                true,
                sceneWithout = "ExternalReceiver",
            ),
            Case("unresolvedAnyPointerCallPrunes", TsUnknownCallFailureReason.POINTER_TARGET_NOT_FOUND, false),
            Case("nonReferencePointerCallContinues", TsUnknownCallFailureReason.NON_REFERENCE_POINTER, true),
            Case(
                "intraproceduralAssignmentCallContinues",
                TsUnknownCallFailureReason.INTERPROCEDURAL_ANALYSIS_DISABLED,
                true,
                tsOptions = TsOptions(interproceduralAnalysis = false),
            ),
            Case(
                "intraproceduralCallStatementContinues",
                TsUnknownCallFailureReason.INTERPROCEDURAL_ANALYSIS_DISABLED,
                true,
                tsOptions = TsOptions(interproceduralAnalysis = false),
            ),
            Case("logCallSkipsBody", TsUnknownCallFailureReason.LOGGING_CALL, true),
            Case("booleanConverterPrunes", TsUnknownCallFailureReason.POINTER_TARGET_NOT_FOUND, false),
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
        dispatcher: TsUnknownCallDispatcher,
        className: String = "CallFallbackBaseline",
    ): Boolean = returnStatement(scene, methodName, className) in
        reachedStatements(methodName, scene, tsOptions, dispatcher, className)

    private fun reachedStatements(
        methodName: String,
        scene: EtsScene,
        tsOptions: TsOptions,
        dispatcher: TsUnknownCallDispatcher,
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

    private class RecordingUnknownCallDispatcher : TsUnknownCallDispatcher {
        val calls = mutableListOf<TsUnknownCall>()
        val receiverIsAssociatedFunction = mutableListOf<Boolean?>()

        override fun dispatch(scope: TsStepScope, call: TsUnknownCall) {
            calls += call
            val receiver = call.receiver?.resolved as? UConcreteHeapRef
            receiverIsAssociatedFunction += receiver?.let { resolved ->
                scope.calcOnState { associatedFunction[resolved] != null }
            }
            TsCompatibilityUnknownCallDispatcher.dispatch(scope, call)
        }
    }

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
    }
}
