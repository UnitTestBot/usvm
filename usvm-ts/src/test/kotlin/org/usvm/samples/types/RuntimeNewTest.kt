package org.usvm.samples.types

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
import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RuntimeNewTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/types/RuntimeNew.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `conditional constructor call selects class and executes its constructor`() {
        for (methodName in listOf("dynamicCall", "inlineConditional", "localAlias")) {
            val method = getMethod(methodName = methodName, className = "RuntimeNew")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            val cases = outcome.states.map { state ->
                assertIs<TsMethodResult.Success>(state.methodResult)
                val test = TsTestResolver().resolve(method, state)
                val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
                val actual = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                assertEquals(if (input) 1.0 else 0.0, actual, "$methodName($input)")
                input to actual
            }
            assertEquals(setOf(false, true), cases.map { it.first }.toSet(), methodName)

            replay(cases.map { (input, actual) ->
                "if (new RuntimeNew().$methodName($input) !== ${actual.toInt()}) " +
                    "throw Error('$methodName($input)');"
            })
        }
    }

    @Test
    fun `constructor identity is captured before an argument changes its binding`() {
        assertSingleNumberResult(methodName = "sideEffectingArgument", expected = 1.0)

        replay(listOf(
            "if (new RuntimeNew().sideEffectingArgument() !== 1) throw Error('constructor snapshot');"
        ))
    }

    @Test
    fun `field read uses the selected class when a constructor union has common property names`() {
        val numberMethod = getMethod(methodName = "valueFromEither", className = "RuntimeNew")
        val numberOutcome = analyze("valueFromEither")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, numberOutcome.stopReason)
        assertTrue(numberOutcome.unsupportedPaths.isEmpty(), "${numberOutcome.unsupportedPaths}")
        val numberCases = numberOutcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(numberMethod, state)
            val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            assertEquals(7.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
            input
        }
        assertEquals(setOf(false, true), numberCases.toSet())

        val sortMethod = getMethod(methodName = "fieldSortByRuntimeClass", className = "RuntimeNew")
        val sortOutcome = analyze("fieldSortByRuntimeClass")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, sortOutcome.stopReason)
        assertTrue(sortOutcome.unsupportedPaths.isEmpty(), "${sortOutcome.unsupportedPaths}")
        val sortCases = sortOutcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(sortMethod, state)
            val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            assertTrue(assertIs<TsTestValue.TsBoolean>(test.returnValue).value)
            input
        }
        assertEquals(setOf(false, true), sortCases.toSet())

        replay(numberCases.map { input ->
            "if (new RuntimeNew().valueFromEither($input) !== 7) throw Error('field number $input');"
        } + sortCases.map { input ->
            "if (new RuntimeNew().fieldSortByRuntimeClass($input) !== true) throw Error('field sort $input');"
        })
    }

    @Test
    fun `field writes and aliases use the selected class`() {
        for (methodName in listOf("writeToEither", "writeThroughAlias")) {
            val method = getMethod(methodName = methodName, className = "RuntimeNew")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            val cases = outcome.states.map { state ->
                assertIs<TsMethodResult.Success>(state.methodResult)
                val test = TsTestResolver().resolve(method, state)
                val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
                val actual = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                val expected = if (methodName == "writeToEither") 5.0 else 11.0
                assertEquals(expected, actual, "$methodName($input)")
                input to actual
            }
            assertEquals(setOf(false, true), cases.map { it.first }.toSet(), methodName)

            replay(cases.map { (input, actual) ->
                "if (new RuntimeNew().$methodName($input) !== ${actual.toInt()}) " +
                    "throw Error('$methodName($input)');"
            })
        }
    }

    @Test
    fun `field writes respect the selected runtime field sort`() {
        val methodName = "writeFieldWithDifferentRuntimeSort"
        val method = getMethod(methodName = methodName, className = "RuntimeNew")
        val outcome = analyze(methodName)

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val cases = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            assertTrue(assertIs<TsTestValue.TsBoolean>(test.returnValue).value)
            input
        }
        assertEquals(setOf(false, true), cases.toSet())

        replay(cases.map { input ->
            "if (new RuntimeNew().$methodName($input) !== true) throw Error('$methodName($input)');"
        })
    }

    @Test
    fun `runtime field sort change is reported as unsupported`() {
        val methodName = "writeIncompatibleRuntimeField"
        val method = getMethod(methodName = methodName, className = "RuntimeNew")
        val outcome = analyze(methodName)

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.any { "Assignment changes runtime sort" in it },
            "${outcome.unsupportedPaths}")
        val cases = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            assertTrue(assertIs<TsTestValue.TsBoolean>(test.returnValue).value)
            input
        }
        assertEquals(setOf(false), cases.toSet())

        replay(listOf(
            "if (new RuntimeNew().$methodName(false) !== true) throw Error('supported field sort');",
            "if (new RuntimeNew().$methodName(true) !== true) throw Error('unsupported field sort');",
        ))
    }

    @Test
    fun `incompatible alternatives of a union field value are reported as unsupported`() {
        val methodName = "writePossiblyIncompatibleRuntimeField"
        val method = getMethod(methodName = methodName, className = "RuntimeNew")
        val outcome = analyze(methodName)

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.any { "Assignment changes runtime sort" in it },
            "${outcome.unsupportedPaths}")
        val cases = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            val input = test.before.parameters.map { assertIs<TsTestValue.TsBoolean>(it).value }
            assertEquals(2, input.size)
            assertTrue(assertIs<TsTestValue.TsBoolean>(test.returnValue).value)
            input
        }
        assertEquals(setOf(listOf(true, false), listOf(false, true)), cases.toSet())

        replay(listOf(
            "if (new RuntimeNew().$methodName(true, false) !== true) throw Error('numeric field');",
            "if (new RuntimeNew().$methodName(false, true) !== true) throw Error('string field');",
            "if (new RuntimeNew().$methodName(true, true) !== true) throw Error('numeric type change');",
            "if (new RuntimeNew().$methodName(false, false) !== true) throw Error('string type change');",
        ))
    }

    @Test
    fun `selected constructor executes exactly once`() {
        val method = getMethod(methodName = "constructorCalledOnce", className = "RuntimeNew")
        val outcome = analyze("constructorCalledOnce")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val cases = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            assertTrue(assertIs<TsTestValue.TsBoolean>(test.returnValue).value)
            input
        }
        assertEquals(setOf(false, true), cases.toSet())

        replay(cases.map { input ->
            "if (new RuntimeNew().constructorCalledOnce($input) !== true) throw Error('ctor count $input');"
        })
    }

    @Test
    fun `direct construction still executes the constructor`() {
        assertSingleNumberResult(methodName = "direct", expected = 1.0)

        replay(listOf(
            "if (new RuntimeNew().direct() !== 1) throw Error('direct construction');"
        ))
    }

    @Test
    fun `known non constructor throws TypeError`() {
        val outcome = analyze("nonConstructable")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state ->
            val exception = assertIs<TsMethodResult.TsException>(state.methodResult)
            assertEquals("TypeError", exception.type.typeName)
        }

        replay(listOf(
            "let threwTypeError = false; try { new RuntimeNew().nonConstructable(); } " +
                "catch (error) { threwTypeError = error instanceof TypeError; } " +
                "if (!threwTypeError) throw Error('non constructor');"
        ))
    }

    @Test
    fun `unmodeled constructor identity is explicitly unsupported`() {
        val method = getMethod(methodName = "symbolicConstructor", className = "RuntimeNew")
        val outcome = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
            "${outcome.unsupportedPaths}")

        val unknownOutcome = analyze("unknownConstructor")
        assertEquals(TsAnalysisStopReason.EXHAUSTED, unknownOutcome.stopReason)
        assertTrue(unknownOutcome.states.isEmpty())
        assertTrue(unknownOutcome.unsupportedPaths.any { "new constructor" in it },
            "${unknownOutcome.unsupportedPaths}")
    }

    @Test
    fun `known object instance throws TypeError instead of allocating`() {
        val outcome = analyze("nonConstructableObject")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state ->
            val exception = assertIs<TsMethodResult.TsException>(state.methodResult)
            assertEquals("TypeError", exception.type.typeName)
        }

        replay(listOf(
            "let threwTypeError = false; try { new RuntimeNew().nonConstructableObject(); } " +
                "catch (error) { threwTypeError = error instanceof TypeError; } " +
                "if (!threwTypeError) throw Error('object constructor');"
        ))
    }

    private fun assertSingleNumberResult(methodName: String, expected: Double) {
        val method = getMethod(methodName = methodName, className = "RuntimeNew")
        val outcome = analyze(methodName)

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            assertEquals(expected, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }
    }

    private fun analyze(methodName: String) = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
        machine.analyzeWithOutcome(methods = listOf(getMethod(methodName = methodName, className = "RuntimeNew")))
    }

    private fun replay(assertions: List<String>) {
        val script = directory.resolve("runtime-new.ts")
        val output = directory.resolve("runtime-new.out")
        script.writeText(buildString {
            appendLine(getResourcePath(tsPath).readText())
            assertions.forEach(::appendLine)
        })

        val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Node replay timed out")
        assertEquals(0, process.exitValue(), output.readText())
    }

    private val machineOptions = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = 30.seconds,
    )
}
