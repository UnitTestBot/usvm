package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class MapSetModelsTest : TsMethodTestRunner() {
    override val scene = loadScene("/models/MapSetModels.ts")
    override val tsOptions = TsOptions(eraseTypeAssertions = true)

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
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
    fun `mapOverwrite agrees with native collection semantics`() {
        assertOne(methodName = "mapOverwrite")
    }

    @Test
    fun `mapMissingAndUndefined agrees with native collection semantics`() {
        assertOne(methodName = "mapMissingAndUndefined")
    }

    @Test
    fun `mapSameValueZero agrees with native collection semantics`() {
        assertOne(methodName = "mapSameValueZero")
    }

    @Test
    fun `mapObjectIdentity agrees with native collection semantics`() {
        assertOne(methodName = "mapObjectIdentity")
    }

    @Test
    fun `mapDeleteAndReinsert agrees with native collection semantics`() {
        assertOne(methodName = "mapDeleteAndReinsert")
    }

    @Test
    fun `mapClear agrees with native collection semantics`() {
        assertOne(methodName = "mapClear")
    }

    @Test
    fun `mapKindsRemainDistinct agrees with native collection semantics`() {
        assertOne(methodName = "mapKindsRemainDistinct")
    }

    @Test
    fun `setSameValueZero agrees with native collection semantics`() {
        assertOne(methodName = "setSameValueZero")
    }

    @Test
    fun `setMissing agrees with native collection semantics`() {
        assertOne(methodName = "setMissing")
    }

    @Test
    fun `setDeleteAndClear agrees with native collection semantics`() {
        assertOne(methodName = "setDeleteAndClear")
    }

    @Test
    fun `nullishConstructors agrees with native collection semantics`() {
        assertOne(methodName = "nullishConstructors")
    }

    @Test
    fun `symbolic number keys preserve conditional equality and state forks`() {
        for (name in listOf("mapSymbolicKeys", "setSymbolicKeys")) {
            discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
                method = getMethod(methodName = name, className = "MapSetModels"),
                { left, right, result -> left.number == right.number && result.number == 1.0 },
                { left, right, result -> left.number != right.number && result.number == 1.0 },
                invariants = arrayOf({ _, _, result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `symbolic string keys compare contents instead of heap identity`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsNumber>(
            method = getMethod(methodName = "mapStringContents", className = "MapSetModels"),
            { left, right, result -> left.value == right.value && result.number == 1.0 },
            { left, right, result -> left.value != right.value && result.number == 1.0 },
            invariants = arrayOf({ _, _, result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `conditional deletion does not leak between state forks`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "mapConditional", className = "MapSetModels"),
            { branch, result -> branch.value && result.number == 1.0 },
            { branch, result -> !branch.value && result.number == 1.0 },
            invariants = arrayOf({ _, result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `native collections confirm concrete boundary and symbolic representatives`() {
        val source = getResourcePath("/models/MapSetModels.ts").readText()
        val methods = listOf(
            "mapOverwrite",
            "mapMissingAndUndefined",
            "mapSameValueZero",
            "mapObjectIdentity",
            "mapDeleteAndReinsert",
            "mapClear",
            "mapKindsRemainDistinct",
            "setSameValueZero",
            "setMissing",
            "setDeleteAndClear",
            "nullishConstructors",
        )
        val concrete = methods.joinToString(separator = "\n") { name ->
            "if (MapSetModels.$name() !== 1) throw Error('$name');"
        }
        val representatives = """
            for (const left of [0, -0, NaN, 7, 19]) {
                for (const right of [0, -0, NaN, 7, 19]) {
                    if (MapSetModels.mapSymbolicKeys(left, right) !== 1) throw Error('map keys');
                    if (MapSetModels.setSymbolicKeys(left, right) !== 1) throw Error('set keys');
                }
            }
            for (const left of ['', 'a', 'ab']) for (const right of ['', 'a', 'ab']) {
                if (MapSetModels.mapStringContents(left, right) !== 1) throw Error('string keys');
            }
            if (MapSetModels.mapConditional(true) !== 1 || MapSetModels.mapConditional(false) !== 1) {
                throw Error('state fork');
            }
        """.trimIndent()

        assertNodeReplay(
            source = source + concrete + representatives,
            directory = directory,
            name = "native-map-set-models",
            timeoutMessage = "Native collection regression timed out",
        )
    }

    @Test
    fun `unimplemented iterable input and capacity overflow stay explicit residual paths`() {
        for (name in listOf("iterableConstructor", "capacityBoundary")) {
            val method = getMethod(methodName = name, className = "MapSetModels")
            val outcome = TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
                machine.analyzeWithMetadata(methods = listOf(method))
            }

            assertTrue(outcome.unsupportedCall, name)
            assertTrue(outcome.states.isEmpty(), name)
            assertTrue(!outcome.engineFailed, name)
        }
    }

    @Test
    fun `explicit old catalog excludes collection semantics`() {
        val oldIds = TsBuiltInUnknownCallModels.catalog().modelIds.filterNot { id ->
            id.startsWith("ts.collection.") || id == "ts.map.constructor" || id == "ts.set.constructor"
        }.toSet()
        val baselineOptions = tsOptions.copy(unknownCallModelSelection = TsUnknownCallModelSelection.Only(oldIds))
        val method = getMethod(methodName = "mapOverwrite", className = "MapSetModels")

        val outcome = TsMachine(scene = scene, options = options, tsOptions = baselineOptions).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertTrue(outcome.unsupportedCall)
        assertTrue(outcome.states.isEmpty())
        assertTrue(!outcome.engineFailed)
    }

    @Test
    fun `source generator yield delegation is unsupported rather than an engine error`() {
        val method = getMethod(methodName = "delegatedYield", className = "MapSetModels")
        val collectingOptions = options.copy(throwExceptionOnStepFailure = false)
        val outcome = TsMachine(scene = scene, options = collectingOptions, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertTrue(!outcome.engineFailed)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { it.contains("Not supported") })
    }

    private fun assertOne(methodName: String) {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = methodName, className = "MapSetModels"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }
}
