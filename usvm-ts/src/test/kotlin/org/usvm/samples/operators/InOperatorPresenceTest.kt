package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class InOperatorPresenceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/operators/InOperator.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `in checks own field presence through writes and deletion`() {
        val methods = listOf(
            "hasPresentNumberProperty",
            "hasUndefinedProperty",
            "lacksProperty",
            "lacksOptionalProperty",
            "hasOwnConstructorMethod",
            "hasAddedProperty",
            "lacksDeletedProperty",
            "hasRestoredProperty",
        )

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "InOperator")
            val outcome = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), methodName)
            val tests = outcome.states.map { state ->
                assertIs<TsMethodResult.Success>(state.methodResult, methodName)
                TsTestResolver().resolve(method, state)
            }
            tests.forEach { test ->
                assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue, methodName).number)
            }

            replay(methodName, tests.map { test ->
                assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
            })
        }
    }

    @Test
    fun `conditional deletion preserves both presence outcomes`() {
        val method = getMethod(methodName = "conditionalDelete", className = "InOperator")
        val outcome = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val tests = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            TsTestResolver().resolve(method, state)
        }
        assertEquals(setOf(false, true), tests.map { test ->
            assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
        }.toSet())

        tests.forEach { test ->
            val shouldDelete = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            assertEquals(if (shouldDelete) 0.0 else 1.0, result)
        }
        replayConditional(tests.map { test ->
            assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
        })
    }

    @Test
    fun `added field remains readable after presence check`() {
        val methodName = "readsAddedProperty"
        val method = getMethod(methodName = methodName, className = "InOperator")
        val options = machineOptions.copy(throwExceptionOnStepFailure = true)
        val outcome = TsMachine(scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty(), "Expected a successful read of the written field")
        val tests = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            TsTestResolver().resolve(method, state)
        }

        tests.forEach { test ->
            val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
            val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            assertEquals(input, result)
        }

        replayRead(methodName, tests.map { test ->
            assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
        })
    }

    @Test
    fun `absent optional field reads as undefined after negative presence check`() {
        val methodName = "readsMissingOptionalAfterIn"
        val method = getMethod(methodName = methodName, className = "InOperator")
        val options = machineOptions.copy(throwExceptionOnStepFailure = true)
        val outcome = TsMachine(scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty(), "Expected a successful read of undefined")
        val tests = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            TsTestResolver().resolve(method, state)
        }
        tests.forEach { test ->
            assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        replayNoArguments(methodName, expected = 1)
    }

    @Test
    fun `block scoped top level write updates field presence`() {
        val methodName = "readsBlockScopedResult"
        val method = getMethod(methodName = methodName, className = "InOperator")
        val options = machineOptions.copy(throwExceptionOnStepFailure = true)
        val outcome = TsMachine(scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty(), "Expected the module initializer to complete")
        val tests = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            TsTestResolver().resolve(method, state)
        }
        tests.forEach { test ->
            assertEquals(7.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        replayNoArguments(methodName, expected = 7)
    }

    @Test
    fun `unmodeled keys arrays and prototypes have explicit unsupported outcomes`() {
        val methods = listOf(
            "hasSymbolicKey",
            "hasInputProperty",
            "testInOperatorObject",
            "testInOperatorArray",
            "testInOperatorObjectAfterDelete",
            "specialPrototypeInitializer",
            "inheritedThroughPrototypeInitializer",
            "inheritedThroughAssignedPrototype",
            "deletedToStringExposesPrototype",
            "inheritedConstructor",
        )

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "InOperator")
            val outcome = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isEmpty(), methodName)
            assertTrue(outcome.unsupportedPaths.isNotEmpty(), methodName)
            if (methodName.endsWith("PrototypeInitializer") || methodName == "inheritedThroughAssignedPrototype") {
                assertTrue(outcome.unsupportedPaths.any { "prototype mutation" in it },
                    "${outcome.unsupportedPaths}")
            }
            if (methodName == "deletedToStringExposesPrototype" || methodName == "inheritedConstructor") {
                assertTrue(outcome.unsupportedPaths.any { it.contains("prototype lookup", ignoreCase = true) },
                    "${outcome.unsupportedPaths}")
            }
        }

        replayPrototypeInitializer()
    }

    private fun replay(methodName: String, values: List<Double>) {
        val assertions = buildString {
            values.forEachIndexed { index, value ->
                appendLine("if (new InOperator().$methodName($value) !== 1) throw Error('state $index');")
            }
        }

        replayInOperatorScript(directory, tsPath, methodName, assertions)
    }

    private fun replayConditional(values: List<Boolean>) {
        val assertions = buildString {
            values.forEachIndexed { index, value ->
                appendLine(
                    "if (new InOperator().conditionalDelete($value) !== ${if (value) 0 else 1}) " +
                        "throw Error('state $index');"
                )
            }
        }

        replayInOperatorScript(directory, tsPath, scriptName = "conditionalDelete", assertions = assertions)
    }

    private fun replayRead(methodName: String, values: List<Double>) {
        val assertions = buildString {
            values.forEachIndexed { index, value ->
                appendLine("if (new InOperator().$methodName($value) !== $value) throw Error('state $index');")
            }
        }

        replayInOperatorScript(directory, tsPath, methodName, assertions)
    }

    private fun replayNoArguments(methodName: String, expected: Int) {
        val assertions = "if (new InOperator().$methodName() !== $expected) throw Error('$methodName');\n"

        replayInOperatorScript(directory, tsPath, methodName, assertions)
    }

    private fun replayPrototypeInitializer() {
        val assertions =
            "if (new InOperator().specialPrototypeInitializer() !== false) throw Error('null prototype');\n" +
            "if (new InOperator().inheritedThroughPrototypeInitializer() !== true) throw Error('inherited');\n" +
            "if (new InOperator().inheritedThroughAssignedPrototype() !== true) throw Error('assigned prototype');\n" +
            "if (new InOperator().deletedToStringExposesPrototype() !== true) throw Error('revealed prototype');\n" +
            "if (new InOperator().inheritedConstructor() !== true) throw Error('inherited constructor');\n"

        replayInOperatorScript(directory, tsPath, scriptName = "specialPrototypeInitializer", assertions = assertions)
    }

    private val machineOptions = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = Duration.INFINITE,
    )
}
