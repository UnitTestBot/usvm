package org.usvm.language

import dk.brics.automaton.Automaton
import dk.brics.automaton.SpecialOperations

class URegularLanguage(
    private val automaton: Automaton
): UFormalLanguage {
    companion object {
        fun empty(): UFormalLanguage = URegularLanguage(Automaton.makeEmpty())
        fun singleton(s: String): UFormalLanguage = URegularLanguage(Automaton.makeString(s))
        fun anyString(): UFormalLanguage = URegularLanguage(Automaton.makeAnyString())
    }

    override val isEmpty: Boolean =
        automaton.isEmpty
    override val isSigmaStar: Boolean =
        automaton.isTotal

    override fun getStrings(length: Int): Iterable<String> =
        // TODO: make it iterable and constrained!
        automaton.getStrings(length)

    override fun intersect(other: UFormalLanguage): UFormalLanguage = when {
        other.isSigmaStar || this.isEmpty -> this
        this.isSigmaStar || other.isEmpty -> other
        other is URegularLanguage -> URegularLanguage(automaton.intersection(other.automaton))
        else -> error("Regular language can be intersected only with regular language!")
    }

    override fun union(other: UFormalLanguage): UFormalLanguage = when {
        other.isEmpty || this.isSigmaStar-> this
        this.isEmpty || other.isSigmaStar -> other
        other is URegularLanguage -> URegularLanguage(automaton.union(other.automaton))
        else -> error("Regular language can be united only with regular language!")
    }

    override fun reverse(): UFormalLanguage = when {
        isEmpty || isSigmaStar -> this
        else -> {
            val result = automaton.clone()
            SpecialOperations.reverse(result)
            URegularLanguage(result)
        }
    }

    override fun repeat(n: Int) = when {
        n == 0 -> empty()
        n == 1 || isEmpty -> this
        else -> URegularLanguage(automaton.repeat(n, n))
    }

    override fun replace(what: UFormalLanguage, with: UFormalLanguage): UFormalLanguage {
        TODO("Not yet implemented")
    }

}