package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsSymbolicStringInputTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `symbolic string witnesses replay including empty and nonempty values`() {
        val source = getResourcePath("/models/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "SymbolicStringInput" }.methods
            .filter { it.name in setOf("identity", "lengthOne", "literal", "literalLength") }
            .associateBy { it.name }
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        val tests = TsMachine(scene, options = options, tsOptions = TsOptions()).use { machine ->
            methods.mapValues { (_, method) ->
                machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
            }
        }

        val identityTests = tests.getValue("identity")
        assertTrue(identityTests.isNotEmpty())
        identityTests.forEach { test ->
            val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
            val result = assertIs<TsTestValue.TsString>(test.returnValue).value
            assertEquals(input, result, message = test.toString())
        }

        val lengthTests = tests.getValue("lengthOne")
        assertEquals(setOf(0.0, 1.0), lengthTests.map { test ->
            assertIs<TsTestValue.TsNumber>(test.returnValue).number
        }.toSet())
        assertTrue(lengthTests.any { test ->
            assertIs<TsTestValue.TsString>(test.before.parameters.single()).value.isEmpty()
        })
        assertTrue(lengthTests.any { test ->
            assertIs<TsTestValue.TsString>(test.before.parameters.single()).value.isNotEmpty()
        })

        val literalTests = tests.getValue("literal")
        assertTrue(literalTests.isNotEmpty())
        literalTests.forEach { test ->
            assertEquals("A\u0000\u03a9\uD83D\uDE00", assertIs<TsTestValue.TsString>(test.returnValue).value)
        }

        val literalLengthTests = tests.getValue("literalLength")
        assertTrue(literalLengthTests.isNotEmpty())
        literalLengthTests.forEach { test ->
            assertEquals(5.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    val args = test.before.parameters.joinToString { value ->
                        jsString(assertIs<TsTestValue.TsString>(value).value)
                    }
                    val expected = when (val result = test.returnValue) {
                        is TsTestValue.TsString -> jsString(result.value)
                        is TsTestValue.TsNumber -> result.number.toString()
                        else -> error("Unexpected result for $name: $result")
                    }

                    appendLine("if (new SymbolicStringInput().$name($args) !== $expected) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }
        val replay = directory.resolve("replay.ts")
        val output = directory.resolve("replay.out")
        replay.writeText(script)

        val process = ProcessBuilder("node", "--experimental-strip-types", replay.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "String witness replay timed out")
            assertEquals(0, process.exitValue(), "${output.readText()}\n$script")
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun jsString(value: String): String = value.map { "\\u%04x".format(it.code) }.joinToString(
        separator = "",
        prefix = "\"",
        postfix = "\"",
    )
}
