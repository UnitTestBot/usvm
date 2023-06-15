package org.usvm.samples.algorithms

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.algorithms.CorrectBracketSequences.isBracket
import org.usvm.samples.algorithms.CorrectBracketSequences.isOpen
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

internal class CorrectBracketSequencesTest : JavaMethodTestRunner() {
    @Test
    fun testIsOpen() {
        checkExecutionMatches(
            CorrectBracketSequences::isOpen,
            eq(4),
            { c, r -> c == '(' && r },
            { c, r -> c == '{' && r },
            { c, r -> c == '[' && r },
            { c, r -> c !in "({[".toList() && r }
        )
    }

    @Test
    @Disabled("Why only three branches?")
    fun testIsBracket() {
        checkPropertiesMatches(
            CorrectBracketSequences::isBracket,
            eq(5),
            { c, r -> isOpen(c) && r },
            { c, r -> c == ')' && r },
            { c, r -> c == '}' && r },
            { c, r -> c == ']' && r },
            { c, r -> c !in "(){}[]".toList() && !r }
        )
    }

    @Test
    @Disabled("Why do we have five branches?")
    fun testIsTheSameType() {
        checkExecutionMatches(
            CorrectBracketSequences::isTheSameType,
            ignoreNumberOfAnalysisResults,
            { a, b, r -> a == '(' && b == ')' && r },
            { a, b, r -> a == '{' && b == '}' && r },
            { a, b, r -> a == '[' && b == ']' && r },
            { a, b, r -> a == '(' && b != ')' && !r },
            { a, b, r -> a == '{' && b != '}' && !r },
            { a, b, r -> a == '[' && b != ']' && !r },
            { a, b, r -> (a != '(' || b != ')') && (a != '{' || b != '}') && (a != '[' || b != ']') && !r }
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.JcStaticFieldRef@4091b9c3")
    fun testIsCbs() {
        val method = CorrectBracketSequences::isCbs
        checkWithExceptionExecutionMatches(
            method,
            ignoreNumberOfAnalysisResults,
            { chars, r -> chars == null && r.isException<NullPointerException>() },
            { chars, r -> chars != null && chars.isEmpty() && r.getOrNull() == true },
            { chars, r -> chars.any { it == null } && r.isException<NullPointerException>() },
            { chars, r -> !isBracket(chars.first()) && r.getOrNull() == false },
            { chars, r -> !isOpen(chars.first()) && r.getOrNull() == false },
            { chars, _ -> isOpen(chars.first()) },
            { chars, r -> chars.all { isOpen(it) } && r.getOrNull() == false },
            { chars, _ ->
                val charsWithoutFirstOpenBrackets = chars.dropWhile { isOpen(it) }
                val firstNotOpenBracketChar = charsWithoutFirstOpenBrackets.first()

                isBracket(firstNotOpenBracketChar) && !isOpen(firstNotOpenBracketChar)
            },
        )
    }
}