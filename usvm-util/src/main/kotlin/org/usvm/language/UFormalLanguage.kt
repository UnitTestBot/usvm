package org.usvm.language

/**
 * Represents set of strings constrained by various logical assumptions.
 */
interface UFormalLanguage {

    /**
     * Returns if this language contains no strings.
     */
    val isEmpty: Boolean

    /**
     * Returns if this language contains only empty string.
     */
    val isEmptyString: Boolean

    /**
     * Returns if this language contains any string.
     */
    val isSigmaStar: Boolean

    /**
     * Returns true if this language contains [string].
     */
    fun contains(string: String): Boolean

    /**
     * Enumerates all strings in this language of a given [length] contained.
     */
    fun getStrings(length: Int): Iterable<String>

    /**
     * Returns an intersection of this and [other] languages.
     */
    fun intersect(other: UFormalLanguage): UFormalLanguage

    /**
     * Returns a union of this and [other] languages.
     */
    fun union(other: UFormalLanguage): UFormalLanguage

    /**
     * Returns a complement of this language.
     */
    fun complement(): UFormalLanguage

    /**
     * Returns a new language containing all concatenations s1+s2, where s1 is from this language, s2 is from [other].
     */
    fun concat(other: UFormalLanguage): UFormalLanguage

    /**
     * Returns a new language representing set of reversed strings in this language.
     */
    fun reverse(): UFormalLanguage

    /**
     * Returns a new language representing set of strings in this language converted to upper case.
     */
    fun toUpperCase(): UFormalLanguage

    /**
     * Returns a new language representing set of strings in this language converted to lower case.
     */
    fun toLowerCase(): UFormalLanguage

    /**
     * Returns a new language representing set of strings, upper case version of which contained in this language.
     */
    fun deUpperCase(): UFormalLanguage

    /**
     * Returns a new language representing set of strings, lower case version of which contained in this language.
     */
    fun deLowerCase(): UFormalLanguage

    /**
     * Returns a new language representing self-concatenation of this language [n] times.
     */
    fun repeat(n: Int): UFormalLanguage


    /**
     * Returns a new language of strings that obtained from this by dropping [begin] first chars and [end] last chars.
     */
    fun trimCount(begin: Int, end: Int): UFormalLanguage

    /**
     * Returns a new language containing strings obtained by replacing strings from [what] language
     * with strings in [with] in strings from this language.
     */
    fun replace(what: UFormalLanguage, with: UFormalLanguage): UFormalLanguage
}

