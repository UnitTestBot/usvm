package org.usvm.regions

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
data class SetRegion<Point>(
    private val points: Set<Point>,
    private val thrown: Boolean
) : Region<SetRegion<Point>>, Iterable<Point> {
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

    override fun union(other: SetRegion<Point>): SetRegion<Point> =
        when {
            !this.thrown && !other.thrown -> SetRegion(this.points.union(other.points), thrown = false)
            this.thrown && !other.thrown -> SetRegion(this.points.minus(other.points), thrown = true)
            !this.thrown && other.thrown -> SetRegion(other.points.minus(this.points), thrown = true)
            this.thrown && other.thrown -> SetRegion(other.points.intersect(this.points), thrown = true)
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

    fun <OtherPoint> map(mapper: (Point) -> OtherPoint): SetRegion<OtherPoint> {
        val otherPoints = mutableSetOf<OtherPoint>()
        points.mapTo(otherPoints, mapper)
        return SetRegion(otherPoints, thrown)
    }

    override fun iterator(): Iterator<Point> {
        check(!thrown) { "Can't iterate over co-finite set" }
        return points.iterator()
    }

    override fun toString(): String =
        "${if (thrown) "Z \\ " else ""}{${points.joinToString(", ") }}"
}