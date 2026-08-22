package org.usvm.baseline

import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.EtsIrProvider
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
import org.usvm.util.getResourcePath
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
        assertIs<EtsInstanceCallExpr>(callExpression("nonReferenceInstanceCallPrunes"))
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
        assertIs<EtsStaticCallExpr>(callExpression("resourceLookupSkipsBody"))
        assertTrue(reachesReturn("resourceLookupSkipsBody"))
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
            unknownCallDispatcher = TsCompatibilityUnknownCallDispatcher,
        ).use { machine ->
            machine.analyze(listOf(method), listOf(initialTarget))
                .flatMapTo(mutableSetOf()) { state -> state.pathNode.allStatements }
        }
    }

    private fun returnStatement(scene: EtsScene, methodName: String): EtsReturnStmt =
        method(scene, methodName).cfg.stmts.filterIsInstance<EtsReturnStmt>().single()

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
