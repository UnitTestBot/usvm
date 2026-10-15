package org.usvm.machine.expr

/** Linear UTF-16 patterns whose greedy repetitions never need backtracking. */
internal data class RegExpPattern(
    val parts: List<RegExpPart>,
    val anchoredStart: Boolean,
    val anchoredEnd: Boolean,
    val captureCount: Int,
) {
    val hasRepetition: Boolean get() = parts.atoms().any { it.repeated }
}

internal sealed interface RegExpPart {
    data class Atom(val ranges: List<IntRange>, val repeated: Boolean = false) : RegExpPart
    data class Group(val parts: List<RegExpPart>, val capture: Int?, val optional: Boolean) : RegExpPart
}

internal fun parseRegExpPattern(source: String): RegExpPattern? = RegExpPatternParser(source).parse()

private const val MAX_PATTERN_LENGTH = 256
private const val MAX_PATTERN_ATOMS = 64

private class RegExpPatternParser(private val source: String) {
    private var index = 0
    private var captureCount = 0

    fun parse(): RegExpPattern? {
        if (source.length > MAX_PATTERN_LENGTH) return null

        val anchoredStart = source.startsWith('^')
        if (anchoredStart) index++

        val parts = sequence() ?: return null
        val anchoredEnd = source.getOrNull(index) == '$'
        if (anchoredEnd) index++
        if (index != source.length) return null

        val atoms = parts.atoms()
        if (atoms.size > MAX_PATTERN_ATOMS) return null

        val needsBacktracking = atoms.zipWithNext().any { (previous, next) ->
            previous.repeated && previous.ranges.any { left -> next.ranges.any { right -> left.overlaps(right) } }
        }
        val hasUnanchoredRepetition = !anchoredStart && atoms.any { it.repeated }
        val unsupportedOptionalSuffix = !validOptionalSuffix(parts)
        if (needsBacktracking || hasUnanchoredRepetition || unsupportedOptionalSuffix) return null

        return RegExpPattern(parts, anchoredStart, anchoredEnd, captureCount)
    }

    private fun sequence(): List<RegExpPart>? {
        val parts = mutableListOf<RegExpPart>()
        while (index < source.length && source[index] != ')' && source[index] != '$') {
            val part = if (source[index] == '(') group() else atom()
            if (part == null) return null
            parts += part
        }
        return parts
    }

    private fun group(): RegExpPart.Group? {
        index++
        val capturing = !source.startsWith("?:", startIndex = index)
        val capture = if (capturing) captureCount++ else null
        if (!capturing) index += 2

        val parts = sequence() ?: return null
        if (source.getOrNull(index) != ')') return null
        index++

        val optional = source.getOrNull(index) == '?'
        if (optional) index++
        return RegExpPart.Group(parts, capture, optional)
    }

    private fun atom(): RegExpPart.Atom? {
        val ranges = if (source[index] == '[') characterClass() else character()
        if (ranges == null) return null

        val repeated = source.getOrNull(index) == '+'
        if (repeated) index++
        return RegExpPart.Atom(ranges, repeated)
    }

    private fun characterClass(): List<IntRange>? {
        index++
        if (source.getOrNull(index) == '^') return null

        val ranges = mutableListOf<IntRange>()
        while (index < source.length && source[index] != ']') {
            val first = character(inClass = true) ?: return null
            val hasRange = source.getOrNull(index) == '-' && source.getOrNull(index + 1) != ']'
            if (hasRange) {
                index++
                val last = character(inClass = true) ?: return null
                val start = first.singleOrNull() ?: return null
                val end = last.singleOrNull() ?: return null
                if (start.first != start.last || end.first != end.last || start.first > end.first) return null
                ranges += start.first..end.first
            } else {
                ranges += first
            }
        }
        if (source.getOrNull(index) != ']' || ranges.isEmpty()) return null
        index++
        return ranges
    }

    private fun character(inClass: Boolean = false): List<IntRange>? {
        val char = source.getOrNull(index) ?: return null
        index++
        if (char == '\\') {
            val escaped = source.getOrNull(index) ?: return null
            index++
            return when (escaped) {
                'd' -> listOf('0'.code..'9'.code)
                'w' -> listOf('0'.code..'9'.code, 'A'.code..'Z'.code, '_'.code..'_'.code, 'a'.code..'z'.code)
                'n' -> listOf('\n'.code..'\n'.code)
                'r' -> listOf('\r'.code..'\r'.code)
                't' -> listOf('\t'.code..'\t'.code)
                in "\\/.*+?()[]{}^$|-" -> listOf(escaped.code..escaped.code)
                else -> null
            }
        }
        if (!inClass && char in ".*+?()[]{}^$|") return null
        return listOf(char.code..char.code)
    }
}

private fun List<RegExpPart>.atoms(): List<RegExpPart.Atom> = flatMap { part ->
    when (part) {
        is RegExpPart.Atom -> listOf(part)
        is RegExpPart.Group -> part.parts.atoms()
    }
}

private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

/** An optional fixed suffix cannot force an earlier greedy run to backtrack. */
private fun validOptionalSuffix(parts: List<RegExpPart>, suffixAllowed: Boolean = true): Boolean =
    parts.withIndex().all { (index, part) ->
        when (part) {
            is RegExpPart.Atom -> true
            is RegExpPart.Group -> {
                val lastPart = suffixAllowed && index == parts.lastIndex
                if (part.optional) {
                    val atoms = part.parts.atoms()
                    lastPart && atoms.isNotEmpty() && atoms.none { it.repeated } &&
                        validOptionalSuffix(part.parts, suffixAllowed = false)
                } else {
                    validOptionalSuffix(part.parts, suffixAllowed = lastPart)
                }
            }
        }
    }
