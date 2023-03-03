package org.usvm.util

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
 * Region of intervals. Example: [0;2) U (3;4] U [6;10).
 */
class Intervals<Point: Comparable<Point>> : Region<Intervals<Point>> {
    private enum class EndpointSort {
        // Warning: do not re-order these values
        OPEN_RIGHT, CLOSED_LEFT, CLOSED_RIGHT, OPEN_LEFT
    }

    private data class Endpoint<Elem: Comparable<Elem>>(val elem: Elem, val sort: EndpointSort): Comparable<Endpoint<Elem>> {
        override fun compareTo(other: Endpoint<Elem>): Int {
            val elemComparison = this.compareTo(other)
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
    }

    override val isEmpty: Boolean =
        TODO("Not yet implemented")

    override fun intersect(other: Intervals<Point>): Intervals<Point> {
        TODO("Not yet implemented")
    }

    override fun subtract(other: Intervals<Point>): Intervals<Point> {
        TODO("Not yet implemented")
    }

    override fun compare(other: Intervals<Point>): RegionComparisonResult {
        TODO("Not yet implemented")
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

data class CartesianRegion<X: Region<X>, Y: Region<Y>>(val x : X, val y : Y) : Region<CartesianRegion<X, Y>> {
    override val isEmpty: Boolean =
        TODO("Not yet implemented")

    override fun compare(other: CartesianRegion<X, Y>): RegionComparisonResult {
        TODO("Not yet implemented")
    }

    override fun subtract(other: CartesianRegion<X, Y>): CartesianRegion<X, Y> {
        TODO("Not yet implemented")
    }

    override fun intersect(other: CartesianRegion<X, Y>): CartesianRegion<X, Y> {
        TODO("Not yet implemented")
    }
}
