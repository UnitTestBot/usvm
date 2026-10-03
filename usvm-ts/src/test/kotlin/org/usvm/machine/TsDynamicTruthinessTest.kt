package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

private const val TRUTHINESS_REPLAY_FAILURE_CONTEXT_LIMIT = 1000
private const val UNBACKED_STRING_REASON = "Truthiness needs a modeled string backing for dynamic references"

class TsDynamicTruthinessTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unbacked any strings are unsupported in truthiness without losing object paths`() {
        val source = getResourcePath("/models/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val names = setOf("anyTruthy", "anyNegated", "objectTruthy")
        val methods = scene.projectClasses.single { it.name == "SymbolicStringInput" }.methods
            .filter { it.name in names }
            .associateBy { it.name }
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
        )

        val results = TsMachine(
            scene,
            options = machineOptions,
            tsOptions = TsOptions(maxArraySize = 2),
        ).use { machine ->
            methods.mapValues { (_, method) ->
                val analysis = machine.analyzeWithOutcome(listOf(method))
                val witnesses = analysis.states.map { state -> TsTestResolver().resolve(method, state) }

                analysis to witnesses
            }
        }

        assertEquals(names, methods.keys)
        results.forEach { (name, result) ->
            val (analysis, witnesses) = result
            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
            if (name == "objectTruthy") {
                assertTrue(analysis.unsupportedPaths.isEmpty(), "$name: ${analysis.unsupportedPaths}")
                assertTrue(
                    witnesses.any { witness -> witness.before.parameters.single() is TsTestValue.TsClass },
                    "$name lost its object path",
                )
            } else {
                assertTrue(UNBACKED_STRING_REASON in analysis.unsupportedPaths, "$name: ${analysis.unsupportedPaths}")
            }
            assertTrue(witnesses.isNotEmpty(), "$name produced no supported witnesses")
        }

        val script = buildString {
            appendLine(source.readText())
            results.forEach { (name, result) ->
                result.second.forEachIndexed { index, witness ->
                    val input = when (val value = witness.before.parameters.single()) {
                        is TsTestValue.TsBoolean -> value.value.toString()
                        is TsTestValue.TsNumber -> value.number.toString()
                        is TsTestValue.TsString -> jsString(value.value)
                        is TsTestValue.TsClass -> "{}"
                        is TsTestValue.TsArray<*> -> "[]"
                        TsTestValue.TsNull -> "null"
                        TsTestValue.TsUndefined -> "undefined"
                        else -> error("Unexpected truthiness input: $value")
                    }
                    val expected = assertIs<TsTestValue.TsNumber>(witness.returnValue).number

                    appendLine("if (new SymbolicStringInput().$name($input) !== $expected) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "any-truthiness",
            timeoutMessage = "any-truthiness replay timed out",
            failureContext = script.take(TRUTHINESS_REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }
}
