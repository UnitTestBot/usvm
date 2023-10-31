package org.usvm.algorithms

data class LcaResult<T, E>(
    val lca: T,
    val leftSuffix: List<E>,
    val rightSuffix: List<E>,
)

inline fun <T, E> findLcaLinear(
    u: T,
    v: T,
    parent: (T) -> T,
    depth: (T) -> Int,
    collect: (T) -> E,
    isSame: (T, T) -> Boolean = { left, right -> left == right },
): LcaResult<T, E> {
    var curLeft = u
    var curRight = v

    val leftSuffix = mutableListOf<E>()
    val rightSuffix = mutableListOf<E>()

    while (!isSame(curLeft, curRight)) {
        val leftDepth = depth(curLeft)
        val rightDepth = depth(curRight)
        if (leftDepth >= rightDepth) {
            leftSuffix += collect(curLeft)
            curLeft = parent(curLeft)
        }
        if (leftDepth < rightDepth) {
            rightSuffix += collect(curRight)
            curRight = parent(curRight)
        }
    }

    return LcaResult(curLeft, leftSuffix, rightSuffix)
}