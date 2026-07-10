package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN

class IncDec : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/IncDec.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test preIncrement`() {
        val method = getMethod("preIncrement")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r -> a.isNaN() && r.isNaN() },
            { a, r -> !a.isNaN() && (r.number eq a.number + 1) },
            invariants = arrayOf(
                { a, r ->
                    if (a.isNaN()) r.isNaN() else r.number eq a.number + 1
                },
            )
        )
    }

    @Test
    fun `test preDecrement`() {
        val method = getMethod("preDecrement")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r -> a.isNaN() && r.isNaN() },
            { a, r -> !a.isNaN() && (r.number eq a.number - 1) },
            invariants = arrayOf(
                { a, r ->
                    if (a.isNaN()) r.isNaN() else r.number eq a.number - 1
                },
            )
        )
    }

    @Test
    fun `test decrementLoop`() {
        val method = getMethod("decrementLoop")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            // Classify by the observed depth, not by n: non-integral inputs
            // (e.g. n = 2.5) legitimately reach depth ceil(n).
            { n, r -> (n.number <= 0 || n.isNaN()) && (r eq 0) },
            { n, r -> r eq 3 },
            { n, r -> r.number > 0 && r.number < 3 },
            invariants = arrayOf(
                { _, r -> r.number >= 0 && r.number <= 3 },
            ),
        )
    }
}
