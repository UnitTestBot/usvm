package org.usvm.test

import dk.brics.automaton.Automaton
import dk.brics.automaton.RegExp
import dk.brics.automaton.SpecialOperations
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

// Tests for new features in dk.brics.automaton

class AutomataTests {
    @ParameterizedTest
    @MethodSource("trimTestCases")
    fun testTrim(regex: String, begin: Int, end: Int, acceptStrings: List<String>, rejectStrings: List<String>) {
        val r = RegExp(regex)
        val a = r.toAutomaton()
        val trimmed = SpecialOperations.trimCount(a, begin, end);
        for (acceptString in acceptStrings)
            assert(trimmed.run(acceptString)) { "\"$acceptString\" is not accepted, but should be" }
        for (rejectString in rejectStrings)
            assert(!trimmed.run(rejectString)) { "\"$rejectString\" is accepted, but should not" }
    }

    @Test
    fun testTrimTotal() {
        val a = Automaton.makeAnyString()
        assert(a.isTotal)
        val trimmed = SpecialOperations.trimCount(a, 1, 1);
        assert(trimmed.isTotal)
    }

    companion object {
        @JvmStatic
        fun trimTestCases() = listOf(
            Arguments.of("abcdef", 1, 1, listOf("bcde"), listOf("abcde", "bcdef")),
            Arguments.of("(ab)+c", 3, 1, listOf("b", "bababab"), listOf("baba", "abab", "abc")),
            Arguments.of("(ab)+c", 2, 2, listOf("", "ababa"), listOf("b", "c"))
        )
    }
}