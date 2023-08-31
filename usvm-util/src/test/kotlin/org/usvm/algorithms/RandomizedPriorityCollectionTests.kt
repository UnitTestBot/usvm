package org.usvm.algorithms

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class RandomizedPriorityCollectionTests {

    @Test
    fun distributionTest() {
        val hitsCount = 10000
        val valuesCount = 100

        val step = 1.0 / hitsCount

        var currentDouble = 0.0
        fun nextDouble(): Double {
            val res = currentDouble
            currentDouble += step
            return res
        }

        val pdf = RandomizedPriorityCollection<Int>(naturalOrder(), ::nextDouble)
        val priorities = HashMap<Int, Double>()
        var prioritySum = 0.0

        for (i in 1..valuesCount) {
            val value = pseudoRandom(i)
            val priority = i.toDouble()
            pdf.add(value, priority)
            priorities[value] = priority
            prioritySum += priority
        }

        val hits = HashMap<Int, Int>()

        for (i in 0 until hitsCount) {
            val peeked = pdf.peek()
            if (!hits.containsKey(peeked)) {
                hits[peeked] = 1
            } else {
                hits[peeked] = hits.getValue(peeked) + 1
            }
        }

        for ((k, v) in hits) {
            assertEquals(priorities.getValue(k) / prioritySum, v / hitsCount.toDouble(), 1e-3)
        }
    }

    @Test
    fun randomEqualsOneTest() {
        val pdf = RandomizedPriorityCollection<Int>(naturalOrder()) { 1.0 }

        for (i in 1..100) {
            val value = pseudoRandom(i)
            val priority = i.toDouble()
            pdf.add(value, priority)
            pdf.peek()
        }
    }
}
