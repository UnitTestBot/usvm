package org.usvm.regions


data class Endpoint<Elem : Comparable<Elem>>(val elem: Elem, val sort: Sort) : Comparable<Endpoint<Elem>> {
    enum class Sort {
        // Warning: do not re-order these values
        OPEN_RIGHT, CLOSED_LEFT, CLOSED_RIGHT, OPEN_LEFT
    }

    override fun compareTo(other: Endpoint<Elem>): Int {
        val elemComparison = this.elem.compareTo(other.elem)
        if (elemComparison == 0)
            return this.sort.compareTo(other.sort)
        return elemComparison
    }

    fun flip(): Endpoint<Elem> {
        val flippedSort =
            when (sort) {
                Sort.CLOSED_LEFT -> Sort.OPEN_RIGHT
                Sort.CLOSED_RIGHT -> Sort.OPEN_LEFT
                Sort.OPEN_LEFT -> Sort.CLOSED_RIGHT
                Sort.OPEN_RIGHT -> Sort.CLOSED_LEFT
            }
        return Endpoint(elem, flippedSort)
    }

    val isLeft
        get() =
            when (sort) {
                Sort.OPEN_LEFT, Sort.CLOSED_LEFT -> true
                else -> false
            }

    fun print(stringBuilder: StringBuilder, isLast: Boolean) {
        when (sort) {
            Sort.CLOSED_LEFT -> {
                assert(!isLast)
                stringBuilder.append('[')
                stringBuilder.append(elem)
                stringBuilder.append("..")
            }

            Sort.CLOSED_RIGHT -> {
                stringBuilder.append(elem)
                stringBuilder.append(']')
                if (!isLast) stringBuilder.append(" U ")
            }

            Sort.OPEN_LEFT -> {
                assert(!isLast)
                stringBuilder.append('(')
                stringBuilder.append(elem)
                stringBuilder.append("..")
            }

            Sort.OPEN_RIGHT -> {
                stringBuilder.append(elem)
                stringBuilder.append(')')
                if (!isLast) stringBuilder.append(" U ")
            }
        }
    }
}

/**
 * Region of intervals. Example: [0..2) U (3..4] U [6..10).
 */
data class IntervalsRegion<Point : Comparable<Point>>(
    private val points: List<Endpoint<Point>>
) : Region<IntervalsRegion<Point>>, Iterable<Endpoint<Point>> {
    /**
     * Implements sweeping line for two sets of segments.
     * That is traverses endpoints of this and [other] intervals in sorted order for *both* sets of endpoints.
     * If visited point is a left endpoint of this interval, calls [left1],
     * if it is a left endpoint of [other] interval, calls [left2],
     * if it's a left endpoint of both, calls [leftBoth]. Symmetrically for right endpoints.
     * Passes two boolean values B1 and B2 into every lambda.
     * B1 means that the sweeping line is currently inside some segment of this, B2 -- of [other].
     *
     * For example, for
     *      this = [1..3] U [4..5] and
     *      [other] = [2..5]
     * it will call
     * TODO: accomplish
     */
    private inline fun combineWith(
        other: IntervalsRegion<Point>,
        left1: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        right1: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        left2: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        right2: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        leftBoth: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        rightBoth: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
    ): IntervalsRegion<Point> {
        val i1 = this.points.listIterator()
        val i2 = other.points.listIterator()
        val result = mutableListOf<Endpoint<Point>>()
        var x1: Endpoint<Point>? = if (i1.hasNext()) i1.next() else null
        var x2: Endpoint<Point>? = if (i2.hasNext()) i2.next() else null
        var c1 = 0 // Counts the amount of segments of this, in which we are currently inside
        var c2 = 0 // Counts the amount of segments of other, in which we are currently inside
        while (x1 != null || x2 != null) {
            val cmp = if (x1 == null) 1 else if (x2 == null) -1 else x1.compareTo(x2)
            val x: Endpoint<Point> = if (cmp <= 0) x1!! else x2!!
            if (cmp <= 0) {
                x1 = if (i1.hasNext()) i1.next() else null
                if (x.isLeft) c1++ else c1--
            }
            if (cmp >= 0) {
                x2 = if (i2.hasNext()) i2.next() else null
                if (x.isLeft) c2++ else c2--
            }
            val res =
                if (cmp == 0) {
                    if (x.isLeft) leftBoth(x, c1 > 1, c2 > 1) else rightBoth(x, c1 > 0, c2 > 0)
                } else if (cmp < 0) {
                    if (x.isLeft) left1(x, c1 > 1, c2 > 0) else right1(x, c1 > 0, c2 > 0)
                } else {
                    if (x.isLeft) left2(x, c1 > 0, c2 > 1) else right2(x, c1 > 0, c2 > 0)
                }
            if (res != null)
                result.add(res)
        }
        return IntervalsRegion(result)
    }

    override val isEmpty: Boolean = points.isEmpty()

    override fun intersect(other: IntervalsRegion<Point>): IntervalsRegion<Point> {
        fun visit1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (!inside1 && inside2) x else null
        fun visit2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (!inside2 && inside1) x else null
        fun visitBoth(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (!inside1 && !inside2) x else null
        return combineWith(other, ::visit1, ::visit1, ::visit2, ::visit2, ::visitBoth, ::visitBoth)
    }

    @Suppress("UNUSED_PARAMETER")
    override fun subtract(other: IntervalsRegion<Point>): IntervalsRegion<Point> {
        fun visit1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (inside2) null else x
        fun visit2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (inside1) x.flip() else null
        return combineWith(other, ::visit1, ::visit1, ::visit2, ::visit2, ::visit2, ::visit2)
    }

    override fun union(other: IntervalsRegion<Point>): IntervalsRegion<Point> {
        fun visit(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (inside1 || inside2) null else x
        return combineWith(other, ::visit, ::visit, ::visit, ::visit, ::visit, ::visit)
    }

    @Suppress("UNUSED_PARAMETER")
    override fun compare(other: IntervalsRegion<Point>): RegionComparisonResult {
        var includes = true
        var disjoint = true
        fun visitLeft1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? {
            disjoint = disjoint && !inside2; return null
        }

        fun visitRight1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? {
            includes = includes && !inside2; disjoint = disjoint && !inside2; return null
        }

        fun visitLeft2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? {
            includes = includes && inside1; disjoint = disjoint && !inside1; return null
        }

        fun visitRight2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? {
            disjoint = disjoint && !inside1; return null
        }

        fun visitBoth(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? {
            disjoint = false; return null
        }
        combineWith(other, ::visitLeft1, ::visitRight1, ::visitLeft2, ::visitRight2, ::visitBoth, ::visitBoth)

        if (includes) return RegionComparisonResult.INCLUDES
        if (disjoint) return RegionComparisonResult.DISJOINT
        return RegionComparisonResult.INTERSECTS
    }

    override fun toString(): String {
        if (points.isEmpty())
            return "<empty>"
        val stringBuilder = StringBuilder()
        val e = points.listIterator()
        while (e.hasNext()) {
            val next = e.next()
            next.print(stringBuilder, !e.hasNext())
        }
        return stringBuilder.toString()
    }

    fun checkInvariants() {
        check(points.distinctBy { it.elem to it.isLeft }.size == points.size) { "Duplicate point: $points" }
        check(points.size % 2 == 0) { "Odd points list size: $points" }
        for (i in 1..points.lastIndex) {
            check(points[i - 1].compareTo(points[i]) < 0) { "Wrong order at positions (${i - 1}, $i): $points" }
        }
        for (i in 0..points.lastIndex) {
            val isLeft = i % 2 == 0
            check(points[i].isLeft == isLeft) { "Wrong sort at position $i: $points" }
        }
    }

    override fun iterator(): Iterator<Endpoint<Point>> = points.iterator()

    companion object {
        private val emptyRegion by lazy { IntervalsRegion<Nothing>(emptyList()) }

        fun <Point : Comparable<Point>> closed(from: Point, to: Point) =
            IntervalsRegion(
                listOf(
                    Endpoint(from, Endpoint.Sort.CLOSED_LEFT),
                    Endpoint(to, Endpoint.Sort.CLOSED_RIGHT)
                )
            )

        fun <Point : Comparable<Point>> singleton(x: Point) = closed(x, x)

        @Suppress("UNCHECKED_CAST")
        fun <Point : Comparable<Point>> empty(): IntervalsRegion<Point> = emptyRegion as IntervalsRegion<Point>
    }
}
