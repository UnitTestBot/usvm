package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

@Disabled("Strings are not supported yet")
class Strings : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Strings.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test concatenate strings`() {
        val method = getMethod("concatenateStrings")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { a, b, r -> r.value == a.value + b.value },
            invariants = arrayOf(
                { a, b, r -> r.value.length == a.value.length + b.value.length }
            )
        )
    }

    @Test
    fun `test string with number`() {
        val method = getMethod("stringWithNumber")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method,
            { s, n, r -> r.value == s.value + n.number.toString() },
        )
    }

    @Test
    fun `test string length`() {
        val method = getMethod("getStringLength")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { s, r -> r.number == s.value.length.toDouble() },
            invariants = arrayOf(
                { _, r -> r.number >= 0 }
            )
        )
    }

    @Test
    @Disabled("String methods not fully implemented")
    fun `test char at`() {
        val method = getMethod("getCharAt")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method,
            { s, index, r ->
                when {
                    index.number < 0 || index.number >= s.value.length -> r.value == ""
                    else -> r.value == s.value[index.number.toInt()].toString()
                }
            }
        )
    }

    @Test
    @Disabled("String methods not fully implemented")
    fun `test substring`() {
        val method = getMethod("getSubstring")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method,
            { s, start, end, r ->
                val startIdx = maxOf(0, minOf(start.number.toInt(), s.value.length))
                val endIdx = maxOf(startIdx, minOf(end.number.toInt(), s.value.length))
                r.value == s.value.substring(startIdx, endIdx)
            }
        )
    }

    @Test
    @Disabled("String methods not fully implemented")
    fun `test index of`() {
        val method = getMethod("findIndexOf")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { s, search, r -> r.number == s.value.indexOf(search.value).toDouble() }
        )
    }

    @Test
    fun `test compare strings`() {
        val method = getMethod("compareStrings")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { a, b, r -> (a.value == b.value) && (r eq 1) },
            { a, b, r -> (a.value < b.value) && (r eq 2) },
            { a, b, r -> (a.value > b.value) && (r eq 3) },
        )
    }

    @Test
    @Disabled("Template literals not implemented")
    fun `test template literal`() {
        val method = getMethod("templateLiteral")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsString>(
            method = method,
            { name, age, r -> r.value == "Hello ${name.value}, you are ${age.number.toInt()} years old" }
        )
    }

    @Test
    @Disabled("String methods not fully implemented")
    fun `test string includes`() {
        val method = getMethod("stringIncludes")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsBoolean>(
            method = method,
            { s, search, r -> r.value == s.value.contains(search.value) }
        )
    }

    @Test
    @Disabled("String methods not fully implemented")
    fun `test string starts with`() {
        val method = getMethod("stringStartsWith")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsBoolean>(
            method = method,
            { s, search, r -> r.value == s.value.startsWith(search.value) }
        )
    }

    @Test
    @Disabled("String methods not fully implemented")
    fun `test string ends with`() {
        val method = getMethod("stringEndsWith")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsBoolean>(
            method = method,
            { s, search, r -> r.value == s.value.endsWith(search.value) }
        )
    }
}
