package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.isTruthy
import java.nio.file.Path
import kotlin.io.path.readText

private val TsTestValue.TsBoolean.number: Double
    get() = if (value) 1.0 else 0.0

class TypeCoercion : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/lang/TypeCoercion.ts"

    override val scene: EtsScene = loadScene(tsPath)

    init {
        options = options.copy(stopOnCoverage = 0, stateCollectionStrategy = StateCollectionStrategy.ALL)
    }

    @Test
    fun `test argWithConst`() {
        val method = getMethod("argWithConst")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> (a.number == 1.0) && (r.number == 1.0) },
            { a, r -> (a.number != 1.0) && (r.number == 0.0) },
        )
    }

    @Test
    fun `test dualBoolean`() {
        val method = getMethod("dualBoolean")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, r -> (a.number == 0.0) && (r.number == -1.0) },
            { a, r -> (a.number == 1.0) && (r.number == 2.0) },
            { a, r -> (a.number != 0.0) && (a.number != 1.0) && (r.number == 3.0) },
            invariants = arrayOf(
                { _, r -> r.number != 1.0 }
            )
        )
    }

    @Test
    @Disabled("Unsupported string")
    fun `test dualBooleanWithoutTypes`() {
        val method = getMethod("dualBooleanWithoutTypes")

        discoverProperties<TsTestValue.TsUnknown, TsTestValue.TsNumber>(
            method,
        )
    }

    @Test
    fun `test argWithArg`() {
        val method = getMethod("argWithArg")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, b, r -> (a.number + b.number == 10.0) && (r.number == 1.0) },
            { a, b, r -> (a.number + b.number != 10.0) && (r.number == 0.0) },
        )
    }

    @Test
    fun `test unreachableByType`() {
        val method = getMethod("unreachableByType")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, b, r -> (a.number != b.number) && (r.number == 2.0) },
            { a, b, r -> (a.number == b.number) && !(isTruthy(a) && !b.value) && (r.number == 1.0) },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 },
            )
        )
    }

    @Test
    @Disabled("Wrong IR, incorrect handling of NaN value")
    fun `test transitiveCoercion`() {
        val method = getMethod("transitiveCoercion")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, b, c, r -> (a.number == b.number) && (b.number == c.number) && (r.number == 1.0) },
            { a, b, c, r -> (a.number == b.number) && (b.number != c.number || !isTruthy(c)) && (r.number == 2.0) },
            { a, b, _, r -> (a.number != b.number) && (r.number == 3.0) },
        )
    }

    @Test
    fun `untyped transitive coercion retains every numeric branch`() {
        val method = getMethod("transitiveCoercionNumbers")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, _, _, result -> result.number == 1.0 },
            { _, _, _, result -> result.number == 2.0 },
            { _, _, _, result -> result.number == 3.0 },
            { _, _, _, result -> result.number == 4.0 },
            invariants = arrayOf({ a, b, c, result ->
                result.number == transitiveResult(a.number, b.number, c.number)
            }),
        )
    }

    @Test
    fun `untyped transitive coercion converts boolean inputs to numbers`() {
        val method = getMethod("transitiveCoercionBoolean")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, _, _, result -> result.number == 2.0 },
            { _, _, _, result -> result.number == 3.0 },
            { _, _, _, result -> result.number == 4.0 },
            invariants = arrayOf({ a, b, c, result ->
                result.number == transitiveResult(a.number, b.number, c.number)
            }),
        )
    }

    @Test
    fun `native coercion agrees with numeric and boolean branch expectations`() {
        assertNodeReplay(
            source = getResourcePath(tsPath).readText() + "\n" + """
                const subject = new TypeCoercion();
                const numeric = [
                    [2, 2, 2, 1], [2, 2, 3, 2], [1, 2, 3, 3], [0, 0, 0, 4],
                    [NaN, NaN, 2, 3], [Infinity, Infinity, Infinity, 1], [-0, 0, -0, 4],
                ];
                for (const [a, b, c, expected] of numeric) {
                    if (subject.transitiveCoercionNumbers(a, b, c) !== expected) throw Error('numeric coercion');
                }
                for (const [a, b, c, expected] of [[0, false, 2, 2], [1, false, 2, 3], [0, false, 0, 4]]) {
                    if (subject.transitiveCoercionBoolean(a, b, c) !== expected) throw Error('boolean coercion');
                }
            """.trimIndent(),
            directory = directory,
            name = "transitive-coercion",
            timeoutMessage = "Native transitive coercion replay timed out",
        )
    }

    private fun transitiveResult(a: Double, b: Double, c: Double): Double = when {
        a != b -> 3.0
        c == 0.0 || c == 1.0 -> 4.0
        !c.isNaN() && a == c -> 1.0
        else -> 2.0
    }
}
