package org.usvm.util

import kotlin.text.StringBuilder

enum class RegionComparisonResult  {
    INCLUDES, INTERSECTS, DISJOINT
}

interface Region<T> {
    val isEmpty: Boolean
    fun compare(other: T): RegionComparisonResult
    fun subtract(other: T): T
    fun intersect(other: T): T
}

class TrivialRegion: Region<TrivialRegion> {
    override val isEmpty = false
    override fun intersect(other: TrivialRegion): TrivialRegion = this
    override fun subtract(other: TrivialRegion): TrivialRegion =
        throw UnsupportedOperationException("TrivialRegion.subtract should not be called")
    override fun compare(other: TrivialRegion): RegionComparisonResult = RegionComparisonResult.INCLUDES
}

/**
 * Region of intervals. Example: [0..2) U (3..4] U [6..10).
 */
data class Intervals<Point: Comparable<Point>>(private val points: List<Endpoint<Point>>) : Region<Intervals<Point>> {
    companion object {
        fun <Point: Comparable<Point>> closed(from: Point, to: Point) = Intervals(mutableListOf(Endpoint(from, EndpointSort.CLOSED_LEFT), Endpoint(to, EndpointSort.CLOSED_RIGHT)))
        fun <Point: Comparable<Point>> singleton(x: Point) = closed(x, x)
    }
    enum class EndpointSort {
        // Warning: do not re-order these values
        OPEN_RIGHT, CLOSED_LEFT, CLOSED_RIGHT, OPEN_LEFT
    }

    data class Endpoint<Elem: Comparable<Elem>>(val elem: Elem, val sort: EndpointSort): Comparable<Endpoint<Elem>> {
        override fun compareTo(other: Endpoint<Elem>): Int {
            val elemComparison = this.elem.compareTo(other.elem)
            if (elemComparison == 0)
                return this.sort.compareTo(other.sort)
            return elemComparison
        }

        fun flip(): Endpoint<Elem> {
            val flippedSort =
                when(sort) {
                    EndpointSort.CLOSED_LEFT -> EndpointSort.OPEN_RIGHT
                    EndpointSort.CLOSED_RIGHT -> EndpointSort.OPEN_LEFT
                    EndpointSort.OPEN_LEFT -> EndpointSort.CLOSED_RIGHT
                    EndpointSort.OPEN_RIGHT -> EndpointSort.CLOSED_LEFT
                }
            return Endpoint(elem, flippedSort)
        }

        val isLeft =
            when(sort) {
                EndpointSort.OPEN_LEFT, EndpointSort.CLOSED_LEFT -> true
                else -> false
            }

        fun print(stringBuilder: StringBuilder, isLast: Boolean) {
            when (sort) {
                EndpointSort.CLOSED_LEFT -> {
                    assert(!isLast)
                    stringBuilder.append('[')
                    stringBuilder.append(elem)
                    stringBuilder.append("..")
                }
                EndpointSort.CLOSED_RIGHT -> {
                    stringBuilder.append(elem)
                    stringBuilder.append(']')
                    if (!isLast) stringBuilder.append(" U ")
                }
                EndpointSort.OPEN_LEFT -> {
                    assert(!isLast)
                    stringBuilder.append('(')
                    stringBuilder.append(elem)
                    stringBuilder.append("..")
                }
                EndpointSort.OPEN_RIGHT -> {
                    stringBuilder.append(elem)
                    stringBuilder.append(')')
                    if (!isLast) stringBuilder.append(" U ")
                }
            }
        }
    }

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
        other: Intervals<Point>,
        left1: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        right1: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        left2: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        right2: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        leftBoth: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
        rightBoth: (Endpoint<Point>, Boolean, Boolean) -> Endpoint<Point>?,
    ): Intervals<Point> {
        val i1 = this.points.listIterator()
        val i2 = other.points.listIterator()
        val result = mutableListOf<Endpoint<Point>>()
        var x1: Endpoint<Point>? = if (i1.hasNext()) i1.next() else null
        var x2: Endpoint<Point>? = if (i2.hasNext()) i2.next() else null
        var c1 = 0 // Counts the amount of segments of this  we are currently inside
        var c2 = 0 // Counts the amount of segments of other we are currently inside
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
        return Intervals(result)
    }

    override val isEmpty: Boolean = points.isEmpty()

    override fun intersect(other: Intervals<Point>): Intervals<Point> {
        fun visit1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (!inside1 && inside2) x else null
        fun visit2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (!inside2 && inside1) x else null
        fun visitBoth(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (!inside1 && !inside2) x else null
        return combineWith(other, ::visit1, ::visit1, ::visit2, ::visit2, ::visitBoth, ::visitBoth)
    }

    @Suppress("UNUSED_PARAMETER")
    override fun subtract(other: Intervals<Point>): Intervals<Point> {
        fun visit1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (inside2) null else x
        fun visit2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (inside1) x.flip() else null
        return combineWith(other, ::visit1, ::visit1, ::visit2, ::visit2, ::visit2, ::visit2)
    }

    fun union(other: Intervals<Point>): Intervals<Point> {
        fun visit(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean) = if (inside1 || inside2) null else x
        return combineWith(other, ::visit, ::visit, ::visit, ::visit, ::visit, ::visit)
    }

    @Suppress("UNUSED_PARAMETER")
    override fun compare(other: Intervals<Point>): RegionComparisonResult {
        var includes = true
        var disjoint = true
        fun visitLeft1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? { disjoint = disjoint && !inside2; return null }
        fun visitRight1(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? { includes = includes && !inside2; disjoint = disjoint && !inside2; return null }
        fun visitLeft2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? { includes = includes && inside1; disjoint = disjoint && !inside1; return null }
        fun visitRight2(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? { disjoint = disjoint && !inside1; return null }
        fun visitBoth(x: Endpoint<Point>, inside1: Boolean, inside2: Boolean): Endpoint<Point>? { disjoint = false; return null }
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
}

private fun <Point> compareSets(s1: Set<Point>, s2: Set<Point>): Pair<Boolean, Boolean> {
    var includes = true
    var disjoint = true
    s2.forEach {x ->
        if (s1.contains(x))
            disjoint = false
        else
            includes = false
    }
    return Pair(includes, disjoint)
}
private fun flip(result: RegionComparisonResult) =
    when(result) {
        RegionComparisonResult.DISJOINT -> RegionComparisonResult.INCLUDES
        RegionComparisonResult.INCLUDES -> RegionComparisonResult.DISJOINT
        RegionComparisonResult.INTERSECTS -> RegionComparisonResult.INTERSECTS
    }

private fun setsComparisonToResult(pair: Pair<Boolean, Boolean>): RegionComparisonResult =
    when {
        pair.first -> RegionComparisonResult.INCLUDES
        pair.second -> RegionComparisonResult.DISJOINT
        else -> RegionComparisonResult.INTERSECTS
    }

/**
 * Region of finite and co-finite sets of points. Two examples: {1,2,3} and Z\{1,2,3}.
 * If [thrown] is true, set {1,2,3} passed to [points] corresponds to Z\{1,2,3}.
 */
data class SetRegion<Point>(private val points: Set<Point>, private val thrown: Boolean) : Region<SetRegion<Point>> {
    companion object {
        fun <Point> empty() = SetRegion<Point>(emptySet(), false)
        fun <Point> singleton(x: Point) = SetRegion(setOf(x), false)
        fun <Point> ofSet(vararg x: Point) = SetRegion(setOf(*x), false)
        fun <Point> ofSequence(seq: Sequence<Point>) = SetRegion(seq.toSet(), false)
        fun <Point> universe() = SetRegion<Point>(emptySet(), true)
    }

    override val isEmpty: Boolean = points.isEmpty() && !thrown

    override fun compare(other: SetRegion<Point>): RegionComparisonResult =
        when {
            !this.thrown && !other.thrown -> setsComparisonToResult(compareSets(this.points, other.points))
            this.thrown && !other.thrown -> flip(setsComparisonToResult(compareSets(this.points, other.points)))
            !this.thrown && other.thrown ->
                if (compareSets(other.points, this.points).first) RegionComparisonResult.DISJOINT
                else RegionComparisonResult.INTERSECTS
            this.thrown && other.thrown ->
                if (compareSets(other.points, this.points).first) RegionComparisonResult.INCLUDES
                else RegionComparisonResult.INTERSECTS
            else -> throw Exception("Unreachable")
        }

    override fun subtract(other: SetRegion<Point>): SetRegion<Point> =
        when {
            !this.thrown && !other.thrown -> SetRegion(this.points.minus(other.points), false)
            this.thrown && !other.thrown -> SetRegion(this.points.union(other.points), true)
            !this.thrown && other.thrown -> SetRegion(other.points.intersect(this.points), false)
            this.thrown && other.thrown -> SetRegion(other.points.minus(this.points), false)
            else -> throw Exception("Unreachable")
        }

    override fun intersect(other: SetRegion<Point>): SetRegion<Point> =
        when {
            !this.thrown && !other.thrown -> SetRegion(this.points.intersect(other.points), false)
            this.thrown && !other.thrown -> SetRegion(other.points.minus(this.points), false)
            !this.thrown && other.thrown -> SetRegion(this.points.minus(other.points), false)
            this.thrown && other.thrown -> SetRegion(other.points.union(this.points), true)
            else -> throw Exception("Unreachable")
        }

    override fun toString(): String =
        "${if (thrown) "Z \\ " else ""}{${points.joinToString(", ") }}"
}

private fun <X: Region<X>, Y: Region<Y>> subtractRect(rects: List<Pair<X, Y>>, rect2: Pair<X, Y>): List<Pair<X, Y>> {
    if (rect2.first.isEmpty || rect2.second.isEmpty)
        return rects
    val (c, d) = rect2
    var unchanged = true
    val result = mutableListOf<Pair<X, Y>>()
    for (rect1 in rects) {
        val (a, b) = rect1
        val ca = c.compare(a)
        val db = d.compare(b)
        if (ca == RegionComparisonResult.DISJOINT || db == RegionComparisonResult.DISJOINT) {
            result.add(rect1)
        } else {
            unchanged = false
            if (ca != RegionComparisonResult.INCLUDES) {
                val ac = a.subtract(c)
                if (!ac.isEmpty)
                    result.add(ac to b)
            }
            if (db != RegionComparisonResult.INCLUDES) {
                val bd = b.subtract(d)
                if (!bd.isEmpty)
                    result.add(a to bd)
            }
        }

    }
    // Warning: it is important to return reference equal value if the result is identical to input!
    if (unchanged)
        return rects
    return result
}

/**
 * A Cartesian product of two regions [X] and [Y].
 * In general case, represented by a set of rectangles with sides [X] and [Y].
 * Note that subtraction might split one rectangle into two:
 *      [1..3]x[1..3] \ [2..4]x[2..4] = [1..2)x[1..3] U [1..3]x[1..2)
 */
data class ProductRegion<X: Region<X>, Y: Region<Y>>(val products: List<Pair<X, Y>>) : Region<ProductRegion<X, Y>> {
    constructor(x: X, y: Y): this(mutableListOf(x to y))

    override val isEmpty: Boolean =
        products.all { it.first.isEmpty || it.second.isEmpty }

    override fun compare(other: ProductRegion<X, Y>): RegionComparisonResult {
        // TODO: comparison actually computes difference. Reuse it somehow (for example, return difference together with verdict).
        val diff = other.subtract(this)
        if (diff === other)
            return RegionComparisonResult.DISJOINT
        if (diff.isEmpty)
            return RegionComparisonResult.INCLUDES
        return RegionComparisonResult.INTERSECTS
    }

    override fun subtract(other: ProductRegion<X, Y>): ProductRegion<X, Y> {
        val newProducts = other.products.fold(this.products, ::subtractRect)
        if (newProducts === this.products)
            return this
        return ProductRegion(newProducts)
    }

    override fun intersect(other: ProductRegion<X, Y>): ProductRegion<X, Y> {
        val newProducts = mutableListOf<Pair<X, Y>>()
        for ((a, b) in this.products) {
            for ((c, d) in other.products) {
                val ac: X? =
                    when (a.compare(c)) {
                        RegionComparisonResult.DISJOINT -> null
                        RegionComparisonResult.INCLUDES -> c
                        RegionComparisonResult.INTERSECTS -> a.intersect(c)
                    }
                if (ac != null) {
                    when (a.compare(c)) {
                        RegionComparisonResult.DISJOINT -> {}
                        RegionComparisonResult.INCLUDES -> newProducts.add(ac to d)
                        RegionComparisonResult.INTERSECTS -> newProducts.add(ac to b.intersect(d))
                    }
                }
            }
        }
        return ProductRegion(newProducts)
    }
}
