/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.ifds

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProducerTest {
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `recursive production delivers nested event before returning`() {
        val producer = ConcurrentProducer<Int>()
        val received = mutableListOf<Int>()
        producer.subscribe { event ->
            received += event
            if (event == 1) producer.produce(event = 2)
        }

        producer.produce(event = 1)

        assertEquals(listOf(1, 2), received)
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `subscription during a callback replays each event once`() {
        val producer = ConcurrentProducer<Int>()
        val received = mutableListOf<Int>()
        producer.subscribe { event ->
            if (event == 1) producer.subscribe { received += it }
        }

        producer.produce(event = 1)
        producer.produce(event = 2)

        assertEquals(listOf(1, 2), received)
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `concurrent subscription and production do not lose or duplicate events`() {
        val producer = ConcurrentProducer<Int>()
        val received = ConcurrentLinkedQueue<Int>()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val subscription = executor.submit {
                start.await()
                producer.subscribe { received += it }
            }
            val production = executor.submit {
                start.await()
                repeat(times = 1000) { producer.produce(event = it) }
            }
            start.countDown()
            subscription.get(3, TimeUnit.SECONDS)
            production.get(3, TimeUnit.SECONDS)

            assertEquals((0 until 1000).toList(), received.toList())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `cross producer callbacks do not invert publication locks`() {
        val first = ConcurrentProducer<Int>()
        val second = ConcurrentProducer<Int>()
        val entered = CountDownLatch(2)
        val received = ConcurrentLinkedQueue<Int>()
        first.subscribe { event ->
            received += event
            if (event == 1) {
                entered.countDown()
                entered.await()
                second.produce(event = 2)
            }
        }
        second.subscribe { event ->
            received += event
            if (event == 1) {
                entered.countDown()
                entered.await()
                first.produce(event = 2)
            }
        }
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstProduction = executor.submit { first.produce(event = 1) }
            val secondProduction = executor.submit { second.produce(event = 1) }
            firstProduction.get(3, TimeUnit.SECONDS)
            secondProduction.get(3, TimeUnit.SECONDS)

            assertEquals(listOf(1, 1, 2, 2), received.sorted())
        } finally {
            executor.shutdownNow()
        }
    }

}
