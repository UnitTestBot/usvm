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

class CollectionOperationsTest : TsMethodTestRunner() {
    override val scene = loadScene("/models/CollectionOperations.ts")
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

    private val methods = listOf(
        "iterableSet", "iterableMap", "mapInvalidEntry", "copyCollections", "arrayFromKeys",
        "liveMapIterator", "clearDuringIteration", "setEntryAndZero", "arrayEntryLiveLength",
        "arrayKeysAndHoles", "arrayMap", "arrayMapMutation", "arrayForEachMutation", "callbackThrows",
        "callbackWithoutThis", "shadowedArrayFrom", "implicitCollectionIteration",
    )

    @Test
    fun `iterableSet preserves native source semantics`() {
        assertOne(methodName = "iterableSet")
    }

    @Test
    fun `iterableMap preserves native source semantics`() {
        assertOne(methodName = "iterableMap")
    }

    @Test
    fun `mapInvalidEntry preserves native source semantics`() {
        assertOne(methodName = "mapInvalidEntry")
    }

    @Test
    fun `copyCollections preserves native source semantics`() {
        assertOne(methodName = "copyCollections")
    }

    @Test
    fun `arrayFromKeys preserves native source semantics`() {
        assertOne(methodName = "arrayFromKeys")
    }

    @Test
    fun `liveMapIterator preserves native source semantics`() {
        assertOne(methodName = "liveMapIterator")
    }

    @Test
    fun `clearDuringIteration preserves native source semantics`() {
        assertOne(methodName = "clearDuringIteration")
    }

    @Test
    fun `setEntryAndZero preserves native source semantics`() {
        assertOne(methodName = "setEntryAndZero")
    }

    @Test
    fun `arrayEntryLiveLength preserves native source semantics`() {
        assertOne(methodName = "arrayEntryLiveLength")
    }

    @Test
    fun `arrayKeysAndHoles preserves native source semantics`() {
        assertOne(methodName = "arrayKeysAndHoles")
    }

    @Test
    fun `arrayMap preserves native source semantics`() {
        assertOne(methodName = "arrayMap")
    }

    @Test
    fun `arrayMapMutation preserves native source semantics`() {
        assertOne(methodName = "arrayMapMutation")
    }

    @Test
    fun `arrayForEachMutation preserves native source semantics`() {
        assertOne(methodName = "arrayForEachMutation")
    }

    @Test
    fun `callbackThrows preserves native source semantics`() {
        assertOne(methodName = "callbackThrows")
    }

    @Test
    fun `live iterator state remains independent across symbolic forks`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "iteratorFork", className = "CollectionOperations"),
            { branch, result -> branch.value && result.number == 1.0 },
            { branch, result -> !branch.value && result.number == 1.0 },
            invariants = arrayOf({ _, result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `Node confirms order mutation holes callbacks and errors`() {
        val source = getResourcePath("/models/CollectionOperations.ts").readText()
        val assertions = methods.joinToString(separator = "\n") { name ->
            "if (CollectionOperations.$name() !== 1) throw Error('$name');"
        }

        assertNodeReplay(
            source = source + assertions +
                "if (CollectionOperations.iteratorFork(true) !== 1 || " +
                "CollectionOperations.iteratorFork(false) !== 1) throw Error('fork');",
            directory = directory,
            name = "native-collection-operations",
            timeoutMessage = "Native collection operation regression timed out",
        )
    }

    @Test
    fun `non-undefined callback thisArg stays an explicit residual boundary`() {
        val method = getMethod(methodName = "unsupportedThisArg", className = "CollectionOperations")

        val outcome = TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertTrue(outcome.unsupportedCall)
        assertTrue(outcome.states.isEmpty())
        assertTrue(!outcome.engineFailed)
    }

    @Test
    fun `callbacks receive undefined this without a thisArg`() {
        assertOne(methodName = "callbackWithoutThis")
    }

    @Test
    fun `shadowed Array from executes the user function`() {
        assertOne(methodName = "shadowedArrayFrom")
    }

    @Test
    fun `implicit Set iteration executes the selected iterator model`() {
        assertOne(methodName = "implicitCollectionIteration")
    }

    @Test
    fun `previous catalog cannot silently execute the new operations`() {
        val newIds = setOf(
            "ts.array.map", "ts.array.forEach", "ts.array.fromIterable", "ts.map.fromIterable", "ts.set.fromIterable",
            "ts.iterator.keys", "ts.iterator.values", "ts.iterator.entries", "ts.iterator.collection",
        )
        val baselineIds = TsBuiltInUnknownCallModels.catalog().modelIds.toSet() - newIds
        val baselineOptions = tsOptions.copy(unknownCallModelSelection = TsUnknownCallModelSelection.Only(baselineIds))
        val names = listOf(
            "iterableSet",
            "arrayMap",
            "arrayFromKeys",
            "arrayEntryLiveLength",
            "implicitCollectionIteration",
        )
        for (name in names) {
            val method = getMethod(methodName = name, className = "CollectionOperations")

            val outcome = TsMachine(scene = scene, options = options, tsOptions = baselineOptions).use { machine ->
                machine.analyzeWithMetadata(methods = listOf(method))
            }

            assertTrue(outcome.states.isEmpty(), name)
            assertTrue(!outcome.engineFailed, name)
        }
    }

    private fun assertOne(methodName: String) {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = methodName, className = "CollectionOperations"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }
}
