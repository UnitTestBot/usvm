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
        val method = getMethod(className, "testUnaryPlusAny")
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
        val method = getMethod(className, "testUnaryPlusBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // +true = 1
                a.value && (r eq 1)
            },
            { a, r ->
                // +false = 0
                !a.value && (r eq 2)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on number`() {
        val method = getMethod(className, "testUnaryPlusNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // +a = a
                r eq 1
            },
            { a, r ->
                // +NaN = NaN
                a.isNaN() && (r eq 2)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on null`() {
        val method = getMethod(className, "testUnaryPlusNull")
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
        val method = getMethod(className, "testUnaryPlusUndefined")
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
        val method = getMethod(className, "testUnaryPlusString42")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { s, r ->
                // +"42" = 42
                s.value == "42" && (r eq 1)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `unary plus on string`() {
        val method = getMethod(className, "testUnaryPlusString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { s, r ->
                // +"42" = 42
                s.value == "42" && (r eq 1)
            },
            { s, r ->
                // +"0" = 0
                s.value == "0" && (r eq 2)
            },
            { s, r ->
                // +"" = 1
                s.value == "" && (r eq 3)
            },
            { s, r ->
                // +"abc" = NaN
                s.value == "abc" && (r eq 4)
            },
            { s, r ->
                // +"NaN" = NaN
                s.value == "NaN" && (r eq 5)
            },
            { s, r ->
                // +"Infinity" = Infinity
                s.value == "Infinity" && (r eq 6)
            },
            { s, r ->
                // +"-Infinity" = -Infinity
                s.value == "-Infinity" && (r eq 7)
            },
            { s, r ->
                // +"1e+100" = 1e+100
                s.value == "1e+100" && (r eq 8)
            },
            { s, r ->
                // +"1e-100" = 1e-100
                s.value == "1e-100" && (r eq 9)
            },
            { s, r ->
                // +"1e+1000" = Infinity
                s.value == "1e+1000" && (r eq 10)
            },
            { s, r ->
                // +"1e-1000" = 0
                s.value == "1e-1000" && (r eq 11)
            },
            { s, r ->
                // +"1.7976931348623157e+308" = Infinity
                s.value == "1.7976931348623157e+308" && (r eq 12)
            },
            { s, r ->
                // +"2e308" = Infinity
                s.value == "2e308" && (r eq 13)
            },
            { s, r ->
                // +"5e-324" = 5e-324
                s.value == "5e-324" && (r eq 14)
            },
            { s, r ->
                // +"1e-324" = 0
                s.value == "1e-324" && (r eq 15)
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
        val method = getMethod(className, "testUnaryPlusObject")
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
