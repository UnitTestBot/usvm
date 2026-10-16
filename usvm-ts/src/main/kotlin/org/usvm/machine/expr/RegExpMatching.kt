package org.usvm.machine.expr

import io.ksmt.sort.KBv16Sort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.stringCharacters
import org.usvm.util.stringLength

internal data class RegExpCapture(
    val present: UBoolExpr,
    val start: UExpr<TsSizeSort>,
    val end: UExpr<TsSizeSort>,
)

internal data class RegExpMatch(
    val success: UBoolExpr,
    val start: UExpr<TsSizeSort>,
    val end: UExpr<TsSizeSort>,
    val captures: Map<Int, RegExpCapture>,
)

/** One bounded scan per greedy run; fixed unanchored patterns use a leftmost-match ITE chain. */
internal class RegExpMatcher(private val state: TsState, private val string: UHeapRef, private val bound: Int) {
    private val ctx = state.ctx
    private val length = state.stringLength(string)
    private val characters = state.stringCharacters(string)

    fun match(pattern: RegExpPattern): RegExpMatch = with(ctx) {
        if (pattern.anchoredStart) return matchAt(pattern, start = 0)

        check(!pattern.hasRepetition) { "Unanchored repetition must be rejected before matching" }
        var result = RegExpMatch(falseExpr, mkBv(0), mkBv(0), emptyMap())
        for (start in bound downTo 0) {
            val candidate = matchAt(pattern, start)
            val captures = candidate.captures.mapValues { (capture, value) ->
                val previous = result.captures[capture] ?: RegExpCapture(falseExpr, mkBv(0), mkBv(0))
                RegExpCapture(
                    present = mkIte(candidate.success, value.present, previous.present),
                    start = mkIte(candidate.success, value.start, previous.start),
                    end = mkIte(candidate.success, value.end, previous.end),
                )
            }
            result = RegExpMatch(
                success = mkOr(candidate.success, result.success),
                start = mkIte(candidate.success, candidate.start, result.start),
                end = mkIte(candidate.success, candidate.end, result.end),
                captures = captures,
            )
        }
        result
    }

    private fun matchAt(pattern: RegExpPattern, start: Int): RegExpMatch = with(ctx) {
        val result = sequence(pattern.parts, mkBv(start))
        // Without /m, ECMAScript $ requires the absolute end, including after a final newline.
        val endMatches = if (pattern.anchoredEnd) mkEq(result.end, length) else trueExpr
        val validStart = mkBvSignedLessOrEqualExpr(mkBv(start), length)
        result.copy(success = mkAnd(validStart, result.success, endMatches))
    }

    private fun sequence(parts: List<RegExpPart>, start: UExpr<TsSizeSort>): RegExpMatch = with(ctx) {
        var result = RegExpMatch(trueExpr, start, start, emptyMap())
        for (part in parts) {
            val next = when (part) {
                is RegExpPart.Atom -> atom(part, result.end)
                is RegExpPart.Group -> group(part, result.end)
            }
            result = result.copy(
                success = mkAnd(result.success, next.success),
                end = next.end,
                captures = result.captures + next.captures,
            )
        }
        result
    }

    private fun group(group: RegExpPart.Group, start: UExpr<TsSizeSort>): RegExpMatch = with(ctx) {
        var result = sequence(group.parts, start)
        val capture = group.capture
        if (capture != null) {
            result = result.copy(captures = result.captures + (capture to RegExpCapture(trueExpr, start, result.end)))
        }
        if (!group.optional) return result

        result.copy(
            success = trueExpr,
            end = mkIte(result.success, result.end, start),
            captures = result.captures.mapValues { (_, value) ->
                RegExpCapture(
                    present = mkAnd(result.success, value.present),
                    start = mkIte(result.success, value.start, start),
                    end = mkIte(result.success, value.end, start),
                )
            },
        )
    }

    private fun atom(atom: RegExpPart.Atom, start: UExpr<TsSizeSort>): RegExpMatch = with(ctx) {
        if (!atom.repeated) {
            val matches = matchesAt(atom, start)
            return RegExpMatch(matches, start, mkBvAddExpr(start, mkBv(1)), emptyMap())
        }

        var active: UBoolExpr = trueExpr
        var end = start
        for (offset in 0 until bound) {
            val index = mkBvAddExpr(start, mkBv(offset))
            active = mkAnd(active, matchesAt(atom, index))
            end = mkIte(active, mkBvAddExpr(index, mkBv(1)), end)
        }
        RegExpMatch(mkBvSignedGreaterExpr(end, start), start, end, emptyMap())
    }

    private fun matchesAt(atom: RegExpPart.Atom, index: UExpr<TsSizeSort>): UBoolExpr = with(ctx) {
        val unit = state.memory.read(mkStringBackingElementLValue(characters, index))
        val withinLength = mkBvSignedLessExpr(index, length)
        mkAnd(withinLength, characterMatches(atom, unit))
    }

    private fun characterMatches(atom: RegExpPart.Atom, value: UExpr<KBv16Sort>): UBoolExpr = with(ctx) {
        val alternatives = atom.ranges.map { range ->
            if (range.first == range.last) {
                mkEq(value, mkBv(range.first, bv16Sort))
            } else {
                val lowerBound = mkBv(range.first, bv16Sort)
                val upperBound = mkBv(range.last, bv16Sort)
                val aboveLowerBound = mkBvUnsignedGreaterOrEqualExpr(value, lowerBound)
                val belowUpperBound = mkBvUnsignedLessOrEqualExpr(value, upperBound)
                mkAnd(aboveLowerBound, belowUpperBound)
            }
        }
        mkOr(alternatives)
    }
}
