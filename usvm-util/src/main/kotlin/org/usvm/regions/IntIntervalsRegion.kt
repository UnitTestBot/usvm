package org.usvm.regions

class IntIntervalsRegion private constructor(
    private val bounds: IntArray,
    val intMaxIncluded: Boolean,
) : Region<IntIntervalsRegion>, Iterable<Int> {
    override val isEmpty: Boolean
        get() = bounds.isEmpty() && !intMaxIncluded

    private inline fun combineWith(
        other: IntIntervalsRegion,
        combination: (Int, Int, Int, Int) -> Boolean,
        onIntMax: (Boolean, Boolean) -> Boolean,
        resultIsInteresting: Boolean = true,
    ): IntIntervalsRegion {
        var i1 = 0
        var i2 = 0
        var c1 = 0 // Counts the amount of segments of this, in which we are currently inside
        var c2 = 0 // Counts the amount of segments of other, in which we are currently inside

        var size = 0
        val capacity = if (resultIsInteresting) bounds.size + other.bounds.size else 0
        val result = IntArray(capacity)

        while (i1 < this.bounds.size || i2 < other.bounds.size) {
            val (wasC1, wasC2) = c1 to c2

            val x1 = if (i1 < this.bounds.size) this.bounds[i1] else null
            val x2 = if (i2 < other.bounds.size) other.bounds[i2] else null

            val cmp = if (x1 == null) 1 else if (x2 == null) -1 else x1.compareTo(x2)
            var x = 0
            if (cmp <= 0) {
                if (i1 % 2 == 0) c1++ else c1--
                i1++
                x = x1!!
            }
            if (cmp >= 0) {
                if (i2 % 2 == 0) c2++ else c2--
                i2++
                x = x2!!
            }

            val shouldRemain = combination(wasC1, wasC2, c1, c2)
            if (resultIsInteresting && shouldRemain) {
                result[size++] = x
            }
        }
        val bounds = result.copyOf(size)
        val intMaxIncluded = onIntMax(this.intMaxIncluded, other.intMaxIncluded)
        return IntIntervalsRegion(bounds, intMaxIncluded)
    }

    override fun subtract(other: IntIntervalsRegion) =
        combineWith(
            other,
            combination = { wasC1, wasC2, c1, c2 ->
                ((wasC1 == 0 || wasC2 > 0) && c1 > 0 && c2 == 0) || // like intersection, but inversed `c2` and `wasC2`
                    (wasC1 > 0 && wasC2 == 0 && (c1 == 0 || c2 > 0)) // like intersection, but inversed `c2` and `wasC2`
            },
            onIntMax = { b1, b2 -> b1 && !b2 }
        )

    override fun union(other: IntIntervalsRegion) =
        combineWith(
            other,
            combination = { wasC1, wasC2, c1, c2 ->
                (wasC1 == 0 && wasC2 == 0 && (c1 > 0 || c2 > 0)) || // beginning of some segment
                    (c1 == 0 && c2 == 0 && (wasC1 > 0 || wasC2 > 0)) // end of some segment
            },
            onIntMax = { b1, b2 -> b1 || b2 }
        )

    override fun intersect(other: IntIntervalsRegion) =
        combineWith(
            other,
            combination = { wasC1, wasC2, c1, c2 ->
                ((wasC1 == 0 || wasC2 == 0) && c1 > 0 && c2 > 0) || // we entered both for the first time
                    (wasC1 > 0 && wasC2 > 0 && (c1 == 0 || c2 == 0)) // we exit something for the first time
            },
            onIntMax = { b1, b2 -> b1 && b2 }
        )


    override fun compare(other: IntIntervalsRegion): Region.ComparisonResult {
        var includes = true
        var disjoint = true
        combineWith(
            other,
            combination = { _, _, c1, c2 ->
                if (c1 == 0 && c2 > 0) {
                    includes = false
                }
                if (c1 > 0 && c2 > 0) {
                    disjoint = false
                }
                false
            },
            onIntMax = { b1, b2 ->
                if (!b1 && b2) {
                    includes = false
                }
                if (b1 && b2) {
                    disjoint = false
                }
                false
            },
            resultIsInteresting = false
        )
        return when {
            includes -> Region.ComparisonResult.INCLUDES
            disjoint -> Region.ComparisonResult.DISJOINT
            else -> Region.ComparisonResult.INTERSECTS
        }
    }

    fun checkInvariants() {
        check(bounds.size % 2 == 0)
        for (i in 1..bounds.lastIndex) {
            check(bounds[i - 1] < bounds[i])
        }
    }

    override fun iterator(): Iterator<Int> = bounds.iterator()
    override fun toString(): String {
        return buildString {
            val repr = bounds.asSequence().chunked(2).joinToString(separator = " U ") { "[${it[0]}..${it[1]})" }
            append(repr)
            if (intMaxIncluded) {
                append("${" U ".takeIf { repr.isNotEmpty() } ?: ""}{${Int.MAX_VALUE}}")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntIntervalsRegion

        if (!bounds.contentEquals(other.bounds)) return false
        return intMaxIncluded == other.intMaxIncluded
    }

    override fun hashCode(): Int {
        var result = bounds.contentHashCode()
        result = 31 * result + intMaxIncluded.hashCode()
        return result
    }


    companion object {
        private val empty by lazy { IntIntervalsRegion(intArrayOf(), intMaxIncluded = false) }
        fun empty() = empty
        fun ofClosed(leftInclusive: Int, rightInclusive: Int): IntIntervalsRegion =
            when {
                (leftInclusive > rightInclusive) -> empty
                rightInclusive != Int.MAX_VALUE -> ofHalfOpen(leftInclusive, rightInclusive + 1)
                leftInclusive != Int.MAX_VALUE -> IntIntervalsRegion(
                    intArrayOf(leftInclusive, rightInclusive),
                    intMaxIncluded = true
                )

                else -> IntIntervalsRegion(intArrayOf(), intMaxIncluded = true)
            }
        fun ofHalfOpen(leftInclusive: Int, rightExclusive: Int): IntIntervalsRegion =
            if (leftInclusive >= rightExclusive) {
                empty
            } else {
                IntIntervalsRegion(intArrayOf(leftInclusive, rightExclusive), intMaxIncluded = false)
            }
        fun point(point: Int): IntIntervalsRegion = ofClosed(point, point)
        fun universe() = ofClosed(Int.MIN_VALUE, Int.MAX_VALUE)
    }
}
