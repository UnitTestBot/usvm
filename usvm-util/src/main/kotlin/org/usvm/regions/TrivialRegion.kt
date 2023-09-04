package org.usvm.regions

class TrivialRegion : Region<TrivialRegion> {
    override val isEmpty = false
    override fun union(other: TrivialRegion): TrivialRegion = this

    override fun intersect(other: TrivialRegion): TrivialRegion = this
    override fun subtract(other: TrivialRegion): TrivialRegion =
        throw UnsupportedOperationException("TrivialRegion.subtract should not be called")

    override fun compare(other: TrivialRegion): Region.ComparisonResult = Region.ComparisonResult.INCLUDES
}