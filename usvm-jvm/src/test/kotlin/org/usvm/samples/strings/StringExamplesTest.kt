package org.usvm.samples.strings

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


import java.util.Locale

internal class StringExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testByteToString() {
        checkExecutionMatches(
            StringExamples::byteToString,
            { _, a, b, r -> a > b && r == a.toString() },
            { _, a, b, r -> a <= b && r == b.toString() },
        )
    }

    @Test
    fun testByteToStringWithConstants() {
        val values: Array<Byte> = arrayOf(
            Byte.MIN_VALUE,
            (Byte.MIN_VALUE + 100).toByte(),
            0.toByte(),
            (Byte.MAX_VALUE - 100).toByte(),
            Byte.MAX_VALUE
        )

        val expected = values.map { it.toString() }

        checkExecutionMatches(
            StringExamples::byteToStringWithConstants,
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testReplace() {
        checkExecutionMatches(
            StringExamples::replace,
            { _, fst, _, _ -> fst == null },
            { _, fst, snd, _ -> fst != null && snd == null },
            { _, fst, snd, r -> fst != null && snd != null && r != null && (!r.contains("abc") || snd == "abc") },
        )
    }

    @Test
    fun testShortToString() {
        checkExecutionMatches(
            StringExamples::shortToString,
            { _, a, b, r -> a > b && r == a.toString() },
            { _, a, b, r -> a <= b && r == b.toString() },
        )
    }

    @Test
    fun testShortToStringWithConstants() {
        val values: Array<Short> = arrayOf(
            Short.MIN_VALUE,
            (Short.MIN_VALUE + 100).toShort(),
            0.toShort(),
            (Short.MAX_VALUE - 100).toShort(),
            Short.MAX_VALUE
        )

        val expected = values.map { it.toString() }

        checkExecutionMatches(
            StringExamples::shortToStringWithConstants,
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testIntToString() {
        checkExecutionMatches(
            StringExamples::intToString,
            { _, a, b, r -> a > b && r == a.toString() },
            { _, a, b, r -> a <= b && r == b.toString() },
        )
    }

    @Test
    fun testIntToStringWithConstants() {
        val values: Array<Int> = arrayOf(
            Integer.MIN_VALUE,
            Integer.MIN_VALUE + 100,
            0,
            Integer.MAX_VALUE - 100,
            Integer.MAX_VALUE
        )

        val expected = values.map { it.toString() }

        checkExecutionMatches(
            StringExamples::intToStringWithConstants,
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testLongToString() {
        checkExecutionMatches(
            StringExamples::longToString,
            { _, a, b, r -> a > b && r == a.toString() },
            { _, a, b, r -> a <= b && r == b.toString() },
        )
    }

    @Test
    fun testLongToStringWithConstants() {
        val values: Array<Long> = arrayOf(
            Long.MIN_VALUE,
            Long.MIN_VALUE + 100L,
            0L,
            Long.MAX_VALUE - 100L,
            Long.MAX_VALUE
        )

        val expected = values.map { it.toString() }

        checkExecutionMatches(
            StringExamples::longToStringWithConstants,
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testStartsWithLiteral() {
        checkExecutionMatches(
            StringExamples::startsWithLiteral,
            { _, v, _ -> v == null },
            { _, v, r -> v != null && v.startsWith("1234567890") && r!!.startsWith("12a4567890") },
            { _, v, r -> v != null && v[0] == 'x' && r!![0] == 'x' },
            { _, v, r -> v != null && v.lowercase(Locale.getDefault()) == r }
        )
    }

    @Test
    fun testBooleanToString() {
        checkExecutionMatches(
            StringExamples::booleanToString,
            { _, a, b, r -> a == b && r == "false" },
            { _, a, b, r -> a != b && r == "true" },
        )
    }


    @Test
    fun testCharToString() {
        checkExecutionMatches(
            StringExamples::charToString,
            { _, a, b, r -> a > b && r == a.toString() },
            { _, a, b, r -> a <= b && r == b.toString() },
        )
    }


    @Test
    fun testStringToByte() {
        checkExecutionMatches(
            StringExamples::stringToByte,
        )
    }

    @Test
    fun testStringToShort() {
        checkExecutionMatches(
            StringExamples::stringToShort,
        )
    }

    @Test
    fun testStringToInt() {
        checkExecutionMatches(
            StringExamples::stringToInt,
        )
    }

    @Test
    fun testStringToLong() {
        checkExecutionMatches(
            StringExamples::stringToLong,
        )
    }

    @Test
    fun testStringToBoolean() {
        checkExecutionMatches(
            StringExamples::stringToBoolean,
            { _, s, r -> (s == null || r == java.lang.Boolean.valueOf(s)) && r == false }, // minimization
            { _, s, r -> s != null && r == true && r == java.lang.Boolean.valueOf(s) },
        )
    }

    @Test
    fun testConcat() {
        checkExecutionMatches(
            StringExamples::concat,
            { _, fst, snd, r -> (fst == null || snd == null) && r == fst + snd },
            { _, fst, snd, r -> r == fst + snd },
        )
    }

    @Test
    @Disabled("Sometimes it freezes the execution for several hours JIRA:1453")
    fun testConcatWithObject() {
        checkExecutionMatches(
            StringExamples::concatWithObject,
            { _, pair, r -> pair == null && r == "fst.toString() = $pair" },
            { _, pair, r -> pair != null && r == "fst.toString() = $pair" }
        )
    }

    @Test
    fun testStringConstants() {
        checkExecutionMatches(
            StringExamples::stringConstants,
            { _, s, r -> r == "String('$s')" },
        )
    }

    @Test
    fun testContainsOnLiterals() {
        checkExecutionMatches(
            StringExamples::containsOnLiterals,
        )
    }

    @Test
    fun testConcatWithInt() {
        checkExecutionMatches(
            StringExamples::concatWithInts,
            { _, a, b, r -> a == b && r == null }, // IllegalArgumentException
            { _, a, b, r -> a > b && r == "a > b, a:$a, b:$b" },
            { _, a, b, r -> a < b && r == "a < b, a:$a, b:$b" },
        )
    }

    @Test
    fun testUseStringBuffer() {
        checkExecutionMatches(
            StringExamples::useStringBuffer,
            { _, fst, snd, r -> r == "$fst, $snd" },
        )
    }

    @Test
    fun testStringBuilderAsParameterExample() {
        checkExecutionMatches(
            StringExamples::stringBuilderAsParameterExample,
        )
    }

    @Test
    fun testNullableStringBuffer() {
        checkWithExceptionExecutionMatches(
            StringExamples::nullableStringBuffer,
            { _, _, i, r -> i >= 0 && r.isException<NullPointerException>() },
            { _, _, i, r -> i < 0 && r.isException<NullPointerException>() },
            { _, buffer, i, r -> i >= 0 && r.getOrNull() == "${buffer}Positive" },
            { _, buffer, i, r -> i < 0 && r.getOrNull() == "${buffer}Negative" },
        )
    }

    @Test
    fun testIsStringBuilderEmpty() {
        checkExecutionMatches(
            StringExamples::isStringBuilderEmpty,
            { _, stringBuilder, result -> result == stringBuilder.isEmpty() }
        )
    }

    @Test
    @Disabled("Flaky on GitHub: https://github.com/UnitTestBot/UTBotJava/issues/1004")
    fun testIsValidUuid() {
        val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        checkExecutionMatches(
            StringExamples::isValidUuid,
            { _, uuid, r -> uuid == null || uuid.isEmpty() && r == false },
            { _, uuid, r -> uuid.isNotEmpty() && uuid.isBlank() && r == false },
            { _, uuid, r -> uuid.isNotEmpty() && uuid.isNotBlank() && r == false },
            { _, uuid, r -> uuid.length > 1 && uuid.isNotBlank() && !uuid.matches(pattern) && r == false },
            { _, uuid, r -> uuid.length > 1 && uuid.isNotBlank() && uuid.matches(pattern) && r == true },
        )
    }

    @Test
    fun testIsValidUuidShortVersion() {
        val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        checkExecutionMatches(
            StringExamples::isValidUuidShortVersion,
            { _, uuid, r -> uuid == null && r == false },
            { _, uuid, r -> uuid.matches(pattern) && r == true },
            { _, uuid, r -> !uuid.matches(pattern) && r == false },
        )
    }

    @Test
    fun testSplitExample() {
        checkExecutionMatches(
            StringExamples::splitExample,
            { _, s, r -> s.all { it.isWhitespace() } && r == 0 },
            { _, s, r -> s.none { it.isWhitespace() } && r == 1 },
            { _, s, r -> s[0].isWhitespace() && s.any { !it.isWhitespace() } && r == 2 },
            { _, s, r -> !s[0].isWhitespace() && s[2].isWhitespace() && r == 1 },
            { _, s, r -> !s[0].isWhitespace() && s[1].isWhitespace() && !s[2].isWhitespace() && r == 2 }
        )
    }

    @Test
    fun testIsBlank() {
        checkExecutionMatches(
            StringExamples::isBlank,
            { _, cs, r -> cs == null && r == true },
            { _, cs, r -> cs.isEmpty() && r == true },
            { _, cs, r -> cs.isNotEmpty() && cs.isBlank() && r == true },
            { _, cs, r -> cs.isNotEmpty() && cs.isNotBlank() && r == false }
        )
    }

    @Test
    fun testLength() {
        checkExecutionMatches(
            StringExamples::length, // TODO: that strange, why we haven't 3rd option?
            { _, cs, r -> cs == null && r == 0 },
            { _, cs, r -> cs != null && r == cs.length },
        )
    }

    @Test
    fun testLonger() {
        checkWithExceptionExecutionMatches(
            StringExamples::longer,
            { _, _, i, r -> i <= 0 && r.isException<IllegalArgumentException>() },
            { _, cs, i, r -> i > 0 && cs == null && !r.getOrThrow() },
            { _, cs, i, r -> i > 0 && cs != null && cs.length > i && r.getOrThrow() }, // TODO: Coverage calculation fails in the instrumented process with Illegal Argument Exception
        )
    }

    @Test
    fun testEqualChar() {
        checkWithExceptionExecutionMatches(
            StringExamples::equalChar,
            { _, cs, r -> cs == null && r.isException<NullPointerException>() },
            { _, cs, r -> cs.isEmpty() && r.isException<StringIndexOutOfBoundsException>() },
            { _, cs, r -> cs.isNotEmpty() && cs[0] == 'a' && r.getOrThrow() },
            { _, cs, r -> cs.isNotEmpty() && cs[0] != 'a' && !r.getOrThrow() },
        )
    }

    @Test
    fun testSubstring() {
        checkWithExceptionExecutionMatches(
            StringExamples::substring,
            { _, s, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, i, r -> s != null && i < 0 || i > s.length && r.isException<StringIndexOutOfBoundsException>() },
            { _, s, i, r -> s != null && i in 0..s.length && r.getOrThrow() == s.substring(i) && s.substring(i) != "password" },
            { _, s, i, r -> s != null && i == 0 && r.getOrThrow() == s.substring(i) && s.substring(i) == "password" },
            { _, s, i, r -> s != null && i != 0 && r.getOrThrow() == s.substring(i) && s.substring(i) == "password" },
        )
    }

    @Test
    fun testSubstringWithEndIndex() {
        checkWithExceptionExecutionMatches(
            StringExamples::substringWithEndIndex,
            { _, s, _, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, b, e, r -> s != null && b < 0 || e > s.length || b > e && r.isException<StringIndexOutOfBoundsException>() },
            { _, s, b, e, r -> r.getOrThrow() == s.substring(b, e) && s.substring(b, e) != "password" },
            { _, s, b, e, r -> b == 0 && r.getOrThrow() == s.substring(b, e) && s.substring(b, e) == "password" },
            { _, s, b, e, r ->
                b != 0 && e == s.length && r.getOrThrow() == s.substring(b, e) && s.substring(b, e) == "password"
            },
            { _, s, b, e, r ->
                b != 0 && e != s.length && r.getOrThrow() == s.substring(b, e) && s.substring(b, e) == "password"
            },
        )
    }

    @Test
    fun testSubstringWithEndIndexNotEqual() {
        checkWithExceptionExecutionMatches(
            StringExamples::substringWithEndIndexNotEqual,
            { _, s, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, e, r -> s != null && e < 1 || e > s.length && r.isException<StringIndexOutOfBoundsException>() },
            { _, s, e, r -> s != null && r.getOrThrow() == s.substring(1, e) },
        )
    }

    @Test
    fun testFullSubstringEquality() {
        checkWithExceptionExecutionMatches(
            StringExamples::fullSubstringEquality,
            { _, s, r -> s == null && r.isException<NullPointerException>() },
            { _, s, r -> s != null && r.getOrThrow() },
        )
    }

    @Test
    @Disabled("TODO: add intern support")
    fun testUseIntern() {
        checkWithExceptionExecutionMatches(
            StringExamples::useIntern,
            { _, s, r -> s == null && r.isException<NullPointerException>() },
            { _, s, r -> s != null && s != "abc" && r.getOrThrow() == 1 },
            { _, s, r -> s != null && s == "abc" && r.getOrThrow() == 3 },
        )
    }

    @Test
    fun testPrefixAndSuffix() {
        checkExecutionMatches(
            StringExamples::prefixAndSuffix,
            { _, s, _ -> s == null }, // NullPointerException
            { _, s, r -> s.length != 5 && r == 0 },
            { _, s, r -> s.length == 5 && !s.startsWith("ab") && r == 1 },
            { _, s, r -> s.length == 5 && s.startsWith("ab") && !s.endsWith("de") && r == 2 },
            { _, s, r -> s.length == 5 && s.startsWith("ab") && s.endsWith("de") && !s.contains("+") && r == 4 },
            { _, s, r -> s.length == 5 && s == "ab+de" && r == 3 },
        )
    }

    @Test
    fun testPrefixWithTwoArgs() {
        checkWithExceptionExecutionMatches(
            StringExamples::prefixWithTwoArgs,
            { _, s, r -> s == null && r.isException<NullPointerException>() },
            { _, s, r -> s != null && s.startsWith("abc", 1) && r.getOrThrow() == 1 },
            { _, s, r -> s != null && !s.startsWith("abc", 1) && r.getOrThrow() == 2 },
        )
    }

    @Test
    fun testPrefixWithOffset() {
        checkExecutionMatches(
            StringExamples::prefixWithOffset, // should be 4, but path selector eliminates several results with false
            { _, o, r -> o < 0 && r == 2 },
            { _, o, r -> o > "babc".length - "abc".length && r == 2 },
            { _, o, r -> o in 0..1 && !"babc".startsWith("abc", o) && r == 2 },
            { _, o, r -> "babc".startsWith("abc", o) && r == 1 },
        )
    }

    @Test
    fun testStartsWith() {
        checkExecutionMatches(
            StringExamples::startsWith,
            { _, _, prefix, _ -> prefix == null },
            { _, _, prefix, _ -> prefix != null && prefix.length < 2 },
            { _, s, prefix, _ -> prefix != null && prefix.length >= 2 && s == null },
            { _, s, prefix, r -> prefix != null && prefix.length >= 2 && s != null && s.startsWith(prefix) && r == true },
            { _, s, prefix, r -> prefix != null && prefix.length >= 2 && s != null && !s.startsWith(prefix) && r == false }

        )
    }

    @Test
    fun testStartsWithOffset() {
        checkExecutionMatches(
            StringExamples::startsWithOffset,
            { _, _, prefix, _, _ -> prefix == null },
            { _, _, prefix, _, _ -> prefix != null && prefix.length < 2 },
            { _, s, prefix, _, _ -> prefix != null && prefix.length >= 2 && s == null },
            { _, s, prefix, o, r ->
                prefix != null && prefix.length >= 2 && s != null && s.startsWith(prefix, o) && o > 0 && r == 0
            },
            { _, s, prefix, o, r ->
                prefix != null && prefix.length >= 2 && s != null && s.startsWith(prefix, o) && o == 0 && r == 1
            },
            { _, s, prefix, o, r ->
                prefix != null && prefix.length >= 2 && s != null && !s.startsWith(prefix, o) && r == 2
            }
        )
    }

    @Test
    fun testEndsWith() {
        checkExecutionMatches(
            StringExamples::endsWith,
            { _, _, suffix, _ -> suffix == null },
            { _, _, suffix, _ -> suffix != null && suffix.length < 2 },
            { _, s, suffix, _ -> suffix != null && suffix.length >= 2 && s == null },
            { _, s, suffix, r -> suffix != null && suffix.length >= 2 && s != null && s.endsWith(suffix) && r == true },
            { _, s, suffix, r -> suffix != null && suffix.length >= 2 && s != null && !s.endsWith(suffix) && r == false }
        )
    }

    @Test
    @Disabled("TODO: support replace")
    fun testReplaceAll() {
        checkWithExceptionExecutionMatches(
            StringExamples::replaceAll,
            { _, s, _, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, regex, _, r -> s != null && regex == null && r.isException<NullPointerException>() },
            { _, s, regex, replacement, r -> s != null && regex != null && replacement == null && r.isException<NullPointerException>() },
            { _, s, regex, replacement, r ->
                s != null && regex != null && replacement != null && r.getOrThrow() == s.replace(regex, replacement)
            }, // one replace only!
        )
    }

    @Test
    fun testLastIndexOf() {
        checkExecutionMatches(
            StringExamples::lastIndexOf,
            { _, s, _, _ -> s == null },
            { _, s, find, _ -> s != null && find == null },
            { _, s, find, r -> r == s.lastIndexOf(find) && r == s.length - find.length },
            { _, s, find, r -> r == s.lastIndexOf(find) && r < s.length - find.length },
            { _, s, find, r -> r == s.lastIndexOf(find) && r == -1 },
        )
    }

    @Test
    fun testIndexOfWithOffset() {
        checkExecutionMatches(
            StringExamples::indexOfWithOffset,
            { _, s, _, _, _ -> s == null },
            { _, s, find, _, _ -> s != null && find == null },
            { _, s, find, offset, r -> r == s.indexOf(find, offset) && r > offset && offset > 0 },
            { _, s, find, offset, r -> r == s.indexOf(find, offset) && r == offset },
            { _, s, find, offset, r -> r == s.indexOf(find, offset) && !(r == offset || (offset in 1 until r)) },
        )
    }


    @Test
    fun testLastIndexOfWithOffset() {
        checkExecutionMatches(
            StringExamples::lastIndexOfWithOffset,
            { _, s, _, _, _ -> s == null },
            { _, s, find, _, _ -> s != null && find == null },
            { _, s, find, i, r -> r == s.lastIndexOf(find, i) && r >= 0 && r < i - find.length && i < s.length },
            { _, s, find, i, r -> r == s.lastIndexOf(find, i) && r >= 0 && !(r < i - find.length && i < s.length) },
            { _, s, find, i, r -> r == s.lastIndexOf(find, i) && r == -1 },
        )
    }

    @Test
    fun testCompareCodePoints() {
        checkWithExceptionExecutionMatches(
            StringExamples::compareCodePoints,
            { _, s, _, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, _, i, r -> s != null && i < 0 || i >= s.length && r.isException<StringIndexOutOfBoundsException>() },
            { _, s, t, _, r -> s != null && t == null && r.isException<NullPointerException>() },
            { _, _, t, i, r -> t != null && i < 0 || i >= t.length && r.isException<StringIndexOutOfBoundsException>() },
            { _, s, t, i, r -> s != null && t != null && s.codePointAt(i) < t.codePointAt(i) && i == 0 && r.getOrThrow() == 0 },
            { _, s, t, i, r -> s != null && t != null && s.codePointAt(i) < t.codePointAt(i) && i != 0 && r.getOrThrow() == 1 },
            { _, s, t, i, r -> s != null && t != null && s.codePointAt(i) >= t.codePointAt(i) && i == 0 && r.getOrThrow() == 2 },
            { _, s, t, i, r -> s != null && t != null && s.codePointAt(i) >= t.codePointAt(i) && i != 0 && r.getOrThrow() == 3 },
        )
    }

    @Test
    fun testToCharArray() {
        checkExecutionMatches(
            StringExamples::toCharArray,
            { _, s, _ -> s == null },
            { _, s, r -> s.toCharArray().contentEquals(r) }
        )
    }

    @Test
    fun testGetObj() {
        checkExecutionMatches(
            StringExamples::getObj,
            { _, obj, r -> obj == r }
        )
    }

    @Test
    fun testGetObjWithCondition() {
        checkExecutionMatches(
            StringExamples::getObjWithCondition,
            { _, obj, r -> obj == null && r == "null" },
            { _, obj, r -> obj != null && obj == "BEDA" && r == "48858" },
            { _, obj, r -> obj != null && obj != "BEDA" && obj == r }
        )
    }

    @Test
    fun testEqualsIgnoreCase() {
        checkExecutionMatches(
            StringExamples::equalsIgnoreCase,
            { _, s, r -> "SUCCESS".equals(s, ignoreCase = true) && r == "success" },
            { _, s, r -> !"SUCCESS".equals(s, ignoreCase = true) && r == "failure" },
        )
    }

    @Test
    fun testListToString() {
        checkExecutionMatches(
            StringExamples::listToString,
            { _, r -> r == "[a, b, c]" },
        )
    }
}
