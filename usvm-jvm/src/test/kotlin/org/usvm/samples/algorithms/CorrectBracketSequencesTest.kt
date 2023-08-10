package org.usvm.samples.algorithms

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.algorithms.CorrectBracketSequences.isBracket
import org.usvm.samples.algorithms.CorrectBracketSequences.isOpen
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

internal class CorrectBracketSequencesTest : JavaMethodTestRunner() {
    @Test
    fun testIsOpen() {
        checkDiscoveredProperties(
            CorrectBracketSequences::isOpen,
            ignoreNumberOfAnalysisResults,
            { c, r -> c in "({[".toList() && r != null && r },
            { c, r -> c !in "({[".toList() && r != null && !r }
        )
    }

    @Test
    fun testIsBracket() {
        checkDiscoveredProperties(
            CorrectBracketSequences::isBracket,
            ignoreNumberOfAnalysisResults,
            { c, r -> c in "(){}[]".toList() && r != null && r },
            { c, r -> c !in "(){}[]".toList() && r != null && !r }
        )
    }

    @Test
    fun testIsTheSameType() {
        checkDiscoveredProperties(
            CorrectBracketSequences::isTheSameType,
            ignoreNumberOfAnalysisResults,
            { a, b, r -> (a == '(' && b == ')' || a == '{' && b == '}' || a == '[' && b == ']') && r == true },
            { a, b, r -> (a != '(' || b != ')') && (a != '{' || b != '}') && (a != '[' || b != ']') && r == false }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [3, 4, 5, 6, 7]")
    fun testIsCbs() {
        val method = CorrectBracketSequences::isCbs
        checkDiscoveredPropertiesWithExceptions(
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