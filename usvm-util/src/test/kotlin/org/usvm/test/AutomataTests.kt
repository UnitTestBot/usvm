package org.usvm.test

import dk.brics.automaton.Automaton
import dk.brics.automaton.BasicAutomata
import dk.brics.automaton.RandomStringSampler
import dk.brics.automaton.RegExp
import dk.brics.automaton.SpecialOperations
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

// Tests for new features in dk.brics.automaton

class AutomataTests {
    @ParameterizedTest
    @MethodSource("trimTestCases")
    fun testTrim(regex: String, begin: Int, end: Int, acceptStrings: List<String>, rejectStrings: List<String>) {
        val r = RegExp(regex)
        val a = r.toAutomaton()
        val trimmed = SpecialOperations.trimCount(a, begin, end)
        for (acceptString in acceptStrings)
            assert(trimmed.run(acceptString)) { "\"$acceptString\" is not accepted, but should be" }
        for (rejectString in rejectStrings)
            assert(!trimmed.run(rejectString)) { "\"$rejectString\" is accepted, but should not" }
    }

    @Test
    fun testTrimTotal() {
        val a = Automaton.makeAnyString()
        assert(a.isTotal)
        val trimmed = SpecialOperations.trimCount(a, 1, 1)
        assert(trimmed.isTotal)
    }

    @Test
    fun testAnyStringOfLength() {
        val a = BasicAutomata.makeAnyStringOfLength(4)
        assert(a.run("2fsd"))
        assert(!a.run("sd"))
        assert(!a.run("2fsddf"))
    }

    @ParameterizedTest
    @MethodSource("samplingTestCases")
    fun testAutomatonSampling(
        automaton: Automaton,
        length: Int,
        count: Int,
        expectedSamplesCount: Int,
        mustHaveSamples: Set<String>,
        seed: Long
    ) {
        val random = java.util.Random(seed)
        val sampler = RandomStringSampler(automaton, length, random)
        val samples = HashSet<String>()
        while (sampler.hasNext() && samples.size < count) {
            assert(samples.add(sampler.next()))
        }

        assertEquals(expectedSamplesCount, samples.size)
        for (s in mustHaveSamples) {
            assert(samples.contains(s))
        }
    }

    companion object {
        @JvmStatic
        fun trimTestCases() = listOf(
            Arguments.of("abcdef", 1, 1, listOf("bcde"), listOf("abcde", "bcdef")),
            Arguments.of("(ab)+c", 3, 1, listOf("b", "bababab"), listOf("baba", "abab", "abc")),
            Arguments.of("(ab)+c", 2, 2, listOf("", "ababa"), listOf("b", "c"))
        )

        @JvmStatic
        fun samplingTestCases() = listOf(
            Arguments.of(RegExp("b*").toAutomaton(), 0, 5, 1, setOf(""), 123),
            Arguments.of(RegExp("ab*").toAutomaton(), 3, 5, 1, setOf("abb"), 123),
            Arguments.of(RegExp("a(b|c)").toAutomaton(), 5, 1, 0, setOf<String>(), 123),
            Arguments.of(RegExp("a(b|c)").toAutomaton(), 2, 5, 2, setOf("ab", "ac"), 123),
            Arguments.of(RegExp("a(b|c)*").toAutomaton(), 10, 5, 5, setOf<String>(), 123),
            Arguments.of(BasicAutomata.makeAnyString(), 5, 10, 10, setOf<String>(), 123),
            Arguments.of(RegExp("aaa").toAutomaton(), 4, 10, 0, setOf<String>(), 123),
        )
    }
}