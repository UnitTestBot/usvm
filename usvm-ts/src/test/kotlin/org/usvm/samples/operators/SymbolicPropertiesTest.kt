package org.usvm.samples.operators

import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInputPropertyPresence
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsTestResolver
import org.usvm.util.eq
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SymbolicPropertiesTest : PropertyTestRunner("/samples/operators/SymbolicProperties.ts") {
    override val tsOptions: TsOptions = TsOptions(maxArraySize = 8)

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
    fun `equivalent declarations from a superclass and interface retain field metadata`() {
        checkSingleInput(name = "multiplePropertyDeclarations", expected = setOf(1))
        checkSingleInput(name = "multipleStringDeclarations", expected = setOf(1, 2))
        checkSingleInput(name = "multipleOptionalDeclarations", expected = setOf(0, 1, 3))
        checkSingleInput(name = "multipleInterfaceDeclarations", expected = setOf(1))
        checkSingleInput(name = "classFieldOverridesInterfaceContract", expected = setOf(1, 2))
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
        }
    }

    @Test
    fun `string concatenation preserves both aliased and distinct receiver paths`() {
        val method = getMethod(methodName = "aliasStringConcat", className = "SymbolicProperties")

        discoverProperties<TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, _, result -> result eq 0 },
            { _, _, result -> result eq 1 },
            { _, _, result -> result eq 2 },
            invariants = arrayOf({ _, _, result -> result.number.toInt() in setOf(0, 1, 2) }),
        )
    }

    @Test
    fun `string concatenation preserves nested alias branches and the last write`() {
        val method = getMethod(methodName = "nestedAliasStringConcat", className = "SymbolicProperties")

        discoverProperties<TsTestValue, TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, _, _, result -> result eq 0 },
            { _, _, _, result -> result eq 1 },
            { _, _, _, result -> result eq 2 },
            { _, _, _, result -> result eq 3 },
            { _, _, _, result -> result eq 4 },
            invariants = arrayOf({ _, _, _, result -> result.number.toInt() in setOf(0, 1, 2, 3, 4) }),
        )
    }

    @Test
    fun `string concatenation resolves conditional references in both operands`() {
        val method = getMethod(methodName = "bothOperandsAliasStringConcat", className = "SymbolicProperties")

        discoverProperties<TsTestValue, TsTestValue, TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, _, _, result -> result eq 0 },
            { _, _, _, result -> result eq 1 },
            { _, _, _, result -> result eq 2 },
            { _, _, _, result -> result eq 3 },
            { _, _, _, result -> result eq 4 },
            invariants = arrayOf({ _, _, _, result -> result.number.toInt() in setOf(0, 1, 2, 3, 4) }),
        )
    }

    @Test
    fun `string concatenation ignores infeasible absent and undefined payload branches`() {
        checkSingleInput(name = "optionalStringConcat", expected = setOf(0, 1, 2, 3))
    }

    @Test
    fun `unsupported numeric concatenation leaves the feasible string path available`() {
        val method = getMethod(methodName = "mixedKindStringConcat", className = "SymbolicProperties")
        val outcome = TsMachine(scene, options = options, tsOptions = tsOptions).use {
            it.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isNotEmpty())
        assertTrue(outcome.unsupportedPaths.all { it.startsWith("Unsupported string concatenation operand:") })

        val tests = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            TsTestResolver().resolve(method, state)
        }
        val results = tests.map { assertIs<TsTestValue.TsNumber>(it.returnValue).number }.toSet()

        assertEquals(setOf(1.0, 2.0), results)
        replay(method, tests)
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
        }
    }

    @Test
    fun `symbolic keys and inherited prototype names remain explicit unsupported outcomes`() {
        listOf("symbolicKey", "prototypeName").forEach { name ->
            val method = getMethod(methodName = name, className = "SymbolicProperties")
            val options = options.copy(throwExceptionOnStepFailure = false)
            val outcome = TsMachine(scene, options = options, tsOptions = tsOptions).use {
                it.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.states.isEmpty(), name)
            assertTrue(outcome.unsupportedPaths.isNotEmpty(), name)
        }
    }

    private fun checkSingleInput(name: String, expected: Set<Int>) {
        val method = getMethod(methodName = name, className = "SymbolicProperties")
        val matchers: Array<(TsTestValue, TsTestValue.TsNumber) -> Boolean> = expected.map { number ->
            { _: TsTestValue, result: TsTestValue.TsNumber -> result eq number }
        }.toTypedArray()

        discoverProperties(
            method = method,
            analysisResultMatchers = matchers,
            invariants = arrayOf({ _: TsTestValue, result: TsTestValue.TsNumber -> result.number.toInt() in expected }),
        )
    }
}

class SymbolicPropertyPresenceModesTest : PropertyTestRunner("/samples/operators/SymbolicProperties.ts") {
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
