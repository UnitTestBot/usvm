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

class RuntimeInstanceofTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/types/RuntimeInstanceof.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `dynamic constructor value selects both outcomes`() {
        val method = getMethod(methodName = "dynamicConstructor", className = "RuntimeInstanceof")
        val outcome = analyze("dynamicConstructor")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val cases = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            val actual = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            assertEquals(if (input) 1.0 else 0.0, actual)
            input to actual
        }
        assertEquals(setOf(false, true), cases.map { it.first }.toSet())

        replay(cases.map { (input, actual) ->
            "if (new RuntimeInstanceof().dynamicConstructor($input) !== ${actual.toInt()}) throw Error('dynamic $input');"
        })
    }

    @Test
    fun `typeof recognizes constructor values stored in a local`() {
        val method = getMethod(methodName = "aliasedClassTypeof", className = "RuntimeInstanceof")
        val outcome = analyze("aliasedClassTypeof")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val inputs = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
            assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
        }
        assertEquals(setOf(false, true), inputs.toSet())

        replay(inputs.map { input ->
            "if (new RuntimeInstanceof().aliasedClassTypeof($input) !== 1) throw Error('typeof $input');"
        })
    }

    @Test
    fun `direct and inherited checks use declared class identity`() {
        val expected = mapOf(
            "directConstructor" to 1.0,
            "castConstructor" to 1.0,
            "nonNullConstructor" to 1.0,
            "anyConstructor" to 1.0,
            "unknownConstructor" to 1.0,
            "objectConstructor" to 1.0,
            "unrelatedConstructor" to 0.0,
            "classTypeof" to 1.0,
            "primitiveLeft" to 0.0,
        )

        expected.forEach { (methodName, expectedResult) ->
            val method = getMethod(methodName = methodName, className = "RuntimeInstanceof")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            outcome.states.forEach { state ->
                assertIs<TsMethodResult.Success>(state.methodResult, methodName)
                val test = TsTestResolver().resolve(method, state)
                assertEquals(expectedResult, assertIs<TsTestValue.TsNumber>(test.returnValue).number, methodName)
            }
        }

        replay(expected.map { (methodName, expectedResult) ->
            "if (new RuntimeInstanceof().$methodName() !== ${expectedResult.toInt()}) throw Error('$methodName');"
        })
    }

    @Test
    fun `nullish left operands are not class instances`() {
        val methods = listOf("undefinedLeft", "nullLeft")

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "RuntimeInstanceof")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), methodName)
            outcome.states.forEach { state ->
                assertIs<TsMethodResult.Success>(state.methodResult)
                val test = TsTestResolver().resolve(method, state)
                assertEquals(0.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number, methodName)
            }
        }

        replay(methods.map { methodName ->
            "if (new RuntimeInstanceof().$methodName() !== 0) throw Error('$methodName');"
        })
    }

    @Test
    fun `subclass instance belongs to declared parent constructor`() {
        val method = getMethod(methodName = "inheritedConstructor", className = "RuntimeInstanceof")
        val outcome = analyze("inheritedConstructor")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        replay(listOf(
            "if (new RuntimeInstanceof().inheritedConstructor(new InstanceChild()) !== 1) throw Error('parent');"
        ))
    }

    @Test
    fun `non callable RHS and custom hasInstance are explicitly unsupported`() {
        val nonCallable = analyze("nonCallableRight")
        val custom = analyze("customHasInstance")
        val inherited = analyze("inheritedHasInstance")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, nonCallable.stopReason)
        assertTrue(nonCallable.states.isEmpty())
        assertTrue(nonCallable.unsupportedPaths.any { "TypeError" in it }, "${nonCallable.unsupportedPaths}")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, custom.stopReason)
        assertTrue(custom.states.isEmpty())
        assertTrue(custom.unsupportedPaths.any { "Symbol.hasInstance" in it }, "${custom.unsupportedPaths}")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, inherited.stopReason)
        assertTrue(inherited.states.isEmpty())
        assertTrue(inherited.unsupportedPaths.any { "Symbol.hasInstance" in it }, "${inherited.unsupportedPaths}")

        replay(listOf(
            "let typeError = false; try { new RuntimeInstanceof().nonCallableRight(); } " +
                "catch (error) { typeError = error instanceof TypeError; } " +
                "if (!typeError) throw Error('TypeError');",
            "if (new RuntimeInstanceof().customHasInstance() !== false) throw Error('custom hasInstance');",
            "if (new RuntimeInstanceof().inheritedHasInstance(new InstanceHasInstanceChild()) !== false) " +
                "throw Error('inherited hasInstance');",
        ))
    }

    @Test
    fun `constructor typed input has an explicit unsupported outcome`() {
        val outcome = analyze("constructorParameter")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
            "${outcome.unsupportedPaths}")

        replay(listOf(
            "if (new RuntimeInstanceof().constructorParameter(InstanceA) !== true) throw Error('A input');",
            "if (new RuntimeInstanceof().constructorParameter(InstanceB) !== false) throw Error('B input');",
        ))
    }

    @Test
    fun `unsupported constructor input does not hide another entrypoint`() {
        val methods = listOf("constructorParameter", "directConstructor")
            .map { getMethod(methodName = it, className = "RuntimeInstanceof") }
        val outcome = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = methods)
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.any { "constructorParameter" in it }, "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state -> assertIs<TsMethodResult.Success>(state.methodResult) }
    }

    private fun analyze(methodName: String) = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
        machine.analyzeWithOutcome(methods = listOf(getMethod(methodName = methodName, className = "RuntimeInstanceof")))
    }

    private fun replay(assertions: List<String>) {
        val script = directory.resolve("runtime-instanceof.ts")
        val output = directory.resolve("runtime-instanceof.out")
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
