package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN

class Neg : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Neg.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test negateNumber`() {
        val method = getMethod("negateNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // -NaN = NaN
                a.isNaN() && r.isNaN()
            },
            { a, r ->
                // -0 = -0
                (a eq 0) && (r eq -0.0)
            },
            { a, r ->
                // -positive number = negative number
                (a.number > 0) && (r.number < 0)
            },
            { a, r ->
                // -negative number = positive number
                (a.number < 0) && (r.number > 0)
            },
            invariants = arrayOf(
                { a, r ->
                    if (a.isNaN()) {
                        r.isNaN()
                    } else {
                        r.number eq -a.number
                    }
                },
            )
        )
    }

    @Test
    fun `test negateBoolean`() {
        val method = getMethod("negateBoolean")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // -true = -1
                a.value && (r eq -1)
            },
            { a, r ->
                // -false = -0
                !a.value && (r eq 0)
            },
        )
    }

    @Test
    fun `test negateUndefined`() {
        val method = getMethod("negateUndefined")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // -undefined = NaN
                r.isNaN()
            },
        )
    }

    @Test
    fun `test negateNull`() {
        val method = getMethod("negateNull")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r ->
                // -null = -0
                r eq 0
            },
        )
    }

    @Disabled("Input strings are not supported yet")
    @Test
    fun `test negateString`() {
        val method = getMethod("negateString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                // -"" = -0
                a.value == "" && (r eq 0.0)
            },
            { a, r ->
                // -"0" = -0
                a.value == "0" && (r eq 0.0)
            },
            { a, r ->
                // -"1" = -1
                a.value == "1" && (r eq -1.0)
            },
            { a, r ->
                // -"-42" = 42
                a.value == "-42" && (r eq 42.0)
            },
            { a, r ->
                // -"NaN" = NaN
                a.value == "NaN" && r.isNaN()
            },
            { a, r ->
                // -"Infinity" = -Infinity
                a.value == "Infinity" && (r eq Double.NEGATIVE_INFINITY)
            },
            { a, r ->
                // -"-Infinity" = Infinity
                a.value == "-Infinity" && (r eq Double.POSITIVE_INFINITY)
            },
            { a, r ->
                // -"hello" = NaN
                a.value == "hello" && r.isNaN()
            },
            { a, r ->
                // -"true" = NaN
                a.value == "true" && r.isNaN()
            },
            { a, r ->
                // -"false" = NaN
                a.value == "false" && r.isNaN()
            },
            { a, r ->
                // -"undefined" = NaN
                a.value == "undefined" && r.isNaN()
            },
            { a, r ->
                // -"null" = NaN
                a.value == "null" && r.isNaN()
            },
            { a, r ->
                // -"(42)" = NaN
                a.value == "(42)" && r.isNaN()
            },
            { a, r ->
                // -"{}" = NaN
                a.value == "{}" && r.isNaN()
            },
            { a, r ->
                // -"{foo: 42}" = NaN
                a.value == "{foo: 42}" && r.isNaN()
            },
            { a, r ->
                // -"[]" = NaN
                a.value == "[]" && r.isNaN()
            },
            { a, r ->
                // -"[42]" = 42
                a.value == "[42]" && (r eq 42.0)
            },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }
}
