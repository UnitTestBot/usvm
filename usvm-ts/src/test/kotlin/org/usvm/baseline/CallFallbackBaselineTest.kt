package org.usvm.baseline

import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsCompatibilityUnknownCallDispatcher
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallDispatcher
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class CallFallbackBaselineTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/baseline/CallFallbackBaseline.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val fullScene = EtsScene(listOf(sourceFile))

    @Test
    fun `a declared method without a body is mocked and execution continues`() {
        assertTrue(reachesReturn("declaredMethodWithoutBodyContinues"))
    }

    @Test
    fun `an any receiver with a known method name is mocked and execution continues`() {
        assertTrue(reachesReturn("anyReceiverWithKnownMethodContinues"))
    }

    @Test
    fun `an allocated receiver without a suitable method is mocked and execution continues`() {
        assertTrue(reachesReturn("allocatedReceiverWithoutMethodContinues"))
    }

    @Test
    fun `an unresolved static call prunes the state`() {
        val scene = sceneWithout("ExternalStatic")
        assertFalse(reachesReturn("unresolvedStaticCallPrunes", scene))
    }

    @Test
    fun `an unresolved bare call does not execute a same named class static`() {
        val call = callExpression("unresolvedBareCallDoesNotUseClassStatic")
        val classReturn = returnStatement(fullScene, "missingBareCall", className = "UnrelatedStatic")
        val reached = assertUnknownStaticDispatch("unresolvedBareCallDoesNotUseClassStatic")

        assertIs<EtsStaticCallExpr>(call)
        assertFalse(classReturn in reached)
        assertFalse(returnStatement(fullScene, "unresolvedBareCallDoesNotUseClassStatic") in reached)
    }

    @Test
    fun `an unresolved bare call does not execute a same named SDK namespace function`() {
        val sdkFile = loadEtsFileAutoConvert(
            getResourcePath("/samples/lang/StaticOverloads.ts"),
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val scene = EtsScene(projectFiles = listOf(sourceFile), sdkFiles = listOf(sdkFile))
        val call = callExpression("unresolvedBareCallDoesNotUseSdkNamespace")
        val namespaceMethods = sdkFile.namespaces.flatMap { namespace ->
            namespace.allClasses.flatMap { clazz -> clazz.methods }
        }

        val reached = assertUnknownStaticDispatch("unresolvedBareCallDoesNotUseSdkNamespace", scene)

        assertTrue(namespaceMethods.any { it.name == "missingNamespaceCall" })
        assertIs<EtsStaticCallExpr>(call)
        assertFalse(returnStatement(scene, "unresolvedBareCallDoesNotUseSdkNamespace") in reached)
    }

    @Test
    fun `an unresolved bare call does not execute an SDK module export`() {
        val sdkFile = loadEtsFileAutoConvert(
            getResourcePath("/samples/lang/StaticOverloads.ts"),
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val scene = EtsScene(projectFiles = listOf(sourceFile), sdkFiles = listOf(sdkFile))
        val call = callExpression("unresolvedBareCallDoesNotUseSdkModule")
        val rootMethods = sdkFile.classes
            .single { it.name == DEFAULT_ARK_CLASS_NAME }
            .methods

        val reached = assertUnknownStaticDispatch("unresolvedBareCallDoesNotUseSdkModule", scene)

        assertTrue(sdkFile.exportInfos.any { it.name == "missingModuleCall" })
        assertTrue(rootMethods.any { it.name == "missingModuleCall" })
        assertIs<EtsStaticCallExpr>(call)
        assertFalse(returnStatement(scene, "unresolvedBareCallDoesNotUseSdkModule") in reached)
    }

    @Test
    fun `an unresolved virtual call prunes the state`() {
        val scene = sceneWithout("ExternalReceiver")
        assertFalse(reachesReturn("unresolvedVirtualCallPrunes", scene))
    }

    @Test
    fun `an unresolved call on an allocated receiver prunes the state`() {
        val scene = sceneWithout("ExternalReceiver")
        assertFalse(reachesReturn("unresolvedAllocatedReceiverCallPrunes", scene))
    }

    @Test
    fun `an instance call on a non-reference value prunes the state`() {
        val call = assertIs<EtsPtrCallExpr>(callExpression("nonReferenceInstanceCallPrunes"))
        assertNotNull(call.receiver)

        assertFalse(reachesReturn("nonReferenceInstanceCallPrunes"))
    }

    @Test
    fun `an unresolved constructor is mocked and execution continues`() {
        val scene = sceneWithout("ExternalReceiver")
        assertTrue(reachesReturn("unresolvedConstructorContinues", scene))
    }

    @Test
    fun `an unresolved any pointer without an associated function prunes the state`() {
        assertPointerCall("unresolvedAnyPointerCallPrunes")
        assertFalse(reachesReturn("unresolvedAnyPointerCallPrunes"))
    }

    @Test
    fun `a non-reference pointer call is mocked and execution continues`() {
        assertPointerCall("nonReferencePointerCallContinues")
        assertTrue(reachesReturn("nonReferencePointerCallContinues"))
    }

    @Test
    fun `an intraprocedural assignment call is mocked and execution continues`() {
        assertTrue(
            reachesReturn(
                "intraproceduralAssignmentCallContinues",
                tsOptions = TsOptions(interproceduralAnalysis = false),
            )
        )
    }

    @Test
    fun `an intraprocedural call statement is mocked and execution continues`() {
        assertTrue(
            reachesReturn(
                "intraproceduralCallStatementContinues",
                tsOptions = TsOptions(interproceduralAnalysis = false),
            )
        )
    }

    @Test
    fun `a call to Log is mocked without entering its body`() {
        val statements = reachedStatements("logCallSkipsBody")
        val logReturn = method(fullScene, "record", className = "Log")
            .cfg.stmts.filterIsInstance<EtsReturnStmt>().single()

        assertTrue(returnStatement(fullScene, "logCallSkipsBody") in statements)
        assertFalse(logReturn in statements)
    }

    @Test
    fun `a call through a receiver named Logger skips its body`() {
        assertTrue(reachesReturn("loggerCallSkipsBody"))
    }

    @Test
    fun `toString returns the current placeholder`() {
        assertTrue(reachesReturn("toStringUsesPlaceholder"))
    }

    @Test
    fun `valueOf returns its receiver`() {
        assertTrue(reachesReturn("valueOfReturnsReceiver"))
    }

    @Test
    fun `native Boolean conversion is an unresolved pointer call that prunes the state`() {
        assertPointerCall("booleanConverterPrunes")
        assertFalse(reachesReturn("booleanConverterPrunes"))
    }

    @Test
    fun `Boolean construction without an SDK class prunes before its approximation`() {
        assertFalse(reachesReturn("booleanConstructorUsesTruthiness"))
    }

    @Test
    fun `Math floor rounds toward negative infinity`() {
        assertTrue(reachesReturn("mathFloorRoundsTowardNegativeInfinity"))
    }

    @Test
    fun `resource lookup is approximated before entering its body`() {
        assertPointerCall("resourceLookupSkipsBody")
        val statements = reachedStatements("resourceLookupSkipsBody")

        assertTrue(returnStatement(fullScene, "resourceLookupSkipsBody") in statements)
        assertFalse(method(fullScene, "\$r", className = "%dflt").cfg.stmts.first() in statements)
    }

    @Test
    fun `retained resource lookup aliases are approximated after binding replacement`() {
        assertTrue(reachesReturn("resourceAliasSkipsBody"))
    }

    @Test
    fun `resource binding replacement executes the selected replacement body`() {
        val statements = reachedStatements("resourceBindingReplacementRunsBody")

        assertTrue(returnStatement(fullScene, "resourceBindingReplacementRunsBody") in statements)
        assertTrue(returnStatement(fullScene, "resourceReplacement", className = "%dflt") in statements)
    }

    @Test
    fun `resource lookup evaluates its argument before approximation`() {
        assertTrue(reachesReturn("resourceLookupEvaluatesArgument"))
    }

    private fun reachesReturn(
        methodName: String,
        scene: EtsScene = fullScene,
        tsOptions: TsOptions = TsOptions(),
    ): Boolean = returnStatement(scene, methodName) in reachedStatements(methodName, scene, tsOptions)

    private fun reachedStatements(
        methodName: String,
        scene: EtsScene = fullScene,
        tsOptions: TsOptions = TsOptions(),
        unknownCallDispatcher: TsUnknownCallDispatcher = TsCompatibilityUnknownCallDispatcher,
    ): Set<EtsStmt> {
        val method = method(scene, methodName)
        val returnStatement = returnStatement(scene, methodName)
        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        initialTarget.addChild(TsReachabilityTarget.FinalPoint(returnStatement))

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            machineObserver = ReachabilityObserver(),
            unknownCallDispatcher = unknownCallDispatcher,
        ).use { machine ->
            machine.analyze(listOf(method), listOf(initialTarget))
                .flatMapTo(mutableSetOf()) { state -> state.pathNode.allStatements }
        }
    }

    private fun assertUnknownStaticDispatch(
        methodName: String,
        scene: EtsScene = fullScene,
    ): Set<EtsStmt> {
        val calls = mutableListOf<TsUnknownCall>()
        val dispatcher = TsUnknownCallDispatcher { scope, call ->
            calls += call
            TsCompatibilityUnknownCallDispatcher.dispatch(scope, call)
        }

        val reached = reachedStatements(methodName, scene, unknownCallDispatcher = dispatcher)

        val dispatched = calls.single()
        assertEquals(TsUnknownCallFailureReason.STATIC_METHOD_NOT_FOUND, dispatched.failureReason)
        assertEquals(callExpression(methodName), dispatched.callSite.callExpr)
        return reached
    }

    private fun returnStatement(
        scene: EtsScene,
        methodName: String,
        className: String = "CallFallbackBaseline",
    ): EtsReturnStmt = method(
        scene = scene,
        methodName = methodName,
        className = className,
    ).cfg.stmts.filterIsInstance<EtsReturnStmt>().single()

    private fun assertPointerCall(methodName: String) {
        assertIs<EtsPtrCallExpr>(callExpression(methodName))
    }

    private fun callExpression(methodName: String): EtsCallExpr = method(fullScene, methodName)
        .cfg.stmts.mapNotNull { it.callExpr }.single()

    private fun method(
        scene: EtsScene,
        methodName: String,
        className: String = "CallFallbackBaseline",
    ): EtsMethod = scene.projectClasses
        .single { it.name == className }
        .methods.single { it.name == methodName }

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
