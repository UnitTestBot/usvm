package org.usvm.machine.interpreter

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.StepScope
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TsModuleInitializationTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                path = getResourcePath("/models/ModuleInitializationLifecycle.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    @Test
    fun `module object aliases retain mutations after initialization`() {
        val method = getMethod(methodName = "readTwice", className = "ModuleInitializationLifecycle")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { value -> value eq 1011 },
            invariants = arrayOf({ value -> value eq 1011 }),
        )
    }

    @Test
    fun `module completion flag becomes true only after its real initializer returns`() {
        val method = getMethod(methodName = "readTwice", className = "ModuleInitializationLifecycle")
        val completionFlags = mutableListOf<Boolean>()
        val observer = object : TsInterpreterObserver {
            override fun onAssignmentCompleted(
                simpleValueResolver: TsSimpleValueResolver,
                stmt: EtsAssignStmt,
                scope: TsStepScope,
            ) {
                val owner = stmt.location.method.enclosingClass ?: return
                val inModuleInitializer = owner.name == DEFAULT_ARK_CLASS_NAME &&
                    stmt.location.method.name == DEFAULT_ARK_METHOD_NAME
                if (!inModuleInitializer) return

                scope.calcOnState { completionFlags += isInitialized(owner) }
            }
        }
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            timeout = 5.seconds,
            solverType = SolverType.YICES,
            throwExceptionOnStepFailure = true,
        )

        val result = TsMachine(
            scene = scene,
            options = options,
            tsOptions = tsOptions,
            observer = observer,
        ).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertFalse(result.engineFailed)
        assertTrue(completionFlags.isNotEmpty())
        assertTrue(completionFlags.none { it })
        val owner = scene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
        assertTrue(result.states.isNotEmpty())
        assertTrue(result.states.all { it.isInitialized(owner) })
    }

    @Test
    fun `a failed initializer stays incomplete and its cached exception survives state cloning`() {
        val failedScene = EtsScene(
            projectFiles = listOf(
                loadEtsFileAutoConvert(
                    path = getResourcePath("/models/FailedModuleInitialization.ts"),
                    provider = EtsIrProvider.TS_FRONTEND,
                ),
            ),
        )
        val target = failedScene.projectClasses.single { it.name == "FailedModuleInitialization" }
        val method = target.methods.single { it.name == "read" }
        val owner = failedScene.projectClasses.single { it.name == DEFAULT_ARK_CLASS_NAME }
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            timeout = 5.seconds,
            solverType = SolverType.YICES,
            throwExceptionOnStepFailure = true,
        )

        TsMachine(scene = failedScene, options = options, tsOptions = tsOptions).use { machine ->
            val result = machine.analyzeWithMetadata(methods = listOf(method))

            assertFalse(result.engineFailed)
            val state = result.states.single()
            assertFalse(state.isInitialized(owner))
            val failure = state.staticInitializationFailures.getValue(owner)
            assertSame(failure, state.methodResult)

            // This deliberately checks a low-level retry contract after real source initialization failed.
            val retry = state.clone()
            retry.methodResult = TsMethodResult.NoCall
            val scope = StepScope(retry, UForkBlackList.createDefault<TsState, EtsStmt>())
            val initialized = retry.ctx.ensureStaticsInitialized(scope = scope, clazz = owner)

            assertNull(initialized)
            assertSame(failure, retry.methodResult)
            assertFalse(retry.isInitialized(owner))
            assertTrue(retry.callStack.isEmpty())
        }
    }
}
