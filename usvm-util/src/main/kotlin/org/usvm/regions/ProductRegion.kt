package org.usvm.regions

private fun <X : Region<X>, Y : Region<Y>> subtractRect(rects: List<Pair<X, Y>>, rect2: Pair<X, Y>): List<Pair<X, Y>> {
    if (rect2.first.isEmpty || rect2.second.isEmpty)
        return rects
    val (c, d) = rect2
    var unchanged = true
    val result = mutableListOf<Pair<X, Y>>()
    for (rect1 in rects) {
        val (a, b) = rect1
        val ca = c.compare(a)
        val db = d.compare(b)
        if (ca == Region.ComparisonResult.DISJOINT || db == Region.ComparisonResult.DISJOINT) {
            result.add(rect1)
        } else {
            unchanged = false
            if (ca != Region.ComparisonResult.INCLUDES) {
                val ac = a.subtract(c)
                if (!ac.isEmpty)
                    result.add(ac to b)
            }
            if (db != Region.ComparisonResult.INCLUDES) {
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
data class ProductRegion<X : Region<X>, Y : Region<Y>>(val products: List<Pair<X, Y>>) : Region<ProductRegion<X, Y>> {
    constructor(x: X, y: Y) : this(mutableListOf(x to y))

    override val isEmpty: Boolean =
        products.all { it.first.isEmpty || it.second.isEmpty }

    override fun union(other: ProductRegion<X, Y>): ProductRegion<X, Y> {
        class UnionState(products: List<Pair<X, Y>>) {
            val xToY = products.toMap(mutableMapOf())
            val yToX =
                products.associateByTo(mutableMapOf(), keySelector = { it.second }, valueTransform = { it.first })

            fun <X : Region<X>, Y : Region<Y>> uniteBy(
                direct: MutableMap<X, Y>,
                reverse: MutableMap<Y, X>,
                x: X,
                y: Y,
            ): Pair<X, Y> {
                val oldY = direct[x] ?: return x to y
                val unionY = oldY.union(y)
                if (unionY == oldY) {
                    return x to y
                }
                direct.remove(x)
                reverse.remove(oldY)
                return uniteBy(reverse, direct, unionY, x).run { second to first }
            }

            fun add(product: Pair<X, Y>) {
                val result =
                    uniteBy(xToY, yToX, product.first, product.second)
                        .run { uniteBy(yToX, xToY, second, first) }
                        .run { second to first }
                xToY[result.first] = result.second
                yToX[result.second] = result.first
            }
        }

        val state = UnionState(products)

        other.products.forEach(state::add)

        return ProductRegion(state.xToY.toList())
    }

    override fun compare(other: ProductRegion<X, Y>): Region.ComparisonResult {
        // TODO: comparison actually computes difference. Reuse it somehow (for example, return difference together with verdict).
        val diff = other.subtract(this)
        if (diff === other)
            return Region.ComparisonResult.DISJOINT
        if (diff.isEmpty)
            return Region.ComparisonResult.INCLUDES
        return Region.ComparisonResult.INTERSECTS
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
                        Region.ComparisonResult.DISJOINT -> null
                        Region.ComparisonResult.INCLUDES -> c
                        Region.ComparisonResult.INTERSECTS -> a.intersect(c)
                    }
                if (ac != null) {
                    when (a.compare(c)) {
                        Region.ComparisonResult.DISJOINT -> {}
                        Region.ComparisonResult.INCLUDES -> newProducts.add(ac to d)
                        Region.ComparisonResult.INTERSECTS -> newProducts.add(ac to b.intersect(d))
                    }
                }
            }
        }
        return ProductRegion(newProducts)
    }

    override fun toString(): String = products.joinToString(prefix = "{", separator = ", ", postfix = "}") { (a , b) ->
        ("$a X $b").prependIndent("\t")
    }
}
