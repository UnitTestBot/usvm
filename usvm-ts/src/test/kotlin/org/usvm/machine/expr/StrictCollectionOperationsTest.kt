package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test

class StrictCollectionOperationsTest : TsMethodTestRunner() {
    override val scene = loadEtsProjectAutoConvert(
        getResourcePath("/models/strict-collections"),
        useArkAnalyzerTypeInference = null,
    )
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
    fun `Array from accepts the native strict null checks constructor signature`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "nativeFromKeys", className = "StrictCollections"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `Node confirms strict project collection behavior`() {
        val source = getResourcePath("/models/strict-collections/StrictCollections.ts").readText()

        assertNodeReplay(
            source = source + "if (StrictCollections.nativeFromKeys() !== 1) throw Error('strict from');",
            directory = directory,
            name = "native-strict-collection-operations",
            timeoutMessage = "Native strict collection regression timed out",
        )
    }
}
