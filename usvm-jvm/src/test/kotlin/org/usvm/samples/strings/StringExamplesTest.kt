package org.usvm.samples.strings


import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException
import java.util.*

internal class StringExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testByteToString() = disableTest("Expected exactly 2 executions, but 5 found") {
        checkDiscoveredProperties(
            StringExamples::byteToString,
            eq(2),
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

        checkDiscoveredProperties(
            StringExamples::byteToStringWithConstants,
            eq(1),
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testReplace() = disableTest("Expected number of executions in bounds 3..4, but 102 found") {
        checkDiscoveredProperties(
            StringExamples::replace,
            between(3..4),
            { _, fst, _, _ -> fst == null },
            { _, fst, snd, _ -> fst != null && snd == null },
            { _, fst, snd, r -> fst != null && snd != null && r != null && (!r.contains("abc") || snd == "abc") },
        )
    }

    @Test
    fun testShortToString() {
        checkDiscoveredProperties(
            StringExamples::shortToString,
            ignoreNumberOfAnalysisResults,
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

        checkDiscoveredProperties(
            StringExamples::shortToStringWithConstants,
            eq(1),
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testIntToString() = disableTest("slow on CI") {
        checkDiscoveredProperties(
            StringExamples::intToString,
            ignoreNumberOfAnalysisResults,
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

        checkDiscoveredProperties(
            StringExamples::intToStringWithConstants,
            eq(1),
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testLongToString() {
        checkDiscoveredProperties(
            StringExamples::longToString,
            ignoreNumberOfAnalysisResults,
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

        checkDiscoveredProperties(
            StringExamples::longToStringWithConstants,
            eq(1),
            { _, r -> r != null && r.indices.all { r[it] == expected[it] } }
        )
    }

    @Test
    fun testStartsWithLiteral() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredProperties(
            StringExamples::startsWithLiteral,
            ge(4),
            { _, v, _ -> v == null },
            { _, v, r -> v != null && v.startsWith("1234567890") && r != null && r.startsWith("12a4567890") },
            { _, v, r -> v != null && v[0] == 'x' && r != null && r[0] == 'x' },
            { _, v, r -> v != null && v.lowercase(Locale.getDefault()) == r }
        )
    }

    @Test
    fun testBooleanToString() {
        checkDiscoveredProperties(
            StringExamples::booleanToString,
            eq(2),
            { _, a, b, r -> a == b && r == "false" },
            { _, a, b, r -> a != b && r == "true" },
        )
    }


    @Test
    fun testCharToString() = disableTest("Expected exactly 2 executions, but 4 found") {
        checkDiscoveredProperties(
            StringExamples::charToString,
            eq(2),
            { _, a, b, r -> a > b && r == a.toString() },
            { _, a, b, r -> a <= b && r == b.toString() },
        )
    }


    @Test
    fun testStringToByte() = disableTest("Expected exactly -1 executions, but 16 found") {
        checkDiscoveredProperties(
            StringExamples::stringToByte,
            eq(-1),
        )
    }

    @Test
    fun testStringToShort() = disableTest("Expected exactly -1 executions, but 16 found") {
        checkDiscoveredProperties(
            StringExamples::stringToShort,
            eq(-1),
        )
    }

    @Test
    fun testStringToInt() = disableTest("Expected exactly -1 executions, but 16 found") {
        checkDiscoveredProperties(
            StringExamples::stringToInt,
            eq(-1),
        )
    }

    @Test
    fun testStringToLong() = disableTest("Expected exactly -1 executions, but 16 found") {
        checkDiscoveredProperties(
            StringExamples::stringToLong,
            eq(-1),
        )
    }

    @Test
    fun testStringToBoolean() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredProperties(
            StringExamples::stringToBoolean,
            ge(2),
            { _, s, r -> (s == null || r == java.lang.Boolean.valueOf(s)) && r == false }, // minimization
            { _, s, r -> s != null && r == true && r == java.lang.Boolean.valueOf(s) },
        )
    }

    @Test
    fun testConcat() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            StringExamples::concat,
            between(1..2),
            { _, fst, snd, r -> (fst == null || snd == null) && r == fst + snd },
            { _, fst, snd, r -> r == fst + snd },
        )
    }

    @Test
    fun testConcatWithObject() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            StringExamples::concatWithObject,
            between(2..3),
            { _, pair, r -> pair == null && r == "fst.toString() = $pair" },
            { _, pair, r -> pair != null && r == "fst.toString() = $pair" }
        )
    }

    @Test
    fun testStringConstants() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            StringExamples::stringConstants,
            between(1..2),
            { _, s, r -> r == "String('$s')" },
        )
    }

    @Test
    fun testContainsOnLiterals() {
        checkDiscoveredProperties(
            StringExamples::containsOnLiterals,
            eq(1),
        )
    }

    @Test
    fun testConcatWithInt() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            StringExamples::concatWithInts,
            eq(3),
            { _, a, b, r -> a == b && r == null }, // IllegalArgumentException
            { _, a, b, r -> a > b && r == "a > b, a:$a, b:$b" },
            { _, a, b, r -> a < b && r == "a < b, a:$a, b:$b" },
        )
    }

    @Test
    fun testUseStringBuffer() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            StringExamples::useStringBuffer,
            between(1..2),
            { _, fst, snd, r -> r == "$fst, $snd" },
        )
    }

    @Test
    fun testStringBuilderAsParameterExample() = disableTest("Expected exactly 1 executions, but 2 found") {
        checkDiscoveredProperties(
            StringExamples::stringBuilderAsParameterExample,
            eq(1),
        )
    }

    @Test
    fun testNullableStringBuffer() = disableTest("Some properties were not discovered at positions (from 0): [2, 3]") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::nullableStringBuffer,
            eq(4),
            { _, _, i, r -> i >= 0 && r.isException<NullPointerException>() },
            { _, _, i, r -> i < 0 && r.isException<NullPointerException>() },
            { _, buffer, i, r -> i >= 0 && r.getOrNull() == "${buffer}Positive" },
            { _, buffer, i, r -> i < 0 && r.getOrNull() == "${buffer}Negative" },
        )
    }

    @Test
    fun testIsStringBuilderEmpty() = disableTest("Expected exactly 2 executions, but 24 found") {
        checkDiscoveredProperties(
            StringExamples::isStringBuilderEmpty,
            eq(2),
            { _, stringBuilder, result -> result == stringBuilder.isEmpty() }
        )
    }

    @Test
    fun testIsValidUuid() = disableTest("Some properties were not discovered at positions (from 0): [1, 2, 3, 4]") {
        val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        checkDiscoveredProperties(
            StringExamples::isValidUuid,
            ignoreNumberOfAnalysisResults,
            { _, uuid, r -> uuid == null || uuid.isEmpty() && r == false },
            { _, uuid, r -> uuid.isNotEmpty() && uuid.isBlank() && r == false },
            { _, uuid, r -> uuid.isNotEmpty() && uuid.isNotBlank() && r == false },
            { _, uuid, r -> uuid.length > 1 && uuid.isNotBlank() && !uuid.matches(pattern) && r == false },
            { _, uuid, r -> uuid.length > 1 && uuid.isNotBlank() && uuid.matches(pattern) && r == true },
        )
    }

    @Test
    fun testIsValidUuidShortVersion() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        checkDiscoveredProperties(
            StringExamples::isValidUuidShortVersion,
            eq(3),
            { _, uuid, r -> uuid == null && r == false },
            { _, uuid, r -> uuid.matches(pattern) && r == true },
            { _, uuid, r -> !uuid.matches(pattern) && r == false },
        )
    }

    @Test
    fun testSplitExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2, 3, 4]") {
        checkDiscoveredProperties(
            StringExamples::splitExample,
            ignoreNumberOfAnalysisResults,
            { _, s, r -> s.all { it.isWhitespace() } && r == 0 },
            { _, s, r -> s.none { it.isWhitespace() } && r == 1 },
            { _, s, r -> s[0].isWhitespace() && s.any { !it.isWhitespace() } && r == 2 },
            { _, s, r -> !s[0].isWhitespace() && s[2].isWhitespace() && r == 1 },
            { _, s, r -> !s[0].isWhitespace() && s[1].isWhitespace() && !s[2].isWhitespace() && r == 2 }
        )
    }

    @Test
    fun testIsBlank() = disableTest("Some properties were not discovered at positions (from 0): [1, 2, 3]") {
        checkDiscoveredProperties(
            StringExamples::isBlank,
            ge(4),
            { _, cs, r -> cs == null && r == true },
            { _, cs, r -> cs.isEmpty() && r == true },
            { _, cs, r -> cs.isNotEmpty() && cs.isBlank() && r == true },
            { _, cs, r -> cs.isNotEmpty() && cs.isNotBlank() && r == false }
        )
    }

    @Test
    fun testLength() = disableTest("Expected exactly 2 executions, but 6 found") {
        checkDiscoveredProperties(
            StringExamples::length, // TODO: that strange, why we haven't 3rd option?
            eq(2),
            { _, cs, r -> cs == null && r == 0 },
            { _, cs, r -> cs != null && r == cs.length },
        )
    }

    @Test
    fun testLonger() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::longer,
            ignoreNumberOfAnalysisResults,
            { _, _, i, r -> i <= 0 && r.isException<IllegalArgumentException>() },
            { _, cs, i, r -> i > 0 && cs == null && !r.getOrThrow() },
            { _, cs, i, r -> i > 0 && cs != null && cs.length > i && r.getOrThrow() }, // TODO: Coverage calculation fails in the instrumented process with Illegal Argument Exception
        )
    }

    @Test
    fun testEqualChar() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::equalChar,
            eq(4),
            { _, cs, r -> cs == null && r.isException<NullPointerException>() },
            { _, cs, r -> cs.isEmpty() && r.isException<StringIndexOutOfBoundsException>() },
            { _, cs, r -> cs.isNotEmpty() && cs[0] == 'a' && r.getOrThrow() },
            { _, cs, r -> cs.isNotEmpty() && cs[0] != 'a' && !r.getOrThrow() },
        )
    }

    @Test
    fun testSubstring() = disableTest("Some properties were not discovered at positions (from 0): [4]") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::substring,
            between(5..8),
            { _, s, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, i, r -> s != null && i < 0 || i > s.length && r.isException<StringIndexOutOfBoundsException>() },
            { _, s, i, r -> s != null && i in 0..s.length && r.getOrThrow() == s.substring(i) && s.substring(i) != "password" },
            { _, s, i, r -> s != null && i == 0 && r.getOrThrow() == s.substring(i) && s.substring(i) == "password" },
            { _, s, i, r -> s != null && i != 0 && r.getOrThrow() == s.substring(i) && s.substring(i) == "password" },
        )
    }

    @Test
    fun testSubstringWithEndIndex() = disableTest("Some properties were not discovered at positions (from 0): [4, 5]") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::substringWithEndIndex,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::substringWithEndIndexNotEqual,
            ignoreNumberOfAnalysisResults,
            { _, s, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, e, r -> s != null && e < 1 || e > s.length && r.isException<StringIndexOutOfBoundsException>() },
            { _, s, e, r -> s != null && r.getOrThrow() == s.substring(1, e) },
        )
    }

    @Test
    fun testFullSubstringEquality() = disableTest("Expected exactly 2 executions, but 3 found") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::fullSubstringEquality,
            eq(2),
            { _, s, r -> s == null && r.isException<NullPointerException>() },
            { _, s, r -> s != null && r.getOrThrow() },
        )
    }

    @Test
    fun testUseIntern() = disableTest("Expected exactly 3 executions, but 8 found") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::useIntern,
            eq(3),
            { _, s, r -> s == null && r.isException<NullPointerException>() },
            { _, s, r -> s != null && s != "abc" && r.getOrThrow() == 1 },
            { _, s, r -> s != null && s == "abc" && r.getOrThrow() == 3 },
        )
    }

    @Test
    fun testPrefixAndSuffix() = disableTest("Some properties were not discovered at positions (from 0): [3, 4, 5]") {
        checkDiscoveredProperties(
            StringExamples::prefixAndSuffix,
            eq(6),
            { _, s, _ -> s == null }, // NullPointerException
            { _, s, r -> s.length != 5 && r == 0 },
            { _, s, r -> s.length == 5 && !s.startsWith("ab") && r == 1 },
            { _, s, r -> s.length == 5 && s.startsWith("ab") && !s.endsWith("de") && r == 2 },
            { _, s, r -> s.length == 5 && s.startsWith("ab") && s.endsWith("de") && !s.contains("+") && r == 4 },
            { _, s, r -> s.length == 5 && s == "ab+de" && r == 3 },
        )
    }

    @Test
    fun testPrefixWithTwoArgs() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::prefixWithTwoArgs,
            between(3..4),
            { _, s, r -> s == null && r.isException<NullPointerException>() },
            { _, s, r -> s != null && s.startsWith("abc", 1) && r.getOrThrow() == 1 },
            { _, s, r -> s != null && !s.startsWith("abc", 1) && r.getOrThrow() == 2 },
        )
    }

    @Test
    fun testPrefixWithOffset() = disableTest("Some properties were not discovered at positions (from 0): [3]") {
        checkDiscoveredProperties(
            StringExamples::prefixWithOffset,
            eq(4), // should be 4, but path selector eliminates several results with false
            { _, o, r -> o < 0 && r == 2 },
            { _, o, r -> o > "babc".length - "abc".length && r == 2 },
            { _, o, r -> o in 0..1 && !"babc".startsWith("abc", o) && r == 2 },
            { _, o, r -> "babc".startsWith("abc", o) && r == 1 },
        )
    }

    @Test
    fun testStartsWith() = disableTest("Some properties were not discovered at positions (from 0): [3]") {
        checkDiscoveredProperties(
            StringExamples::startsWith,
            between(5..6),
            { _, _, prefix, _ -> prefix == null },
            { _, _, prefix, _ -> prefix != null && prefix.length < 2 },
            { _, s, prefix, _ -> prefix != null && prefix.length >= 2 && s == null },
            { _, s, prefix, r -> prefix != null && prefix.length >= 2 && s != null && s.startsWith(prefix) && r == true },
            { _, s, prefix, r -> prefix != null && prefix.length >= 2 && s != null && !s.startsWith(prefix) && r == false }

        )
    }

    @Test
    fun testStartsWithOffset() = disableTest("Some properties were not discovered at positions (from 0): [3, 4]") {
        checkDiscoveredProperties(
            StringExamples::startsWithOffset,
            between(6..10),
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
    fun testEndsWith() = disableTest("Some properties were not discovered at positions (from 0): [3]") {
        checkDiscoveredProperties(
            StringExamples::endsWith,
            between(5..6),
            { _, _, suffix, _ -> suffix == null },
            { _, _, suffix, _ -> suffix != null && suffix.length < 2 },
            { _, s, suffix, _ -> suffix != null && suffix.length >= 2 && s == null },
            { _, s, suffix, r -> suffix != null && suffix.length >= 2 && s != null && s.endsWith(suffix) && r == true },
            { _, s, suffix, r -> suffix != null && suffix.length >= 2 && s != null && !s.endsWith(suffix) && r == false }
        )
    }

    @Test
    fun testReplaceAll() = disableTest("Index 2 out of bounds for length 2") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::replaceAll,
            eq(4),
            { _, s, _, _, r -> s == null && r.isException<NullPointerException>() },
            { _, s, regex, _, r -> s != null && regex == null && r.isException<NullPointerException>() },
            { _, s, regex, replacement, r -> s != null && regex != null && replacement == null && r.isException<NullPointerException>() },
            { _, s, regex, replacement, r ->
                s != null && regex != null && replacement != null && r.getOrThrow() == s.replace(regex, replacement)
            }, // one replace only!
        )
    }

    @Test
    fun testLastIndexOf() = disableTest("Expected number of executions in bounds 5..7, but 87 found") {
        checkDiscoveredProperties(
            StringExamples::lastIndexOf,
            between(5..7),
            { _, s, _, _ -> s == null },
            { _, s, find, _ -> s != null && find == null },
            { _, s, find, r -> r == s.lastIndexOf(find) && r == s.length - find.length },
            { _, s, find, r -> r == s.lastIndexOf(find) && r < s.length - find.length },
            { _, s, find, r -> r == s.lastIndexOf(find) && r == -1 },
        )
    }

    @Test
    fun testIndexOfWithOffset() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            StringExamples::indexOfWithOffset,
            between(5..9),
            { _, s, _, _, _ -> s == null },
            { _, s, find, _, _ -> s != null && find == null },
            { _, s, find, offset, r -> r == s.indexOf(find, offset) && r > offset && offset > 0 },
            { _, s, find, offset, r -> r == s.indexOf(find, offset) && r == offset },
            { _, s, find, offset, r -> r == s.indexOf(find, offset) && !(r == offset || (offset in 1 until r)) },
        )
    }


    @Test
    fun testLastIndexOfWithOffset() = disableTest("Expected number of executions in bounds 5..9, but 78 found") {
        checkDiscoveredProperties(
            StringExamples::lastIndexOfWithOffset,
            between(5..9),
            { _, s, _, _, _ -> s == null },
            { _, s, find, _, _ -> s != null && find == null },
            { _, s, find, i, r -> r == s.lastIndexOf(find, i) && r >= 0 && r < i - find.length && i < s.length },
            { _, s, find, i, r -> r == s.lastIndexOf(find, i) && r >= 0 && !(r < i - find.length && i < s.length) },
            { _, s, find, i, r -> r == s.lastIndexOf(find, i) && r == -1 },
        )
    }

    @Test
    fun testCompareCodePoints() = disableTest("Expected number of executions in bounds 8..10, but 79 found") {
        checkDiscoveredPropertiesWithExceptions(
            StringExamples::compareCodePoints,
            between(8..10),
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
    fun testToCharArray() = disableTest("Expected exactly 2 executions, but 440 found") {
        checkDiscoveredProperties(
            StringExamples::toCharArray,
            eq(2),
            { _, s, _ -> s == null },
            { _, s, r -> s.toCharArray().contentEquals(r) }
        )
    }

    @Test
    fun testGetObj() {
        checkDiscoveredProperties(
            StringExamples::getObj,
            eq(1),
            { _, obj, r -> obj == r }
        )
    }

    @Test
    fun testGetObjWithCondition() = disableTest("Expected number of executions in bounds 3..4, but 7 found") {
        checkDiscoveredProperties(
            StringExamples::getObjWithCondition,
            between(3..4),
            { _, obj, r -> obj == null && r == "null" },
            { _, obj, r -> obj != null && obj == "BEDA" && r == "48858" },
            { _, obj, r -> obj != null && obj != "BEDA" && obj == r }
        )
    }

    @Test
    fun testEqualsIgnoreCase() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            StringExamples::equalsIgnoreCase,
            ignoreNumberOfAnalysisResults,
            { _, s, r -> "SUCCESS".equals(s, ignoreCase = true) && r == "success" },
            { _, s, r -> !"SUCCESS".equals(s, ignoreCase = true) && r == "failure" },
        )
    }

    @Test
    fun testListToString() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            StringExamples::listToString,
            eq(1),
            { _, r -> r == "[a, b, c]" },
        )
    }
}
