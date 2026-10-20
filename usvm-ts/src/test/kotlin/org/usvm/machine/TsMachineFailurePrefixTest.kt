package org.usvm.machine

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Source-level boundary handling plus deliberately injected low-level interpreter failures. */
class TsMachineFailurePrefixTest {
    private val scene = loadScene(resource = "/models/SharedAnalysis.ts")
    private val method = scene.projectClasses.single { it.name == "SharedAnalysis" }
        .methods
        .single { it.name == "success" }
    private val options = UMachineOptions(
        solverType = SolverType.YICES,
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = 5.seconds,
    )

    @Test
    fun `unmodeled global retains a limitation prefix instead of claiming UNSAT`() {
        val globalScene = loadScene(resource = "/models/UnmodeledGlobalBinding.ts")
        val globalMethod = globalScene.projectClasses.single { it.name == "UnmodeledGlobalBinding" }
            .methods
            .single { it.name == "read" }
        val prefixes = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { prefixes += it }

        val result = TsMachine(
            scene = globalScene,
            options = options,
            tsOptions = TsOptions(),
            observer = stoppedObserver,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(globalMethod)) }

        assertFalse(result.engineFailed)
        assertTrue(result.runtimeLimited)
        assertTrue(result.states.isEmpty())
        assertTrue(result.unsupportedPaths.any { "GLOBAL_BINDING_NOT_MODELED" in it && "Reflect" in it })
        assertTrue(prefixes.isNotEmpty())
        assertTrue(prefixes.all { !it.pathConstraints.isFalse && it.models.isNotEmpty() })
        assertTrue(prefixes.all { it.unsupportedReason != null && it.engineFailureReason == null })
    }

    @Test
    fun `failed interpreter step retains its diagnostic prefix without a successful result`() {
        val prefixes = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { prefixes += it }
        val observer = failingReturnObserver(stoppedObserver = stoppedObserver)

        val result = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            observer = observer,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertTrue(result.engineFailed)
        assertFalse(result.runtimeLimited)
        assertFalse(result.unsupportedCall)
        assertTrue(result.states.isEmpty())
        val prefix = prefixes.single()
        assertTrue(prefix.callStack.isNotEmpty())
        assertTrue(prefix.models.isNotEmpty())
        assertFalse(prefix.pathConstraints.isFalse)
        assertNull(prefix.unsupportedReason)
        assertTrue(prefix.engineFailureReason?.contains("Injected step failure") == true)
        assertEquals(prefix.engineFailureReason, prefix.clone().engineFailureReason)
    }

    @Test
    fun `completed fork survives a failure in the original branch of the same step`() {
        val forkMethod = scene.projectClasses.single { it.name == "SharedAnalysis" }
            .methods
            .single { it.name == "forkable" }
        val prefixes = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { prefixes += it }
        val observer = failingReturnObserver(
            stoppedObserver = stoppedObserver,
            target = forkMethod,
            forkBeforeFailure = true,
        )

        val result = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            observer = observer,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(forkMethod)) }

        assertTrue(result.engineFailed)
        assertFalse(result.runtimeLimited)
        val returned = result.states.single()
        val completion = returned.methodResult as TsMethodResult.Success
        assertEquals(returned.ctx.falseExpr, returned.models.first().eval(completion.value))
        assertEquals(expected = 1, actual = prefixes.size)
        assertNull(returned.engineFailureReason)
        assertNull(returned.unsupportedReason)
    }

    @Test
    fun `fail fast option still throws the internal exception`() {
        val stoppedObserver = TsStoppedStatesObserver { error("No prefix emission is expected before fail fast") }
        val observer = failingReturnObserver(stoppedObserver = stoppedObserver)

        val failure = assertFailsWith<IllegalStateException> {
            TsMachine(
                scene = scene,
                options = options.copy(throwExceptionOnStepFailure = true),
                tsOptions = TsOptions(),
                observer = observer,
            ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }
        }

        assertEquals(expected = "Injected step failure", actual = failure.message)
    }

    private fun failingReturnObserver(
        stoppedObserver: TsStoppedStatesObserver,
        target: EtsMethod = method,
        forkBeforeFailure: Boolean = false,
    ): TsInterpreterObserver = object : TsInterpreterObserver by stoppedObserver {
        private var failureInjected = false

        override fun onReturnStatement(
            simpleValueResolver: TsSimpleValueResolver,
            stmt: EtsReturnStmt,
            scope: TsStepScope,
        ) {
            if (stmt.location.method != target || failureInjected) return

            failureInjected = true
            if (forkBeforeFailure) {
                val input = checkNotNull(stmt.returnValue?.accept(simpleValueResolver))
                val condition = input.asExpr(scope.calcOnState { ctx.boolSort })
                scope.fork(condition = condition)
            }

            error("Injected step failure")
        }
    }

    private fun loadScene(resource: String) = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(path = getResourcePath(resource), provider = EtsIrProvider.TS_FRONTEND),
        ),
    )
}
