package org.usvm.language

import dk.brics.automaton.Automaton
import dk.brics.automaton.BasicAutomata
import dk.brics.automaton.RegExp
import dk.brics.automaton.SpecialOperations

class URegularLanguage(
    private val automaton: Automaton
): UFormalLanguage {
    companion object {
        init {
            Automaton.setAllowMutate(false)
        }
        fun empty(): UFormalLanguage = URegularLanguage(Automaton.makeEmpty())
        fun singleton(s: String): UFormalLanguage = URegularLanguage(Automaton.makeString(s))
        fun anyString(): UFormalLanguage = URegularLanguage(Automaton.makeAnyString())
        fun anyStringOfLength(length: Int): UFormalLanguage = URegularLanguage(BasicAutomata.makeAnyStringOfLength(length))
        fun fromRegex(pattern: String) = URegularLanguage(RegExp(pattern).toAutomaton())
    }

    override val isEmpty: Boolean =
        automaton.isEmpty
    override val isEmptyString: Boolean =
        automaton.isEmptyString
    override val isSigmaStar: Boolean =
        automaton.isTotal

    override fun contains(string: String) =
        automaton.run(string)

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

    override fun complement(): UFormalLanguage = when {
        isEmpty -> anyString()
        isSigmaStar -> empty()
        else -> {
            val result = automaton.clone()
            URegularLanguage(result.complement())
        }
    }

    override fun concat(other: UFormalLanguage): UFormalLanguage = when {
        this.isEmpty || other.isEmptyString -> this
        other.isEmpty || this.isEmptyString -> other
        this.isSigmaStar && other.isSigmaStar -> other
        other is URegularLanguage -> URegularLanguage(automaton.concatenate(other.automaton))
        else -> error("Regular language can be united only with regular language!")
    }

    override fun reverse(): URegularLanguage = when {
        isEmpty || isSigmaStar -> this
        else -> {
            val result = automaton.clone()
            SpecialOperations.reverse(result)
            URegularLanguage(result)
        }
    }

    override fun toUpperCase(): UFormalLanguage = when {
        isEmpty -> this
        else -> {
            val result = automaton.clone()
            SpecialOperations.toUpperCase(result)
            URegularLanguage(result)
        }
    }

    override fun toLowerCase(): UFormalLanguage = when {
        isEmpty -> this
        else -> {
            val result = automaton.clone()
            SpecialOperations.toLowerCase(result)
            URegularLanguage(result)
        }
    }


    override fun deUpperCase(): UFormalLanguage = when {
        isEmpty -> this
        else -> {
            val result = automaton.clone()
            SpecialOperations.deUpperCase(result)
            URegularLanguage(result)
        }
    }

    override fun deLowerCase(): UFormalLanguage = when {
        isEmpty -> this
        else -> {
            val result = automaton.clone()
            SpecialOperations.deLowerCase(result)
            URegularLanguage(result)
        }
    }
    override fun trimCount(begin: Int, end: Int): URegularLanguage = when {
        isEmpty || isSigmaStar -> this
        else -> URegularLanguage(SpecialOperations.trimCount(automaton, begin, end))
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