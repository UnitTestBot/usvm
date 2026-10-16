package org.usvm.samples.types

import org.jacodb.ets.model.EtsMethod
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DateConstructorIdentityTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = "/samples/types/DateConstructorIdentity.ts"
    override val scene = loadScene(sourcePath)
    private var modelsEnabled = false

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        val selection = if (modelsEnabled) {
            TsUnknownCallModelSelection.All
        } else {
            TsUnknownCallModelSelection.Only(ids = emptySet())
        }
        val selectedOptions = tsOptions.copy(
            unknownCallModelSelection = selection,
            unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
        )

        TsMachine(scene = scene, options = options, tsOptions = selectedOptions).use { machine ->
            val result = machine.analyzeWithMetadata(listOf(method))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
            assertFalse(result.engineFailed)
            assertTrue(result.unsupportedPaths.isEmpty(), result.unsupportedPaths.joinToString())
            result.states.map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Date has a stable constructor identity and exact instanceof semantics`(withModels: Boolean) {
        modelsEnabled = withModels
        options = options.copy(timeout = 5.seconds, stepsFromLastCovered = 200L)
        val methods = listOf(
            "direct", "noArguments", "argumentEffects", "stableGlobal", "aliased", "inheritedConstructor", "otherValues",
            "shadowed",
        )

        methods.forEach { methodName ->
            discoverProperties<TsTestValue.TsBoolean>(
                method = getMethod(methodName = methodName, className = "DateConstructorIdentity"),
                { result -> result.value },
                invariants = arrayOf({ result -> result.value }),
            )
        }

        replay(methods)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Date constructor properties retain inheritance and own overrides`(withModels: Boolean) {
        modelsEnabled = withModels
        options = options.copy(timeout = 5.seconds, stepsFromLastCovered = 200L)
        val methods = listOf("cloneThroughConstructor", "ownConstructor", "deletedOwnConstructor")

        methods.forEach { methodName ->
            discoverProperties<TsTestValue.TsBoolean>(
                method = getMethod(methodName = methodName, className = "DateConstructorIdentity"),
                { result -> result.value },
                invariants = arrayOf({ result -> result.value }),
            )
        }

        replay(methods)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Date prototype mutation remains explicitly unsupported`(withModels: Boolean) {
        val selection = if (withModels) {
            TsUnknownCallModelSelection.All
        } else {
            TsUnknownCallModelSelection.Only(ids = emptySet())
        }
        val selectedOptions = tsOptions.copy(
            unknownCallModelSelection = selection,
            unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
        )
        val method = getMethod(methodName = "mutatedPrototype", className = "DateConstructorIdentity")

        TsMachine(scene = scene, options = options, tsOptions = selectedOptions).use { machine ->
            val result = machine.analyzeWithMetadata(listOf(method))

            assertFalse(result.engineFailed)
            assertTrue(result.states.isEmpty())
            assertTrue(result.unsupportedPaths.any { it.contains("Date.prototype access is not modeled") })
        }
    }

    private fun replay(methodNames: List<String>) {
        val source = getResourcePath(sourcePath).readText()
        val assertions = methodNames.joinToString(separator = "\n") { name ->
            "if (!new DateConstructorIdentity().$name()) throw Error('$name');"
        }

        assertNodeReplay(
            source = "$source\n$assertions",
            directory = directory,
            name = "date-identity-$modelsEnabled",
            timeoutMessage = "Date constructor replay timed out",
        )
    }
}
