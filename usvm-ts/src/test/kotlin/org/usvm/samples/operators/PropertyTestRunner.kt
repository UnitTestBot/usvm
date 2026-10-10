package org.usvm.samples.operators

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.call.TsCompatibilityUnknownCallDispatcher
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Property discovery and Node replay consume the same exhaustive analysis and witness snapshots. */
abstract class PropertyTestRunner(protected val sourcePath: String) : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    override val scene: EtsScene = loadScene(sourcePath)

    init {
        options = options.copy(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            throwExceptionOnStepFailure = true,
        )
    }

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = tsOptions,
            unknownCallDispatcher = TsCompatibilityUnknownCallDispatcher,
        ).use { it.analyzeWithOutcome(methods = listOf(method)) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, method.name)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${method.name}: ${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty(), method.name)
        val tests = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult, method.name)
            TsTestResolver().resolve(method, state)
        }
        replay(method, tests)
        tests
    }

    private fun replay(method: EtsMethod, tests: List<TsTest>) {
        val assertions = buildString {
            tests.forEachIndexed { index, test ->
                appendLine("{")
                val serializer = ReplayObjects(this)
                val args = test.before.parameters.map { serializer.value(it) }
                appendLine("const actual = new ${method.enclosingClass!!.name}().${method.name}(${args.joinToString()});")
                val expected = serializer.value(test.returnValue)
                appendLine("if (!same(actual, $expected)) throw Error('${method.name} result $index');")
                test.after.parameters.forEachIndexed { argIndex, after ->
                    val expectedAfter = serializer.value(after)
                    appendLine("if (!same(${args[argIndex]}, $expectedAfter)) throw Error('${method.name} input $argIndex state $index');")
                }
                appendLine("}")
            }
        }
        val equals = """
            function same(a, b) {
                if (Object.is(a, b)) return true;
                if (a === null || b === null || typeof a !== 'object' || typeof b !== 'object') return false;
                const ka = Object.keys(a), kb = Object.keys(b);
                return ka.length === kb.length && ka.every(k => Object.hasOwn(b, k) && same(a[k], b[k]));
            }
        """.trimIndent()

        replayInOperatorScript(directory, sourcePath, method.name, "$equals\n$assertions")
    }

    private class ReplayObjects(private val script: StringBuilder) {
        private val objects = IdentityHashMap<TsTestValue, String>()

        fun value(value: TsTestValue): String = when (value) {
            is TsTestValue.TsBoolean -> value.value.toString()
            is TsTestValue.TsNumber -> when {
                value.number.isNaN() -> "NaN"
                value.number == Double.POSITIVE_INFINITY -> "Infinity"
                value.number == Double.NEGATIVE_INFINITY -> "-Infinity"
                else -> value.number.toString()
            }
            is TsTestValue.TsString -> jsString(value.value)
            TsTestValue.TsUndefined -> "undefined"
            TsTestValue.TsNull -> "null"
            is TsTestValue.TsClass -> objects[value] ?: run {
                val ref = "obj${objects.size}"
                objects[value] = ref
                script.appendLine("const $ref = {};")
                value.properties.forEach { (key, field) ->
                    val payload = value(field)
                    script.appendLine("Object.defineProperty($ref, ${jsString(key)}, {value: $payload, writable: true, enumerable: true, configurable: true});")
                }
                ref
            }
            is TsTestValue.TsArray<*> -> "[${value.values.joinToString { value(it) }}]"
            else -> error("Unsupported replay value: $value")
        }
    }
}

internal fun replayInOperatorScript(
    directory: Path,
    sourcePath: String,
    scriptName: String,
    assertions: String,
) {
    val source = buildString {
        appendLine(getResourcePath(sourcePath).readText())
        append(assertions)
    }

    assertNodeReplay(
        source = source,
        directory = directory,
        name = scriptName,
        timeoutMessage = "Node replay timed out: $scriptName",
    )
}
