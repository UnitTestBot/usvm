package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UConcreteHeapRef
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.getResolvedValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PromiseSettlementArityTest : TsMethodTestRunner() {
    override val scene = loadScene("/models/PromiseSettlementArity.ts")
    override val tsOptions = TsOptions(eraseTypeAssertions = true)

    // Entry-point async returns are Promises. Inspect their fulfilled payload, the value observed by await,
    // rather than asking the ordinary object witness resolver to reconstruct a native Promise instance.
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyze(listOf(method)).map { state ->
                val completion = assertIs<TsMethodResult.Success>(state.methodResult)
                val promise = assertIs<UConcreteHeapRef>(completion.value)
                assertEquals(PromiseState.FULFILLED, state.promiseState[promise])

                val resultState = state.clone()
                val payload = resultState.getResolvedValue(promise, resultState.ctx.addressSort)
                resultState.methodResult = TsMethodResult.Success.MockedCall(payload, method.signature)
                TsTestResolver().resolve(method, resultState)
            }
        }
    }

    @TempDir
    lateinit var directory: Path

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `resolveMissing follows ordinary Promise semantics`() {
        assertOne(methodName = "resolveMissing")
    }

    @Test
    fun `rejectMissing follows ordinary Promise semantics`() {
        assertOne(methodName = "rejectMissing")
    }

    @Test
    fun `resolveExtra follows ordinary Promise semantics`() {
        assertOne(methodName = "resolveExtra")
    }

    @Test
    fun `rejectExtra follows ordinary Promise semantics`() {
        assertOne(methodName = "rejectExtra")
    }

    @Test
    fun `callbackResolveMissing follows ordinary Promise semantics`() {
        assertOne(methodName = "callbackResolveMissing")
    }

    @Test
    fun `callbackRejectMissing follows ordinary Promise semantics`() {
        assertOne(methodName = "callbackRejectMissing")
    }

    @Test
    fun `firstSettlementWins follows ordinary Promise semantics`() {
        assertOne(methodName = "firstSettlementWins")
    }

    @Test
    fun `callbackExtra follows ordinary Promise semantics`() {
        assertOne(methodName = "callbackExtra")
    }

    @Test
    fun `native Promise confirms missing extra and repeated settlements`() {
        val source = getResourcePath("/models/PromiseSettlementArity.ts").readText()
        val methods = listOf(
            "resolveMissing",
            "rejectMissing",
            "resolveExtra",
            "rejectExtra",
            "callbackResolveMissing",
            "callbackRejectMissing",
            "firstSettlementWins",
            "callbackExtra",
        )
        val calls = methods.joinToString(separator = "\n") { name ->
            "if (await PromiseSettlementArity.$name() !== 1) throw Error('$name');"
        }

        val invoke = "\n(async () => { $calls })().catch(error => { console.error(error); process.exitCode = 1; });"

        assertNodeReplay(
            source = source + invoke,
            directory = directory,
            name = "promise-settlement-arity",
            timeoutMessage = "Native Promise settlement regression timed out",
        )
    }

    private fun assertOne(methodName: String) {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = methodName, className = "PromiseSettlementArity"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }
}
