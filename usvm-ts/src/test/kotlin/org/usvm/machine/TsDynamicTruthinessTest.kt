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
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
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

class TsDynamicTruthinessTest : TsMethodTestRunner() {
    private data class AnalysisWitnesses(
        val analysis: TsAnalysisResult,
        val witnesses: List<TsTest>,
    )

    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))

    @Test
    fun `dynamic truthiness prepares string backing without losing object paths`() {
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = getMethod(methodName = "objectTruthy", className = "SymbolicStringInput"),
            { _, result -> result.number == 1.0 },
            invariants = arrayOf({ _, result -> result.number == 1.0 }),
        )

        val names = setOf(
            "anyTruthy",
            "anyNegated",
            "anyAnd",
            "anyOr",
            "anyObjectTruthy",
            "objectTruthy",
            "truthinessBeforeKind",
            "equalsObjectBeforeKind",
        )
        val methods = scene.projectClasses.single { it.name == "SymbolicStringInput" }.methods
            .filter { it.name in names }
            .associateBy { it.name }
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
        )

        val results = TsMachine(
            scene,
            options = machineOptions,
            tsOptions = TsOptions(maxArraySize = 2),
        ).use { machine ->
            methods.mapValues { (_, method) ->
                val analysis = machine.analyzeWithOutcome(listOf(method))
                val witnesses = analysis.states.map { state -> TsTestResolver().resolve(method, state) }

                AnalysisWitnesses(analysis, witnesses)
            }
        }

        assertEquals(names, methods.keys)
        results.forEach { (name, result) ->
            val (analysis, witnesses) = result
            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
            assertTrue(analysis.unsupportedPaths.isEmpty(), "$name: ${analysis.unsupportedPaths}")
            if (name == "anyObjectTruthy") {
                assertTrue(
                    witnesses.any { witness ->
                        witness.before.parameters.single() is TsTestValue.TsClass &&
                            assertIs<TsTestValue.TsNumber>(witness.returnValue).number == 1.0
                    },
                    "$name lost its object path",
                )
            }
            assertTrue(witnesses.isNotEmpty(), "$name produced no supported witnesses")
        }

        val script = buildString {
            appendLine(source.readText())
            results.forEach { (name, result) ->
                result.witnesses.forEachIndexed { index, witness ->
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

    @Test
    fun `truthiness before type refinement retains empty strings null and undefined`() {
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = getMethod(methodName = "truthinessBeforeKind", className = "SymbolicStringInput"),
            { input, result -> input is TsTestValue.TsString && input.value.isEmpty() && result.number == 10.0 },
            { input, result -> input is TsTestValue.TsString && input.value.isNotEmpty() && result.number == 21.0 },
            { input, result -> input == TsTestValue.TsNull && result.number == 30.0 },
            { input, result -> input == TsTestValue.TsUndefined && result.number == 40.0 },
        )
    }

    @Test
    fun `object equality before type refinement retains all runtime kinds`() {
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = getMethod(methodName = "equalsObjectBeforeKind", className = "SymbolicStringInput"),
            { input, result -> input is TsTestValue.TsString && result.number == 10.0 },
            { input, result -> input == TsTestValue.TsNull && result.number == 20.0 },
            { input, result -> input == TsTestValue.TsUndefined && result.number == 30.0 },
            { input, result ->
                val otherKind = input !is TsTestValue.TsString &&
                    input != TsTestValue.TsNull && input != TsTestValue.TsUndefined

                otherKind && result.number == 40.0
            },
            invariants = arrayOf({ _, result -> result.number < 100.0 }),
        )
    }
}
