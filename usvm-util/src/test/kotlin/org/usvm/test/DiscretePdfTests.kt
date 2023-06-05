package org.usvm.test

import org.junit.jupiter.api.Test
import org.usvm.util.DiscretePdf
import kotlin.test.assertEquals

internal class DiscretePdfTests {

    @Test
    fun distributionTest() {
        val hitsCount = 10000
        val valuesCount = 100

        val step = 1f / hitsCount

        var currentFloat = 0f
        fun nextFloat(): Float {
            val res = currentFloat
            currentFloat += step
            return res
        }

        val pdf = DiscretePdf<Int>(naturalOrder(), ::nextFloat)
        val priorities = HashMap<Int, Float>()
        var prioritySum = 0f

        for (i in 1..valuesCount) {
            val value = hash(i)
            val priority = i.toFloat()
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
            assertEquals(priorities.getValue(k) / prioritySum, v / hitsCount.toFloat(), 1e-3f)
        }
    }

    @Test
    fun randomEqualsOneTest() {
        val pdf = DiscretePdf<Int>(naturalOrder()) { 1f }

        for (i in 1..100) {
            val value = hash(i)
            val priority = i.toFloat()
            pdf.add(value, priority)
            pdf.peek()
        }
    }
}