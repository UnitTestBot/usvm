package org.usvm.regions

interface Region<T> {
    val isEmpty: Boolean
    fun compare(other: T): ComparisonResult
    fun union(other: T): T
    fun subtract(other: T): T
    fun intersect(other: T): T

    enum class ComparisonResult {
        INCLUDES, INTERSECTS, DISJOINT
    }
}
