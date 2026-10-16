package org.usvm.util

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UConcreteHeapRef
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.api.evalTypeEquals
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.expr.mkNotNullOrUndefined
import org.usvm.solver.USatResult
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsStringWitnessResolverTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/lang/SymbolicStringInput.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `unused dynamic input preserves its constant return value`() {
        val method = getMethod(methodName = "unusedDynamicInput", className = "SymbolicStringInput")

        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, result -> result.number == 7.0 },
            invariants = arrayOf({ _, result -> result.number == 7.0 }),
        )
    }

    @Test
    fun `unused dynamic input has a string witness when its completed type model selects string`() {
        val method = getMethod(methodName = "unusedDynamicInput", className = "SymbolicStringInput")
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            val state = machine.analyze(methods = listOf(method)).single()
            val reference = state.symbolicStringCandidates.single()
            with(state.ctx) {
                // Exercise witness completion after analysis; execution never needed this input's contents.
                val parameter = state.memory.read(mkRegisterStackLValue(addressSort, idx = 1)) as UConcreteHeapRef
                state.pathConstraints += parameter.getFakeType(state.memory).refTypeExpr
                state.pathConstraints += state.memory.types.evalTypeEquals(reference, EtsStringType)
                state.pathConstraints += mkNotNullOrUndefined(reference)

                val result = solver<EtsType>().check(state.pathConstraints)

                assertTrue(result is USatResult)
                state.models = listOf(result.model)
            }

            val witness = TsTestResolver().resolve(method = method, state = state)

            assertIs<TsTestValue.TsString>(witness.before.parameters.single())
            assertEquals(witness.before.parameters, witness.after.parameters)
            assertEquals(7.0, assertIs<TsTestValue.TsNumber>(witness.returnValue).number)
            assertTrue(state.boundedStringBackingRefs.isEmpty())
        }
    }

    @Test
    fun `string inferred from any has replayable length witnesses`() {
        val method = getMethod(methodName = "anyStringLength", className = "SymbolicStringInput")
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyze(methods = listOf(method))
        }
        val tests = states.map { state -> TsTestResolver().resolve(method, state) }

        assertTrue(states.isNotEmpty())
        assertEquals(
            setOf(0.0, 1.0, 2.0),
            tests.map { test -> assertIs<TsTestValue.TsNumber>(test.returnValue).number }.toSet(),
        )
        tests.forEach { test ->
            val input = test.before.parameters.single()
            val expected = when {
                input !is TsTestValue.TsString -> 0.0
                input.value.length == 1 -> 1.0
                else -> 2.0
            }

            assertEquals(expected, assertIs<TsTestValue.TsNumber>(test.returnValue).number, message = "$test")
        }

        val script = buildString {
            appendLine(getResourcePath(tsPath).readText())
            appendLine("if (new SymbolicStringInput().anyStringLength(\"\") !== 2) throw Error('empty string');")
            appendLine("if (new SymbolicStringInput().anyStringLength(\"x\") !== 1) throw Error('one-char string');")
            tests.forEachIndexed { index, test ->
                val input = when (val value = test.before.parameters.single()) {
                    TsTestValue.TsUndefined -> "undefined"
                    TsTestValue.TsNull -> "null"
                    is TsTestValue.TsBoolean -> value.value.toString()
                    is TsTestValue.TsNumber -> value.number.toString()
                    is TsTestValue.TsString -> jsString(value.value)
                    is TsTestValue.TsClass -> "{}"
                    else -> error("Unexpected input for anyStringLength: $value")
                }
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number

                appendLine("if (new SymbolicStringInput().anyStringLength($input) !== $expected) {")
                appendLine("  throw Error('any string witness $index');")
                appendLine("}")
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "any-string",
            timeoutMessage = "Any-string replay timed out",
        )
    }
}
