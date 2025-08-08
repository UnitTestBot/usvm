package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN
import kotlin.test.Test

@Disabled("Unary plus is not supported by ArkAnalyzer. https://gitcode.com/openharmony-sig/arkanalyzer/issues/737")
class UnaryPlus : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/UnaryPlus.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `unary plus on any`() {
        val method = getMethod("testUnaryPlusAny")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            // The result is always a (non-negative) number, but the value depends on the input
            { _, r -> r.number >= 0 },
            invariants = arrayOf(
                { _, r -> r.number >= 0 }
            )
        )
    }

    @Test
    fun `unary plus on boolean`() {
        val method = getMethod("testUnaryPlusBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // +true = 1
                (r eq 1) && a.value
            },
            { a, r ->
                // +false = 0
                (r eq 2) && !a.value
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on number`() {
        val method = getMethod("testUnaryPlusNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, r ->
                // +a = a
                r eq 1
            },
            { a, r ->
                // +NaN = NaN
                (r eq 2) && a.isNaN()
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on null`() {
        val method = getMethod("testUnaryPlusNull")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // +null = 0
                r eq 1
            },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on undefined`() {
        val method = getMethod("testUnaryPlusUndefined")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // +undefined = NaN
                (r eq 1)
            },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on string 42`() {
        val method = getMethod("testUnaryPlusString42")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { s, r ->
                // +"42" = 42
                (r eq 1) && s.value == "42"
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on string`() {
        val method = getMethod("testUnaryPlusString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { s, r ->
                // +"42" = 42
                (r eq 1) && s.value == "42"
            },
            { s, r ->
                // +"0" = 0
                (r eq 2) && s.value == "0"
            },
            { s, r ->
                // +"" = 1
                (r eq 3) && s.value == ""
            },
            { s, r ->
                // +"abc" = NaN
                (r eq 4) && s.value == "abc"
            },
            { s, r ->
                // +"NaN" = NaN
                (r eq 5) && s.value == "NaN"
            },
            { s, r ->
                // +"Infinity" = Infinity
                (r eq 6) && s.value == "Infinity"
            },
            { s, r ->
                // +"-Infinity" = -Infinity
                (r eq 7) && s.value == "-Infinity"
            },
            { s, r ->
                // +"1e+100" = 1e+100
                (r eq 8) && s.value == "1e+100"
            },
            { s, r ->
                // +"1e-100" = 1e-100
                (r eq 9) && s.value == "1e-100"
            },
            { s, r ->
                // +"1e+1000" = Infinity
                (r eq 10) && s.value == "1e+1000"
            },
            { s, r ->
                // +"1e-1000" = 0
                (r eq 11) && s.value == "1e-1000"
            },
            { s, r ->
                // +"1.7976931348623157e+308" = Infinity
                (r eq 12) && s.value == "1.7976931348623157e+308"
            },
            { s, r ->
                // +"2e308" = Infinity
                (r eq 13) && s.value == "2e308"
            },
            { s, r ->
                // +"5e-324" = 5e-324
                (r eq 14) && s.value == "5e-324"
            },
            { s, r ->
                // +"1e-324" = 0
                (r eq 15) && s.value == "1e-324"
            },
            // Fallback case is also reachable:
            { _, r -> r eq 100 },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on object`() {
        val method = getMethod("testUnaryPlusObject")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // +{} = 0
                r eq 1
            },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }
}
