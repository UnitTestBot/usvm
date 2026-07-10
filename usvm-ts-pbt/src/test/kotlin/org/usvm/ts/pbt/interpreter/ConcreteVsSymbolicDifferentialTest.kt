package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.ts.pbt.report.SYMBOLIC_STRING_PLACEHOLDER
import org.usvm.ts.pbt.report.toVValueOrNull
import org.usvm.ts.pbt.util.getResourcePath
import org.usvm.util.TsTestResolver
import kotlin.time.Duration.Companion.seconds

/**
 * Differential oracle: for every input model the *symbolic* engine produces,
 * the *concrete* interpreter must compute the same result.
 *
 * Skipped (not failed) cases: non-replayable inputs (symbolic string placeholders),
 * `Unsupported`/`Diverged` concrete outcomes. The test requires zero mismatches
 * and at least one successfully compared execution per method suite.
 */
@EnabledIfEnvironmentVariable(named = "ARKANALYZER_DIR", matches = ".+")
class ConcreteVsSymbolicDifferentialTest {

    private fun loadScene(resourcePath: String): EtsScene {
        val file = loadEtsFileAutoConvert(getResourcePath(resourcePath))
        return EtsScene(listOf(file))
    }

    private fun symbolicTests(scene: EtsScene, method: EtsMethod): List<TsTest> {
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
            exceptionsPropagation = true,
            timeout = 60.seconds,
            stepsFromLastCovered = 3500L,
            solverType = SolverType.YICES,
            solverTimeout = kotlin.time.Duration.INFINITE,
            typeOperationsTimeout = kotlin.time.Duration.INFINITE,
        )
        return TsMachine(scene, options, TsOptions()).use { machine ->
            val states = machine.analyze(listOf(method))
            states.map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    data class Verdict(val compared: Int, val skipped: Int, val mismatches: List<String>)

    /**
     * Known divergences between the symbolic engine and precise JS semantics,
     * confirmed by manual analysis. Keyed by "ClassName.methodName".
     *
     * These are *engine* issues, i.e. differential-testing findings:
     * - `Add.addUnknownValues`: for reference operands usvm-ts approximates `+`
     *   numerically (e.g. `null + {}` becomes fp NaN), while JS applies ToPrimitive
     *   and string concatenation (`null + {} === "null[object Object]"`), so the
     *   engine follows the `res != res` (NaN) branch that is concretely unreachable.
     * - `Less.lessUnknown`: for mixed-type fake-object operands the engine resolves
     *   relational operators per sort pair (`Lt.onBool = !lhs && rhs`, see
     *   TsBinaryOperator.Lt) instead of the JS ToNumber coercion, so e.g.
     *   `false < 0.0` is reported `true` while JS gives `0 < 0 === false`.
     * - `And.andOfUnknown`: frontends lower `if (x)` to the idiom `x != 0`; the
     *   engine evaluates it numerically, so for `x = undefined` it gets
     *   `ToNumber(undefined) = NaN != 0 -> true` while `undefined` is falsy in JS
     *   (the NaN hole of the compare-to-zero truthiness contract).
     * - `TypeCoercion.transitiveCoercionNoTypes`: doubly ambiguous — the *genuine*
     *   source-level `c != 0` loose comparison is indistinguishable in the IR from
     *   the truthiness idiom (the concrete interpreter follows the idiom contract),
     *   and the engine's `&&` on references diverges from JS anyway
     *   (JS gives 2, engine 1, concrete 4).
     *
     * NOTE: requires the CI-pinned ArkAnalyzer (`neo/2025-09-03`). Older/newer AA
     * builds may emit a different if-successor order in the DTO, which inverts
     * every branch after the jacodb lift (observed with `lipen/usvm`).
     */
    private val knownEngineDivergences: Set<String> = setOf(
        "Add.addUnknownValues",
        "Less.lessUnknown",
        "And.andOfUnknown", // FIXED in caelmbleidd/ts-interpreter-fixes (truthiness idiom
        // resolved via mkTruthyExpr for all operand kinds); unwhitelist after the
        // engine branch is merged and the dependency is picked up.
        "TypeCoercion.transitiveCoercionNoTypes",
    )

    private fun runDifferential(resourcePath: String, className: String): Verdict {
        val scene = loadScene(resourcePath)
        val cls = scene.projectAndSdkClasses.single { it.name == className }
        val methods = cls.methods.filter {
            !it.name.startsWith("%") && it.name != "constructor" && it.cfg.stmts.isNotEmpty()
        }
        val interpreter = EtsConcreteInterpreter(scene)
        val classResolver = { name: String -> scene.projectAndSdkClasses.firstOrNull { it.name == name } }

        var compared = 0
        var skipped = 0
        val mismatches = mutableListOf<String>()

        for (method in methods) {
            if ("$className.${method.name}" in knownEngineDivergences) {
                skipped++
                continue
            }
            val tests = try {
                symbolicTests(scene, method)
            } catch (e: Throwable) {
                // e.g. NotImplementedError for BigInt-typed parameters
                skipped++
                continue
            }
            for (test in tests) {
                val ctx = "${method.name}(${test.before.parameters})"

                val thisValue = test.before.thisInstance?.toVValueOrNull(classResolver) ?: VUndefined
                val args = test.before.parameters.map {
                    it.toVValueOrNull(classResolver) ?: run { skipped++; return@map null }
                }
                if (args.any { it == null }) continue
                @Suppress("UNCHECKED_CAST")
                val result = interpreter.execute(method, thisValue, args as List<VValue>)

                when (result) {
                    is ExecutionResult.Unsupported, is ExecutionResult.Diverged -> skipped++
                    is ExecutionResult.Threw ->
                        if (test.returnValue is TsTestValue.TsException) compared++
                        else mismatches += "$ctx: concrete threw ${result.value}, symbolic returned ${test.returnValue}"

                    is ExecutionResult.Returned -> {
                        if (test.returnValue is TsTestValue.TsException) {
                            mismatches += "$ctx: concrete returned ${result.value}, symbolic threw ${test.returnValue}"
                        } else if (matches(test.returnValue, result.value)) {
                            compared++
                        } else if (containsPlaceholder(test.returnValue)) {
                            skipped++
                        } else {
                            mismatches += "$ctx: symbolic=${test.returnValue}, concrete=${result.value}"
                        }
                    }
                }
            }
        }
        return Verdict(compared, skipped, mismatches)
    }

    private fun containsPlaceholder(v: TsTestValue): Boolean = when (v) {
        is TsTestValue.TsString -> v.value == SYMBOLIC_STRING_PLACEHOLDER
        is TsTestValue.TsArray<*> -> v.values.any { containsPlaceholder(it) }
        is TsTestValue.TsClass -> v.properties.values.any { containsPlaceholder(it) }
        else -> false
    }

    private fun matches(expected: TsTestValue, actual: VValue): Boolean = when (expected) {
        is TsTestValue.TsNumber ->
            actual is VNumber && (expected.number == actual.value ||
                (expected.number.isNaN() && actual.value.isNaN()))

        is TsTestValue.TsBoolean -> actual is VBool && expected.value == actual.value
        is TsTestValue.TsString -> actual is VString && expected.value == actual.value
        TsTestValue.TsNull -> actual == VNull
        TsTestValue.TsUndefined -> actual == VUndefined

        is TsTestValue.TsArray<*> ->
            actual is VArray && expected.values.size == actual.elements.size &&
                expected.values.zip(actual.elements).all { (e, a) -> matches(e, a) }

        is TsTestValue.TsClass ->
            actual is VObject && expected.properties.all { (k, v) ->
                matches(v, actual.fields[k] ?: VUndefined)
            }

        else -> false
    }

    @ParameterizedTest
    @CsvSource(
        "/samples/operators/Add.ts, Add",
        "/samples/operators/Less.ts, Less",
        "/samples/operators/Neg.ts, Neg",
        "/samples/operators/And.ts, And",
        "/samples/operators/Equality.ts, Equality",
        "/samples/lang/Truthy.ts, Truthy",
        "/samples/lang/TypeCoercion.ts, TypeCoercion",
    )
    fun differential(resourcePath: String, className: String) {
        val verdict = runDifferential(resourcePath, className)
        println("[$className] compared=${verdict.compared}, skipped=${verdict.skipped}, mismatches=${verdict.mismatches.size}")
        verdict.mismatches.forEach { println("  MISMATCH: $it") }
        assertTrue(verdict.compared > 0) { "no successfully compared executions for $className" }
        assertTrue(verdict.mismatches.isEmpty()) {
            "differential mismatches for $className:\n" + verdict.mismatches.joinToString("\n")
        }
    }
}
