package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArraySparseGrowthSourceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/ArraySparseGrowthSource.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    override val tsOptions = TsOptions(maxArraySize = 6)
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                limitations += event
            }
        }

        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions, observer = observer).use { machine ->
            val outcome = machine.analyzeWithMetadata(methods = listOf(method))

            assertTrue(outcome.unsupportedPaths.isEmpty(), outcome.toString())
            assertTrue(!outcome.engineFailed && !outcome.unsupportedCall, outcome.toString())
            assertTrue(limitations.isEmpty(), limitations.toString())
            outcome.states.map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    init {
        options = options.copy(stateCollectionStrategy = StateCollectionStrategy.ALL, stopOnCoverage = 0)
    }

    @Test
    fun `bounded indexed growth preserves gap reads kinds references and prior deletion`() {
        for (name in supportedMethods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = method(name),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `symbolic gap index preserves both supported values without stale slots`() {
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method("symbolicIndex"),
            { index, result -> index.number == 2.0 && result.number == 1.0 },
            { index, result -> index.number == 3.0 && result.number == 1.0 },
            invariants = arrayOf({ index, result ->
                if (index.number == 2.0 || index.number == 3.0) result.number == 1.0 else result.number == 0.0
            }),
        )
    }

    @Test
    fun `growth capacity remains a reported limitation`() {
        val reasons = mutableListOf<TsRuntimeFeatureLimitationReason>()
        val observer = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                reasons += event.reason
            }
        }

        TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer).use { machine ->
            val result = machine.analyzeWithMetadata(methods = listOf(method("beyondCapacity")))

            assertTrue(result.states.isEmpty())
            assertEquals(listOf(TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY), reasons)
        }
    }

    @Test
    fun `dense only operations stay explicitly outside the sparse model`() {
        for (name in listOf("denseOnlyConsumer", "denseProofAfterSparseGrowth")) {
            TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
                val result = machine.analyzeWithMetadata(methods = listOf(method(name)))

                assertTrue(result.states.isEmpty(), "$name: $result")
                assertTrue(result.unsupportedCall || result.unsupportedPaths.isNotEmpty(), "$name: $result")
            }
        }
    }

    @Test
    fun `generated source witnesses agree with native TypeScript`() {
        val script = buildString {
            appendLine(source.readText())
            for (name in supportedMethods) {
                appendLine("if (ArraySparseGrowthSource.$name() !== 1) throw Error('$name');")
            }
            appendLine(
                "if (ArraySparseGrowthSource.denseProofAfterSparseGrowth() !== 'c,,') throw Error('dense proof');",
            )
            val tests = runner(method("symbolicIndex"), options)
            assertTrue(tests.isNotEmpty())
            for (test in tests) {
                val index = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                appendLine("if (ArraySparseGrowthSource.symbolicIndex($index) !== $expected) throw Error('$index');")
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "array-sparse-growth",
            timeoutMessage = "Sparse indexed growth replay timed out",
        )
    }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "ArraySparseGrowthSource")

    private val supportedMethods = listOf(
        "numbers",
        "booleans",
        "references",
        "widenedAlias",
        "unresolvedValues",
        "shrinkThenRegrow",
        "movesAndPops",
        "fillGaps",
    )
}
