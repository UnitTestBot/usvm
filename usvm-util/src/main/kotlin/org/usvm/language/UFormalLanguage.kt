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
     * Returns if this language contains any string.
     */
    val isSigmaStar: Boolean

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
     * Returns a new language representing set of reversed strings in this language.
     */
    fun reverse(): UFormalLanguage

    /**
     * Returns a new language representing self-concatenation of this language [n] times.
     */
    fun repeat(n: Int): UFormalLanguage

    /**
     * Returns a new language containing strings obtained by replacing strings from [what] language
     * with strings in [with] in strings from this language.
     */
    fun replace(what: UFormalLanguage, with: UFormalLanguage): UFormalLanguage
}

