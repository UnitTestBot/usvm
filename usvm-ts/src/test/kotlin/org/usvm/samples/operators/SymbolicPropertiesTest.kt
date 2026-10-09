package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInputPropertyPresence
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.eq
import org.usvm.util.jsString
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

open class SymbolicPropertiesTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = "/samples/operators/SymbolicProperties.ts"
    override val scene: EtsScene = loadScene(sourcePath)
    override val tsOptions: TsOptions = TsOptions(maxArraySize = 8)
    private val machineOptions = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = Duration.INFINITE,
        throwExceptionOnStepFailure = true,
    )

    @Test
    fun `required fields exist and optional or unknown fields have both initial possibilities`() {
        checkSingleInput(name = "required", expected = setOf(1))
        checkSingleInput(name = "inheritedFields", expected = setOf(1))
        checkSingleInput(name = "optional", expected = setOf(0, 1))
        checkSingleInput(name = "unknown", expected = setOf(0, 1))
        checkSingleInput(name = "repeatedPresence", expected = setOf(1))
        checkSingleInput(name = "absentRead", expected = setOf(1, 2))
        checkSingleInput(name = "readsUnknown", expected = setOf(0, 1, 2, 3))
        checkSingleInput(name = "comparesUnknownValues", expected = setOf(0, 1, 2))
    }

    @Test
    fun `literal payloads can be added and their runtime kind can change`() {
        val methods = listOf(
            "writesNumber", "writesBoolean", "writesString", "writesUndefined", "writesNull",
            "writesObject", "writesArray", "changesDeclaredKind", "changesKinds", "bracketLiteral", "nestedInput",
            "deletesBracketLiteral", "numericLiteralKey", "lengthProperty", "unusualKeys", "rewritesLongLiteral",
        )

        methods.forEach { checkSingleInput(it, expected = setOf(1)) }
    }

    @Test
    fun `deletion restoration and local aliases agree with reads and presence`() {
        listOf("deletes", "deletesUnknown", "restores", "localAlias", "deletesLongLiteral").forEach {
            checkSingleInput(it, expected = setOf(1))
        }
    }

    @Test
    fun `conditional writes deletes and kind changes remain branch independent`() {
        listOf("conditionalWrite", "conditionalDelete", "conditionalKinds").forEach { name ->
            val method = getMethod(methodName = name, className = "SymbolicProperties")

            val trueResult = if (name == "conditionalDelete") 0 else 1
            val falseResult = 1 - trueResult

            discoverProperties<TsTestValue, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
                method = method,
                { _, flag, result -> flag.value && result eq trueResult },
                { _, flag, result -> !flag.value && result eq falseResult },
                invariants = arrayOf({ _, flag, result -> result eq if (flag.value) trueResult else falseResult }),
            )
            replay(name, analyze(name))
        }
    }

    @Test
    fun `distinct and aliased symbolic receivers share only matching heap updates`() {
        listOf("aliasWrite", "aliasKindChange", "aliasDelete", "aliasStringChange", "aliasObjectChange", "aliasArrayChange").forEach { name ->
            val method = getMethod(methodName = name, className = "SymbolicProperties")

            discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
                method = method,
                { _, _, result -> result eq 0 },
                { _, _, result -> result eq 1 },
                invariants = arrayOf({ _, _, result -> result eq 0 || result eq 1 }),
            )
            replay(name, analyze(name))
        }
    }

    @Test
    fun `symbolic payload writes do not force input field capability`() {
        val name = "writesSymbolic"
        val method = getMethod(methodName = name, className = "SymbolicProperties")

        discoverProperties<TsTestValue, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, value, result -> !value.number.isNaN() && result eq 1 },
            { _, value, result -> value.number.isNaN() && result eq -1 },
            invariants = arrayOf({ _, value, result -> result eq if (value.number.isNaN()) -1 else 1 }),
        )
        replay(name, analyze(name))
    }

    @Test
    fun `symbolic boolean string reference and unknown payloads preserve their values`() {
        listOf("writesSymbolicBoolean", "writesSymbolicString", "writesSymbolicObject", "copiesUnknown").forEach { name ->
            val method = getMethod(methodName = name, className = "SymbolicProperties")

            discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
                method = method,
                { _, _, result -> result eq 1 },
                invariants = arrayOf({ _, _, result -> result eq 1 }),
            )
            replay(name, analyze(name))
        }
    }

    @Test
    fun `optional string presence guards string backing constraints`() {
        checkSingleInput(name = "optionalUndefinedPresence", expected = setOf(0, 1, 2))
        checkSingleInput(name = "optionalString", expected = setOf(0, 1, 2, 3))
    }

    @Test
    fun `returned object snapshots include additions and omit deletions`() {
        listOf("returnsWrittenObject", "returnsDeletedObject").forEach { name ->
            val method = getMethod(methodName = name, className = "SymbolicProperties")

            discoverProperties<TsTestValue.TsClass, TsTestValue.TsClass>(
                method = method,
                { _, result -> if (name == "returnsWrittenObject") "fresh" in result.properties else "x" !in result.properties },
                invariants = arrayOf({ _, result ->
                    if (name == "returnsWrittenObject") "fresh" in result.properties else "x" !in result.properties
                }),
            )
            replay(name, analyze(name))
        }
    }

    @Test
    fun `symbolic keys and inherited prototype names remain explicit unsupported outcomes`() {
        listOf("symbolicKey", "prototypeName").forEach { name ->
            val method = getMethod(methodName = name, className = "SymbolicProperties")
            val options = machineOptions.copy(throwExceptionOnStepFailure = false)
            val outcome = TsMachine(scene, options = options, tsOptions = tsOptions).use {
                it.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.states.isEmpty(), name)
            assertTrue(outcome.unsupportedPaths.isNotEmpty(), name)
        }
    }

    protected fun checkSingleInput(name: String, expected: Set<Int>) {
        val method = getMethod(methodName = name, className = "SymbolicProperties")
        val matchers: Array<(TsTestValue, TsTestValue.TsNumber) -> Boolean> = expected.map { number ->
            { _: TsTestValue, result: TsTestValue.TsNumber -> result eq number }
        }.toTypedArray()

        val tests = analyze(name)

        discoverProperties(
            method = method,
            analysisResultMatchers = matchers,
            invariants = arrayOf({ _: TsTestValue, result: TsTestValue.TsNumber -> result.number.toInt() in expected }),
        )
        replay(name, tests)
    }

    private fun analyze(name: String): List<TsTest> {
        val method = getMethod(methodName = name, className = "SymbolicProperties")
        val outcome = TsMachine(scene, options = machineOptions, tsOptions = tsOptions).use {
            it.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, name)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "$name: ${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty(), name)
        return outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult, name)
            TsTestResolver().resolve(method, state)
        }
    }

    private fun replay(name: String, tests: List<TsTest>) {
        val assertions = buildString {
            tests.forEachIndexed { index, test ->
                appendLine("{")
                val serializer = ReplayObjects(this)
                val args = test.before.parameters.map { serializer.value(it) }
                appendLine("const actual = new SymbolicProperties().$name(${args.joinToString()});")
                val expected = serializer.value(test.returnValue)
                appendLine("if (!same(actual, $expected)) throw Error('$name result $index');")
                test.after.parameters.forEachIndexed { argIndex, after ->
                    val expectedAfter = serializer.value(after)
                    appendLine("if (!same(${args[argIndex]}, $expectedAfter)) throw Error('$name input $argIndex state $index');")
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

        replayInOperatorScript(directory, sourcePath, name, "$equals\n$assertions")
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

class SymbolicPropertyPresenceModesTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/operators/SymbolicProperties.ts")
    private var policy = TsInputPropertyPresence.DECLARED_FIELDS
    override val tsOptions: TsOptions get() = TsOptions(inputPropertyPresence = policy)

    @Test
    fun `explicit policies affect initial presence but writes and deletions override them`() {
        val expectedRequired = mapOf(
            TsInputPropertyPresence.DECLARED_FIELDS to setOf(1),
            TsInputPropertyPresence.SYMBOLIC to setOf(0, 1),
            TsInputPropertyPresence.ASSUME_PRESENT to setOf(1),
            TsInputPropertyPresence.ASSUME_ABSENT to setOf(0),
        )

        expectedRequired.forEach { (mode, expected) ->
            policy = mode
            val method = getMethod(methodName = "required", className = "SymbolicProperties")
            val matchers: Array<(TsTestValue, TsTestValue.TsNumber) -> Boolean> = expected.map { number ->
                { _: TsTestValue, result: TsTestValue.TsNumber -> result eq number }
            }.toTypedArray()

            discoverProperties(
                method = method,
                analysisResultMatchers = matchers,
                invariants = arrayOf({ _: TsTestValue, result: TsTestValue.TsNumber -> result.number.toInt() in expected }),
            )
            val expectedUnknown = when (mode) {
                TsInputPropertyPresence.ASSUME_PRESENT -> setOf(1)
                TsInputPropertyPresence.ASSUME_ABSENT -> setOf(0)
                else -> setOf(0, 1)
            }
            listOf("optional", "unknown").forEach { name ->
                val initialMatchers: Array<(TsTestValue, TsTestValue.TsNumber) -> Boolean> = expectedUnknown.map { number ->
                    { _: TsTestValue, result: TsTestValue.TsNumber -> result eq number }
                }.toTypedArray()
                discoverProperties(
                    method = getMethod(methodName = name, className = "SymbolicProperties"),
                    analysisResultMatchers = initialMatchers,
                    invariants = arrayOf({ _: TsTestValue, result: TsTestValue.TsNumber ->
                        result.number.toInt() in expectedUnknown
                    }),
                )
            }
            listOf("writesUndefined", "deletes", "restores").forEach { name ->
                discoverProperties<TsTestValue, TsTestValue.TsNumber>(
                    method = getMethod(methodName = name, className = "SymbolicProperties"),
                    { _, result -> result eq 1 },
                    invariants = arrayOf({ _, result -> result eq 1 }),
                )
            }
        }
    }
}
