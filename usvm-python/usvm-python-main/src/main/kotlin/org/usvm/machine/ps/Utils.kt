package org.usvm.machine.ps

import kotlin.random.Random

fun <T> weightedRandom(random: Random, items: List<T>, weighter: (T) -> Double): T {
    require(items.isNotEmpty())
    val prefixSum = items.map(weighter).runningFold(0.0) { acc, item -> acc + item }.drop(1)
    val sum = prefixSum.last()
    require(sum > 0)
    val borders = prefixSum.map { it / sum }
    val key = random.nextDouble()
    require(borders.last() > key)
    val idx = borders.indexOfFirst { it > key }
    return items[idx]
}
