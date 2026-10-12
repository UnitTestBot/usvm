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

import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jacodb.api.common.CommonMethod
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummarySubscriptionTest {
    @Test
    fun `completed owner stops delivery while history remains replayable`() {
        val method = mockk<CommonMethod>()
        val storage = SummaryStorageWithProducers<TestSummary>(isConcurrent = true)
        val owner = Job()
        val received = mutableListOf<Int>()
        storage.add(TestSummary(method, value = 1))
        storage.subscribe(method = method, scope = CoroutineScope(owner)) { received += it.value }

        owner.cancel()
        storage.add(TestSummary(method, value = 2))
        val replayed = mutableListOf<Int>()
        storage.subscribe(method = method) { replayed += it.value }

        assertEquals(listOf(1), received)
        assertEquals(listOf(1, 2), replayed)
    }

    @Test
    fun `cancellation clears the handler before owner children finish cleanup`() = runBlocking {
        val method = mockk<CommonMethod>()
        val storage = SummaryStorageWithProducers<TestSummary>(isConcurrent = true)
        val owner = Job()
        val releaseCleanup = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val child = CoroutineScope(coroutineContext + owner).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { releaseCleanup.await() }
            }
        }
        storage.subscribe(method = method, scope = CoroutineScope(owner)) { received += it.value }

        try {
            owner.cancel()
            storage.add(TestSummary(method, value = 1))

            assertTrue(owner.isCancelled)
            assertFalse(owner.isCompleted)
            assertTrue(received.isEmpty())
        } finally {
            releaseCleanup.complete(Unit)
            child.join()
        }
    }

    @Test
    fun `already cancelled owner receives neither replay nor new summaries`() {
        val method = mockk<CommonMethod>()
        val storage = SummaryStorageWithProducers<TestSummary>(isConcurrent = true)
        val owner = Job()
        val received = mutableListOf<Int>()
        storage.add(TestSummary(method, value = 1))
        owner.cancel()

        storage.subscribe(method = method, scope = CoroutineScope(owner)) { received += it.value }
        storage.add(TestSummary(method, value = 2))

        assertTrue(received.isEmpty())
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `cancellation clears a consumer selected by an earlier publication snapshot`() {
        val method = mockk<CommonMethod>()
        val storage = SummaryStorageWithProducers<TestSummary>(isConcurrent = true)
        val owner = Job()
        val received = mutableListOf<Int>()
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        storage.subscribe(method = method, scope = CoroutineScope(owner)) { received += it.value }
        // ConcurrentProducer delivers its newest consumer first: pause before visiting the scoped consumer.
        storage.subscribe(method = method) {
            callbackStarted.countDown()
            assertTrue(releaseCallback.await(3, TimeUnit.SECONDS))
        }

        try {
            val publication = executor.submit { storage.add(TestSummary(method, value = 1)) }
            assertTrue(callbackStarted.await(3, TimeUnit.SECONDS))

            owner.cancel()
            releaseCallback.countDown()
            publication.get(3, TimeUnit.SECONDS)

            assertTrue(received.isEmpty())
        } finally {
            releaseCallback.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancelled callback container no longer retains an active target`() {
        val received = mutableListOf<Int>()
        val consumer = CancellableConsumer(Consumer<Int> { received += it })

        consumer.cancel()
        consumer.consume(event = 1)

        assertFalse(consumer.isActive)
        assertTrue(received.isEmpty())
    }

    private data class TestSummary(
        override val method: CommonMethod,
        val value: Int,
    ) : Summary<CommonMethod>
}
