package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class NullishCoalescing : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/NullishCoalescing.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `nullish coalescing operator`() {
        val method = getMethod(className, "testNullishCoalescing")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // null ?? "default" -> "default"
                a is TsTestValue.TsNull && r eq 1
            },
            { a, r ->
                // undefined ?? "default" -> "default"
                a is TsTestValue.TsUndefined && r eq 2
            },
            { a, r ->
                // false ?? "default" -> false
                a is TsTestValue.TsBoolean && !a.value && r eq 3
            },
            { a, r ->
                // 0 ?? "default" -> 0
                a is TsTestValue.TsNumber && a.number == 0.0 && r eq 4
            },
            { a, r ->
                // "" ?? "default" -> ""
                a is TsTestValue.TsString && a.value == "" && r eq 5
            },
            // Fallback case is also reachable:
            { _, r -> r eq 100 },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `nullish coalescing chaining`() {
        val method = getMethod(className, "testNullishChaining")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // null ?? undefined ?? "value" -> "value"
                r eq 1
            },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `nullish coalescing with objects`() {
        val method = getMethod(className, "testNullishWithObjects")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // null ?? {..} -> {..}
                r eq 1
            },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }
}
