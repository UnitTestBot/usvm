package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.isTrue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsMachineAnalysisResult
import org.usvm.machine.TsMethodCall
import org.usvm.machine.TsOptions
import org.usvm.machine.call.intrinsic.TsModelAssume
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.solver.USatResult
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsModelAssumeTest {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = getResourcePath("/models/SourceAssumeCalls.ts")
    private val scene = EtsScene(listOf(loadEtsFileAutoConvert(sourcePath, provider = EtsIrProvider.TS_FRONTEND)))
    private val artifact = loadEtsIrUnknownCallModelArtifact(
        sourcePath = getResourcePath("/models/SourceAssumeModels.ts"),
        entryPointClassName = "SourceAssumeModels",
        entryPointMethodName = "always",
    )
    private val models = TsUnknownCallModelCatalog(
        listOf("always", "never", "gated", "positive").map { name ->
            val entryPoint = artifact.entryPoint.enclosingClass!!.methods.single { it.name == name }
            TsEtsIrUnknownCallModel(
                id = "test.assume.$name",
                target = TsUnknownCallTarget(methodName = name, enclosingClassName = "ExternalAssume"),
                artifact = artifact.copy(entryPoint = entryPoint),
                requiredModelIds = setOf(TsModelAssume.id),
            )
        } + TsModelAssume,
        selection = TsUnknownCallModelSelection.Only(
            setOf("test.assume.always", "test.assume.never", "test.assume.gated", "test.assume.positive")
        ),
    )

    @Test
    fun `true continues and false prunes without fallback or unsupported outcome`() {
        val accepted = analyze("always")
        val rejected = analyze("never")

        assertEquals(expected = 1, actual = accepted.states.size)
        assertTrue(rejected.states.isEmpty())
        for (result in listOf(accepted, rejected)) {
            assertFalse(result.analysis.engineFailed)
            assertFalse(result.analysis.unsupportedCall)
            assertTrue(result.analysis.unsupportedPaths.isEmpty())
            assertEquals(
                listOf(TsUnknownCallOutcome.MODEL_APPLIED, TsUnknownCallOutcome.MODEL_APPLIED),
                result.events.map { it.outcome },
            )
        }
    }

    @Test
    fun `symbolic assumption keeps only its positive domain and refreshes witness models`() {
        var cachedOrder = emptyList<Boolean>()
        val result = analyze("gated") { state ->
            val argument = state.ctx.mkRegisterReading(idx = 1, sort = state.ctx.boolSort)
            val cachedModels = listOf(state.ctx.mkNot(argument), argument).map { guard ->
                val constraints = state.pathConstraints.clone()
                constraints += guard
                when (val result = state.ctx.solver<EtsType>().check(constraints)) {
                    is USatResult -> result.model
                    else -> error("Both boolean cache domains must be feasible")
                }
            }
            state.models = cachedModels
            cachedOrder = cachedModels.map { it.eval(argument).isTrue }
        }
        val method = method("gated")

        assertEquals(listOf(false, true), cachedOrder)
        assertEquals(expected = 1, actual = result.states.size)
        val state = result.states.single()
        val test = TsTestResolver().resolve(method, state)
        assertTrue(assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value)
        assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        assertTrue(state.models.isNotEmpty())
        for (model in state.models) {
            val argument = state.ctx.mkRegisterReading(idx = 1, sort = state.ctx.boolSort)
            assertTrue(model.eval(argument).isTrue)
        }
    }

    @Test
    fun `application declarations cannot acquire assume semantics`() {
        val result = analyze("foreign")
        val test = TsTestResolver().resolve(method("foreign"), result.states.single())

        assertEquals(9.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        assertEquals(listOf(TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN), result.events.map { it.outcome })
    }

    @Test
    fun `namespaced helper cannot impersonate the private helper inside an active artifact`() {
        var checked = false
        val observer = object : TsInterpreterObserver {
            override fun onStatementEntered(stmt: EtsStmt, scope: TsStepScope) {
                if (stmt is TsMethodCall) return
                val expression = stmt.callExpr ?: return
                if (expression.callee.name != "assume") return

                val owner = expression.callee.enclosingClass
                val forgedOwner = owner.copy(namespace = EtsNamespaceSignature(name = "Forged", file = owner.file))
                scope.calcOnState {
                    val call = TsUnknownCall(
                        callee = expression.callee.copy(enclosingClass = forgedOwner),
                        receiver = null,
                        arguments = listOf(TsUnknownCallValue(expression.args.single(), ctx.trueExpr)),
                        resultType = expression.type,
                        callSite = stmt,
                        failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
                    )
                    assertTrue(isUnknownCallModelFileActive(owner))
                    assertNull(TsModelAssume.apply(this, call))
                    checked = true
                }
            }
        }

        TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            unknownCallModels = models,
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method("always"))) }

        assertTrue(checked)
    }

    @Test
    fun `private artifact helper requires its active dispatcher frame`() {
        val callSite = artifact.entryPoint.cfg.stmts.single { it.callExpr?.callee?.name == "assume" }
        val expression = requireNotNull(callSite.callExpr)
        var checked = false

        analyze("always") { state ->
            val call = TsUnknownCall(
                callee = expression.callee,
                receiver = null,
                arguments = listOf(TsUnknownCallValue(expression.args.single(), state.ctx.trueExpr)),
                resultType = expression.type,
                callSite = callSite,
                failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
            )
            assertNull(TsModelAssume.apply(state, call))
            checked = true
        }

        assertTrue(checked)
    }

    @Test
    fun `positive numeric witnesses replay in the original source contract`() {
        val method = method("positive")
        val result = analyze("positive")
        val checks = result.states.map { state ->
            val test = TsTestResolver().resolve(method, state)
            val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
            val output = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            assertTrue(input > 0)
            assertEquals(input, output)
            "if (!Object.is(new SourceAssumeCalls().positive($input), $output)) throw Error('positive');"
        }
        assertTrue(checks.isNotEmpty())

        assertNodeReplay(
            source = sourcePath.readText() + "\nconst ExternalAssume = { positive: value => value };\n" +
                checks.joinToString(separator = "\n"),
            directory = directory,
            name = "assume-positive",
            timeoutMessage = "Assume witness replay timed out",
        )
    }

    private fun analyze(name: String, configureInitialState: (TsState) -> Unit = {}): Result {
        val events = mutableListOf<TsUnknownCallEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) {
                events += event
            }
        }
        val analysis = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
            unknownCallModels = models,
            observer = observer,
        ).use { machine ->
            machine.analyzeWithMetadata(listOf(method(name))) { _, state -> configureInitialState(state) }
        }
        return Result(analysis = analysis, events = events)
    }

    private fun method(name: String): EtsMethod = scene.projectClasses.single { it.name == "SourceAssumeCalls" }
        .methods
        .single { it.name == name }

    private data class Result(
        val analysis: TsMachineAnalysisResult,
        val events: List<TsUnknownCallEvent>,
    ) {
        val states: List<TsState> get() = analysis.states
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
        )
    }
}
